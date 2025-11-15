package at.co.netconsulting.geotracker.composables

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.osmdroid.util.GeoPoint

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
                onNavigateToHeartRateDetail = { eventName, metrics ->
                    // Handle heart rate navigation - you might need to store this data
                    // and navigate to a heart rate detail route
                },
                onNavigateToWeatherDetail = { eventName, metrics ->
                    // Handle weather navigation - you might need to store this data
                    // and navigate to a weather detail route
                },
                onNavigateToBarometerDetail = { eventName, metrics ->
                    // Handle barometer navigation - you might need to store this data
                    // and navigate to a barometer detail route
                },
                onNavigateToAltitudeDetail = { eventName, metrics ->
                    // Handle altitude navigation - you might need to store this data
                    // and navigate to an altitude detail route
                },
                onNavigateToMapWithRoute = { locationPoints ->
                    // Handle map navigation - you might need to store this data
                    // and navigate to a map route
                },
                onNavigateToMapWithRouteRerun = { locationPoints ->
                    // Handle map navigation with route rerun - you might need to store this data
                    // and navigate to a map route
                },
                onNavigateToRouteComparison = { eventId, eventName ->
                    android.util.Log.d("AppNavigation", "Navigating to route comparison for eventId: $eventId")
                    try {
                        navController.navigate("routeComparison/$eventId")
                        android.util.Log.d("AppNavigation", "Navigation command sent successfully")
                    } catch (e: Exception) {
                        android.util.Log.e("AppNavigation", "Navigation failed", e)
                    }
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

        // Route comparison screen
        composable(
            route = "routeComparison/{eventId}",
            arguments = listOf(
                navArgument("eventId") {
                    type = NavType.IntType
                }
            )
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getInt("eventId") ?: -1
            RouteComparisonScreen(
                eventId = eventId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // If you need GPX import functionality, add it here
        // composable("importGpx") {
        //     GpxImportScreen(
        //         onNavigateBack = {
        //             navController.popBackStack()
        //         },
        //         onImportSuccess = { eventId ->
        //             navController.popBackStack()
        //             if (eventId > 0) {
        //                 navController.navigate("editEvent/$eventId")
        //             }
        //         }
        //     )
        // }
    }
}

/**
 * Extension function to add a new destination to the NavController
 */
fun NavHostController.navigateToImportGpx() {
    this.navigate("importGpx")
}