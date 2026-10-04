package pt.dabs.fueltracker.feature.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.interfaces.Detector
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import pt.dabs.fueltracker.R
import pt.dabs.fueltracker.core.scanning.OcrLine
import pt.dabs.fueltracker.core.scanning.OcrRows

/**
 * Lê o código QR do talão e, nas mesmas imagens, o texto do talão (litros e preço/litro).
 *
 * Depois de o QR ser lido continua a analisar durante um curto período e fica com a melhor
 * leitura de texto (a que tem mais carateres). Toda a análise corre no dispositivo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReceiptScreen(
    onResult: (qrRaw: String, ocrRows: List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionAsked by remember { mutableStateOf(false) }
    var qrFound by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        permissionAsked = true
    }
    LaunchedEffect(Unit) {
        if (!permissionGranted) launcher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                permissionGranted -> {
                    ReceiptCameraPreview(
                        onQrFound = { qrFound = true },
                        onResult = onResult,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 3.dp,
                    ) {
                        Text(
                            text = stringResource(if (qrFound) R.string.scan_reading_text else R.string.scan_hint),
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                permissionAsked -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.scan_permission_denied), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                            Text(stringResource(R.string.scan_permission_retry))
                        }
                    }
                }
            }
        }
    }
}

/** Quanto tempo, depois do QR, se continua a tentar melhorar a leitura do texto. */
private const val TEXT_GRACE_MILLIS = 1_500L
private const val TEXT_GRACE_FRAMES = 8

@Composable
private fun ReceiptCameraPreview(
    onQrFound: () -> Unit,
    onResult: (qrRaw: String, ocrRows: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnQrFound by rememberUpdatedState(onQrFound)

    val barcodeScanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            // O OCR precisa de mais resolução do que o predefinido (640x480) para ler um talão inteiro.
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                )
                .build()
        }
    }

    DisposableEffect(controller, lifecycleOwner) {
        val executor = ContextCompat.getMainExecutor(context)
        var qrRaw: String? = null
        var qrFoundAt = 0L
        var framesAfterQr = 0
        var bestRows: List<String> = emptyList()
        var bestChars = 0
        var delivered = false

        fun deliver() {
            val qr = qrRaw ?: return
            if (delivered) return
            delivered = true
            controller.clearImageAnalysisAnalyzer()
            currentOnResult(qr, bestRows)
        }

        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(listOf<Detector<*>>(barcodeScanner, textRecognizer), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, executor) { result ->
                if (delivered) return@MlKitAnalyzer

                result?.getValue(textRecognizer)?.let { text ->
                    val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                        line.boundingBox?.let { box -> OcrLine(line.text, box.top, box.bottom, box.left) }
                    }
                    val rows = OcrRows.merge(lines)
                    val chars = rows.sumOf { it.length }
                    if (chars > bestChars) {
                        bestChars = chars
                        bestRows = rows
                    }
                }

                if (qrRaw == null) {
                    val raw = result?.getValue(barcodeScanner)
                        ?.firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.isNotBlank() } }
                    if (raw != null) {
                        qrRaw = raw
                        qrFoundAt = SystemClock.elapsedRealtime()
                        currentOnQrFound()
                    }
                } else {
                    framesAfterQr++
                    val elapsed = SystemClock.elapsedRealtime() - qrFoundAt
                    if (elapsed >= TEXT_GRACE_MILLIS || framesAfterQr >= TEXT_GRACE_FRAMES) deliver()
                }
            },
        )
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            barcodeScanner.close()
            textRecognizer.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        },
    )
}
