package pt.dabs.fueltracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import pt.dabs.fueltracker.feature.fillup.FillUpScreen
import pt.dabs.fueltracker.feature.history.HistoryScreen
import pt.dabs.fueltracker.feature.home.HomeScreen
import pt.dabs.fueltracker.feature.nif.ShowNifScreen
import pt.dabs.fueltracker.feature.stats.StatsScreen
import pt.dabs.fueltracker.feature.scan.ScanOdometerScreen
import pt.dabs.fueltracker.feature.scan.ScanReceiptScreen
import pt.dabs.fueltracker.feature.settings.SettingsScreen

/**
 * @param newFillUpRequests contador de pedidos externos (widget) de novo abastecimento;
 * cada incremento abre o fluxo rápido uma vez.
 */
@Composable
fun FuelTrackerNavHost(newFillUpRequests: Int = 0) {
    val navController = rememberNavController()
    LaunchedEffect(newFillUpRequests) {
        if (newFillUpRequests > 0) {
            navController.navigate(FillUpRoute(startWithScan = true)) {
                popUpTo(HomeRoute)
                launchSingleTop = true
            }
        }
    }
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onAddFillUp = { navController.navigate(FillUpRoute(startWithScan = true)) },
                onOpenHistory = { navController.navigate(HistoryRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenStats = { navController.navigate(StatsRoute) },
                onShowNif = { navController.navigate(ShowNifRoute) },
            )
        }
        composable<StatsRoute> {
            StatsScreen(onBack = { navController.popBackStack() })
        }
        composable<ShowNifRoute> {
            ShowNifScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<HistoryRoute> {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(FillUpRoute(id)) },
                onAddFillUp = { navController.navigate(FillUpRoute(startWithScan = true)) },
            )
        }
        composable<FillUpRoute> { entry ->
            // Os ecrãs de leitura devolvem resultados pelo SavedStateHandle desta entrada.
            val handle = entry.savedStateHandle
            val scannedQr by handle.getStateFlow<String?>(SCANNED_QR_KEY, null).collectAsStateWithLifecycle()
            val scannedText by handle.getStateFlow<String?>(SCANNED_TEXT_KEY, null).collectAsStateWithLifecycle()
            val scannedOdometer by handle.getStateFlow<Int?>(SCANNED_ODOMETER_KEY, null).collectAsStateWithLifecycle()
            val odometerManual by handle.getStateFlow(ODOMETER_MANUAL_KEY, false).collectAsStateWithLifecycle()
            FillUpScreen(
                onDone = { navController.popBackStack() },
                onScanReceipt = { navController.navigate(ScanReceiptRoute) },
                onScanOdometer = { last, predicted ->
                    navController.navigate(ScanOdometerRoute(last ?: NO_VALUE, predicted ?: NO_VALUE))
                },
                scannedQr = scannedQr,
                scannedText = scannedText,
                onScannedQrConsumed = {
                    handle[SCANNED_QR_KEY] = null
                    handle[SCANNED_TEXT_KEY] = null
                },
                scannedOdometer = scannedOdometer,
                odometerManual = odometerManual,
                onScannedOdometerConsumed = {
                    handle[SCANNED_ODOMETER_KEY] = null
                    handle[ODOMETER_MANUAL_KEY] = false
                },
            )
        }
        composable<ScanReceiptRoute> {
            ScanReceiptScreen(
                onResult = { qrRaw, ocrRows ->
                    navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                        handle[SCANNED_TEXT_KEY] = ocrRows.joinToString("\n")
                        handle[SCANNED_QR_KEY] = qrRaw
                    }
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<ScanOdometerRoute> { entry ->
            val route = entry.toRoute<ScanOdometerRoute>()
            ScanOdometerScreen(
                lastOdometerKm = route.lastOdometerKm.takeIf { it != NO_VALUE },
                predictedKm = route.predictedKm.takeIf { it != NO_VALUE },
                onResult = { km ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(SCANNED_ODOMETER_KEY, km)
                    navController.popBackStack()
                },
                onManual = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(ODOMETER_MANUAL_KEY, true)
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
