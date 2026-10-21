package fr.dreau.trackpacer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.currentBackStackEntryAsState
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import fr.dreau.trackpacer.ui.screens.CueProfileScreen
import fr.dreau.trackpacer.ui.screens.HomeScreen
import fr.dreau.trackpacer.ui.screens.IntervalScreen
import fr.dreau.trackpacer.ui.screens.PaceScreen
import fr.dreau.trackpacer.ui.screens.RunScreen
import fr.dreau.trackpacer.ui.screens.SettingsScreen
import fr.dreau.trackpacer.ui.screens.SummaryScreen

object Routes {
    const val HOME = "home"
    const val PACE = "pace"
    const val INTERVAL = "interval"
    const val SETTINGS = "settings"
    const val CUES = "cues"
    const val RUN = "run"
    const val SUMMARY = "summary"
}

@Composable
fun PacerNavHost() {
    val navController = rememberSwipeDismissableNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val settingsViewModel: SettingsViewModel = viewModel()
    val runViewModel: RunViewModel = viewModel()

    AppScaffold {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = Routes.HOME,
            // A swipe during a rep must not throw the runner out of the chrono screen.
            userSwipeEnabled = currentRoute != Routes.RUN,
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    settingsViewModel = settingsViewModel,
                    runViewModel = runViewModel,
                    onEditPace = { navController.navigate(Routes.PACE) },
                    onEditInterval = { navController.navigate(Routes.INTERVAL) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onRun = { navController.navigate(Routes.RUN) },
                )
            }
            composable(Routes.PACE) {
                PaceScreen(viewModel = settingsViewModel, onDone = { navController.popBackStack() })
            }
            composable(Routes.INTERVAL) {
                IntervalScreen(viewModel = settingsViewModel, onDone = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onEditCues = { navController.navigate(Routes.CUES) },
                )
            }
            composable(Routes.CUES) {
                CueProfileScreen(viewModel = settingsViewModel, onDone = { navController.popBackStack() })
            }
            composable(Routes.RUN) {
                RunScreen(
                    viewModel = runViewModel,
                    onFinished = {
                        navController.navigate(Routes.SUMMARY) {
                            popUpTo(Routes.HOME) { inclusive = false }
                        }
                    },
                )
            }
            composable(Routes.SUMMARY) {
                SummaryScreen(
                    viewModel = runViewModel,
                    onClose = {
                        runViewModel.clearSession()
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    },
                )
            }
        }
    }
}
