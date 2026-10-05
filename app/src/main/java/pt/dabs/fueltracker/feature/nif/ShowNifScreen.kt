package pt.dabs.fueltracker.feature.nif

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.dabs.fueltracker.R

/**
 * NIF em alto contraste, letras grandes e brilho máximo, para mostrar ao funcionário do posto.
 * Toque em qualquer ponto para fechar.
 */
@Composable
fun ShowNifScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ShowNifViewModel = hiltViewModel(),
) {
    val nif by viewModel.nif.collectAsStateWithLifecycle()
    KeepScreenBright()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onBack)
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val value = nif
        if (value == null) {
            Text(stringResource(R.string.nif_missing), color = Color.White, textAlign = TextAlign.Center, fontSize = 20.sp)
            Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.settings_title))
            }
        } else {
            Text(stringResource(R.string.nif_label), color = Color(0xFFBBBBBB), fontSize = 22.sp)
            Text(
                text = value.chunked(3).joinToString(" "),
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                lineHeight = 72.sp,
            )
            Text(stringResource(R.string.nif_tap_to_close), color = Color(0xFF888888), fontSize = 14.sp, modifier = Modifier.padding(top = 32.dp))
        }
    }
}

@Composable
private fun KeepScreenBright() {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window) {
        val previousBrightness = window.attributes.screenBrightness
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply { screenBrightness = previousBrightness }
        }
    }
}
