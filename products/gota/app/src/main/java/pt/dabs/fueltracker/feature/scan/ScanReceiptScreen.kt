package pt.dabs.fueltracker.feature.scan

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import pt.dabs.fueltracker.core.scanning.OcrLine
import pt.dabs.fueltracker.core.scanning.OcrRows
import pt.dabs.fueltracker.core.scanning.StillImageScanner
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Lê o código QR do talão e o texto do talão (litros, preço/litro, desconto, total).
 *
 * Os frames de análise (1920×1440) chegam para o QR mas são curtos para o texto miúdo de um
 * talão inteiro: assim que o QR é lido tira-se uma fotografia em resolução máxima e o OCR corre
 * sobre ela, como numa foto da galeria. A melhor leitura dos frames fica como recurso.
 * Toda a análise corre no dispositivo; a fotografia vai para a cache e é apagada logo a seguir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReceiptScreen(
    onResult: (qrRaw: String?, ocrRows: List<String>) -> Unit,
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

    // Fotografia já tirada: o seletor de fotos do sistema não precisa de permissões.
    val scope = rememberCoroutineScope()
    val stillScanner = remember { StillImageScanner(context.applicationContext) }
    var analyzingPhoto by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<Int?>(null) }
    val currentOnResult by rememberUpdatedState(onResult)
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            analyzingPhoto = true
            photoError = null
            try {
                val scan = stillScanner.scanReceipt(uri)
                // Sem QR legível aceita-se o talão se o texto tiver os litros ou o preço.
                if (scan.isUsable) currentOnResult(scan.qrRaw, scan.ocrRows) else photoError = R.string.scan_photo_no_qr
            } catch (e: Exception) {
                photoError = R.string.scan_photo_failed
            } finally {
                analyzingPhoto = false
            }
        }
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

            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 3.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(
                            when {
                                analyzingPhoto -> R.string.scan_photo_analyzing
                                photoError != null -> photoError!!
                                qrFound -> R.string.scan_reading_text
                                permissionGranted -> R.string.scan_hint
                                else -> R.string.scan_photo_only_hint
                            }
                        ),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (photoError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !analyzingPhoto,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.scan_pick_photo))
                    }
                }
            }
        }
    }
}

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
    val scope = rememberCoroutineScope()
    val stillScanner = remember { StillImageScanner(context.applicationContext) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            // A análise lê o QR; depois tira-se uma fotografia em resolução máxima para o texto.
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS or CameraController.IMAGE_CAPTURE)
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
        var bestRows: List<String> = emptyList()
        var bestChars = 0
        var delivered = false
        var capturing = false

        fun deliver(stillRows: List<String> = emptyList()) {
            val qr = qrRaw ?: return
            if (delivered) return
            delivered = true
            controller.clearImageAnalysisAnalyzer()
            // A fotografia tem muito mais resolução do que os frames de análise: preferi-la quando
            // tem os valores, ou quando os frames também não os têm.
            val rows = when {
                StillImageScanner.hasFuelAmounts(stillRows) -> stillRows
                StillImageScanner.hasFuelAmounts(bestRows) -> bestRows
                stillRows.isNotEmpty() -> stillRows
                else -> bestRows
            }
            currentOnResult(qr, rows)
        }

        /** Fotografia em resolução máxima para o OCR, guardada só em cache e apagada a seguir. */
        fun captureStill() {
            if (capturing) return
            capturing = true
            val file = File(context.cacheDir, "receipt-scan.jpg")
            controller.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        scope.launch {
                            val rows = try {
                                stillScanner.recognizeRows(Uri.fromFile(file))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                emptyList()
                            } finally {
                                file.delete()
                            }
                            deliver(rows)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        deliver()
                    }
                },
            )
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
                        currentOnQrFound()
                        captureStill()
                    }
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
