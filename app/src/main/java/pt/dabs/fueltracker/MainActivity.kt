package pt.dabs.fueltracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import pt.dabs.fueltracker.core.ui.theme.FuelTrackerTheme
import pt.dabs.fueltracker.navigation.FuelTrackerNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FuelTrackerTheme {
                FuelTrackerNavHost()
            }
        }
    }
}
