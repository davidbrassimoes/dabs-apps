package pt.dabs.fueltracker.feature.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.interfaces.Detector
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import pt.dabs.fueltracker.R
import pt.dabs.fueltracker.core.scanning.OcrLine
import pt.dabs.fueltracker.core.scanning.OcrRows
import pt.dabs.fueltracker.core.scanning.OdometerReader
import pt.dabs.fueltracker.core.scanning.OdometerStabilizer
import pt.dabs.fueltracker.core.ui.format.PtFormat

/** Fração do pré-visualizador ocupada pela moldura onde se enquadram os dígitos. */
private const val FRAME_WIDTH_FRACTION = 0.8f
private const val FRAME_HEIGHT_FRACTION = 0.18f
private const val REQUIRED_STABLE_FRAMES = 3

/**
 * Lê o conta-km total em tempo real: só o texto dentro da moldura conta, só dígitos plausíveis
 * face ao último odómetro são aceites, e o valor fixa quando se repete em frames consecutivos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanOdometerScreen(
    lastOdometerKm: Int?,
    predictedKm: Int?,
    onResult: (Int) -> Unit,
    onManual: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionAsked by remember { mutableStateOf(false) }
    var candidate by remember { mutableStateOf<Int?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
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
                title = { Text(stringResource(R.string.scan_odometer_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (permissionGranted) {
                OdometerCameraPreview(
                    lastOdometerKm = lastOdometerKm,
                    onProgress = { value, fraction -> candidate = value; progress = fraction },
                    onResult = onResult,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(FRAME_WIDTH_FRACTION)
                        .fillMaxHeight(FRAME_HEIGHT_FRACTION)
                        .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                )
            } else if (permissionAsked) {
                Column(modifier = Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.scan_permission_denied), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.scan_permission_retry))
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
                        text = candidate?.let { stringResource(R.string.scan_odometer_reading, PtFormat.km(it)) }
                            ?: stringResource(R.string.scan_odometer_hint),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (candidate != null) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                    if (lastOdometerKm != null || predictedKm != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = listOfNotNull(
                                lastOdometerKm?.let { stringResource(R.string.scan_odometer_last, PtFormat.km(it)) },
                                predictedKm?.let { stringResource(R.string.scan_odometer_predicted, PtFormat.km(it)) },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.scan_odometer_manual))
                    }
                }
            }
        }
    }
}

@Composable
private fun OdometerCameraPreview(
    lastOdometerKm: Int?,
    onProgress: (candidate: Int?, progress: Float) -> Unit,
    onResult: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnProgress by rememberUpdatedState(onProgress)
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    val currentPreviewSize by rememberUpdatedState(previewSize)

    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                .build()
        }
    }

    DisposableEffect(controller, lifecycleOwner) {
        val executor = ContextCompat.getMainExecutor(context)
        val stabilizer = OdometerStabilizer(requiredFrames = REQUIRED_STABLE_FRAMES)
        var delivered = false

        controller.setImageAnalysisAnalyzer(
            executor,
            // Coordenadas referidas ao PreviewView, para comparar com a moldura desenhada por cima.
            MlKitAnalyzer(listOf<Detector<*>>(textRecognizer), CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { result ->
                if (delivered) return@MlKitAnalyzer
                val frame = frameRect(currentPreviewSize)
                val lines = result?.getValue(textRecognizer)?.textBlocks
                    ?.flatMap { it.lines }
                    ?.mapNotNull { line ->
                        line.boundingBox
                            ?.takeIf { box -> frame == null || frame.contains(box.centerX(), box.centerY()) }
                            ?.let { box -> OcrLine(line.text, box.top, box.bottom, box.left) }
                    }
                    .orEmpty()
                val reading = OdometerReader.read(OcrRows.merge(lines), lastOdometerKm)
                val locked = stabilizer.offer(reading)
                currentOnProgress(stabilizer.candidate, stabilizer.progress)
                if (locked != null) {
                    delivered = true
                    controller.clearImageAnalysisAnalyzer()
                    currentOnResult(locked)
                }
            },
        )
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            textRecognizer.close()
        }
    }

    AndroidView(
        modifier = modifier.onSizeChanged { previewSize = it },
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        },
    )
}

/** Retângulo da moldura em píxeis do PreviewView; null enquanto o tamanho não é conhecido. */
private fun frameRect(size: IntSize): Rect? {
    if (size.width == 0 || size.height == 0) return null
    val frameWidth = size.width * FRAME_WIDTH_FRACTION
    val frameHeight = size.height * FRAME_HEIGHT_FRACTION
    val left = (size.width - frameWidth) / 2
    val top = (size.height - frameHeight) / 2
    return Rect(left.toInt(), top.toInt(), (left + frameWidth).toInt(), (top + frameHeight).toInt())
}
