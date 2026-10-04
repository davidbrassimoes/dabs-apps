package pt.dabs.fueltracker.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pt.dabs.fueltracker.feature.fillup.FillUpScreen
import pt.dabs.fueltracker.feature.history.HistoryScreen
import pt.dabs.fueltracker.feature.home.HomeScreen
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
        composable<FillUpRoute> {
            FillUpScreen(onDone = { navController.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
