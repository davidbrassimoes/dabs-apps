package pt.dabs.fueltracker.feature.scan

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import pt.dabs.fueltracker.R
import kotlinx.coroutines.launch
import pt.dabs.fueltracker.core.scanning.LumaImage
import pt.dabs.fueltracker.core.scanning.OdometerStabilizer
import pt.dabs.fueltracker.core.scanning.SevenSegmentReader
import pt.dabs.fueltracker.core.scanning.StillImageScanner
import pt.dabs.fueltracker.core.domain.NumberInput
import pt.dabs.fueltracker.core.ui.format.PtFormat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Fração do pré-visualizador ocupada pela moldura onde se enquadra o visor (parcial e total). */
private const val FRAME_WIDTH_FRACTION = 0.8f
private const val FRAME_HEIGHT_FRACTION = 0.26f
private const val REQUIRED_STABLE_FRAMES = 3

/**
 * Lê o conta-km total em tempo real com o leitor de sete segmentos (o ML Kit não lê o visor de
 * blocos do carro): só a zona da moldura conta, só leituras plausíveis face ao último odómetro
 * são aceites, e o valor fixa quando se repete em frames consecutivos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanOdometerScreen(
    lastOdometerKm: Int?,
    predictedKm: Int?,
    onResult: (km: Int, tripTenthsKm: Int?) -> Unit,
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
    var panel by remember { mutableStateOf<SevenSegmentReader.PanelReading?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        permissionAsked = true
    }
    LaunchedEffect(Unit) {
        if (!permissionGranted) launcher.launch(Manifest.permission.CAMERA)
    }

    // Fotografia do painel já tirada: leitor de sete segmentos na imagem inteira (OCR como recurso).
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
                val reading = stillScanner.readPanel(uri, lastOdometerKm)
                val total = reading.totalKm
                if (total != null) currentOnResult(total, reading.tripTenthsKm) else photoError = R.string.scan_odometer_photo_not_found
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
                    onProgress = { value, fraction, reading -> candidate = value; progress = fraction; if (reading?.tripTenthsKm != null) panel = reading },
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
                        text = when {
                            analyzingPhoto -> stringResource(R.string.scan_photo_analyzing)
                            photoError != null -> stringResource(photoError!!)
                            candidate != null -> stringResource(R.string.scan_odometer_reading, PtFormat.km(candidate!!))
                            else -> stringResource(R.string.scan_odometer_hint)
                        },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (photoError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    if (candidate != null) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                    panel?.tripTenthsKm?.let { tenths ->
                        // O parcial é posto a zeros a cada abastecimento: deve ser a distância desde o anterior.
                        val trip = NumberInput.formatScaled(tenths.toLong(), 1) + " km"
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = when (panel?.consistentWithTrip) {
                                true -> stringResource(R.string.scan_odometer_trip_ok, trip)
                                false -> stringResource(R.string.scan_odometer_trip_mismatch, trip)
                                null -> stringResource(R.string.scan_odometer_trip, trip)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (panel?.consistentWithTrip == false) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
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
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = !analyzingPhoto,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.scan_pick_photo))
                        }
                        OutlinedButton(onClick = onManual, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.scan_odometer_manual))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OdometerCameraPreview(
    lastOdometerKm: Int?,
    onProgress: (candidate: Int?, progress: Float, panel: SevenSegmentReader.PanelReading?) -> Unit,
    onResult: (km: Int, tripTenthsKm: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnProgress by rememberUpdatedState(onProgress)

    // A leitura de cada frame (~10-40 ms) corre fora da thread principal.
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                .build()
        }
    }

    DisposableEffect(controller, lifecycleOwner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val stabilizer = OdometerStabilizer(requiredFrames = REQUIRED_STABLE_FRAMES)
        val delivered = AtomicBoolean(false)
        // Parciais lidos e quantas vezes: entrega-se o mais repetido, não o do último frame.
        val tripVotes = HashMap<Int, Int>()

        controller.setImageAnalysisAnalyzer(analysisExecutor) { proxy ->
            val reading = proxy.use { frame ->
                if (delivered.get()) return@use null
                // O cropRect é a parte da imagem visível no PreviewView (viewport do controlador),
                // por isso a moldura, centrada, corresponde às mesmas frações da imagem rodada.
                // Com folga: o leitor encontra os dígitos sozinho dentro da zona.
                val region = frame.toUprightLuma()
                    .centerRegion(FRAME_WIDTH_FRACTION * 1.1f, FRAME_HEIGHT_FRACTION * 1.5f)
                SevenSegmentReader.readPanel(region, lastOdometerKm)
            }
            mainExecutor.execute {
                if (delivered.get()) return@execute
                reading?.tripTenthsKm?.let { tripVotes.merge(it, 1, Int::plus) }
                val locked = stabilizer.offer(reading?.totalKm)
                currentOnProgress(stabilizer.candidate, stabilizer.progress, reading)
                if (locked != null && delivered.compareAndSet(false, true)) {
                    controller.clearImageAnalysisAnalyzer()
                    currentOnResult(locked, tripVotes.maxByOrNull { it.value }?.key)
                }
            }
        }
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            delivered.set(true)
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
        }
    }
    DisposableEffect(analysisExecutor) {
        onDispose { analysisExecutor.shutdown() }
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

/** Plano Y (luminância) da parte visível do frame, já rodado para a orientação do ecrã. */
private fun ImageProxy.toUprightLuma(): LumaImage {
    val plane = planes[0]
    val buffer = plane.buffer.apply { rewind() }
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val crop = cropRect
    return LumaImage.fromYPlane(bytes, plane.rowStride, plane.pixelStride, crop.left, crop.top, crop.right, crop.bottom)
        .rotate(imageInfo.rotationDegrees)
}
