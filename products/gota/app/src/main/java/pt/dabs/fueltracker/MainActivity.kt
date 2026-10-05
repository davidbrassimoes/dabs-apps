package pt.dabs.fueltracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import pt.dabs.fueltracker.core.ui.theme.FuelTrackerTheme
import pt.dabs.fueltracker.navigation.FuelTrackerNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Incrementa a cada pedido de novo abastecimento vindo de fora (widget), para o NavHost reagir. */
    private var newFillUpRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            FuelTrackerTheme {
                FuelTrackerNavHost(newFillUpRequests = newFillUpRequests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_NEW_FILL_UP) newFillUpRequests++
    }

    companion object {
        const val ACTION_NEW_FILL_UP = "pt.dabs.fueltracker.action.NEW_FILL_UP"
    }
}
