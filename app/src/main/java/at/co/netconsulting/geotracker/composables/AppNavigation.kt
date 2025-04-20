package at.co.netconsulting.geotracker.composables

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

/**
 * Main navigation component for the app
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "events"
    ) {
        // Events list screen
        composable("events") {
            EventsScreen(
                onEditEvent = { eventId ->
                    navController.navigate("editEvent/$eventId")
                },
                onNavigateToImportGpx = {
                    navController.navigate("importGpx")
                }
            )
        }

        // Edit event screen
        composable(
            route = "editEvent/{eventId}",
            arguments = listOf(
                navArgument("eventId") {
                    type = NavType.IntType
                }
            )
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getInt("eventId") ?: -1
            EditEventScreen(
                eventId = eventId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // GPX import screen
        composable("importGpx") {
            GpxImportScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onImportSuccess = { eventId ->
                    // Navigate back to events screen after successful import
                    navController.popBackStack()

                    // Optionally, navigate to edit the newly imported event
                    if (eventId > 0) {
                        navController.navigate("editEvent/$eventId")
                    }
                }
            )
        }
    }
}

/**
 * Extension function to add a new destination to the NavController
 */
fun NavHostController.navigateToImportGpx() {
    this.navigate("importGpx")
}