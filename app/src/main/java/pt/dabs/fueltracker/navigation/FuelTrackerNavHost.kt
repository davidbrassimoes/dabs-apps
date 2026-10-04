package pt.dabs.fueltracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pt.dabs.fueltracker.feature.fillup.FillUpScreen
import pt.dabs.fueltracker.feature.history.HistoryScreen
import pt.dabs.fueltracker.feature.home.HomeScreen
import pt.dabs.fueltracker.feature.scan.ScanReceiptScreen
import pt.dabs.fueltracker.feature.settings.SettingsScreen

@Composable
fun FuelTrackerNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onAddFillUp = { navController.navigate(FillUpRoute()) },
                onOpenHistory = { navController.navigate(HistoryRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<HistoryRoute> {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(FillUpRoute(id)) },
                onAddFillUp = { navController.navigate(FillUpRoute()) },
            )
        }
        composable<FillUpRoute> { entry ->
            // O ecrã de leitura devolve o texto do QR pelo SavedStateHandle desta entrada.
            val scannedQr by entry.savedStateHandle.getStateFlow<String?>(SCANNED_QR_KEY, null).collectAsStateWithLifecycle()
            FillUpScreen(
                onDone = { navController.popBackStack() },
                onScanReceipt = { navController.navigate(ScanReceiptRoute) },
                scannedQr = scannedQr,
                onScannedQrConsumed = { entry.savedStateHandle[SCANNED_QR_KEY] = null },
            )
        }
        composable<ScanReceiptRoute> {
            ScanReceiptScreen(
                onResult = { raw ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(SCANNED_QR_KEY, raw)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
