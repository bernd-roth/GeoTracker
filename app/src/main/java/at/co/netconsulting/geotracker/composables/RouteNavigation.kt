package at.co.netconsulting.geotracker.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

// Routes for the app
object Routes {
    const val EVENTS = "events"
    const val EDIT_EVENT = "edit_event/{eventId}"

    // Create actual navigation path with parameters
    fun editEvent(eventId: Int) = "edit_event/$eventId"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.EVENTS
    ) {
        // Events list screen
        composable(Routes.EVENTS) {
            EventsScreen(
                onEditEvent = { eventId ->
                    navController.navigate(Routes.editEvent(eventId))
                }
            )
        }

        // Edit event screen
        composable(
            route = Routes.EDIT_EVENT,
            arguments = listOf(
                navArgument("eventId") {
                    type = NavType.IntType
                }
            )
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getInt("eventId") ?: 0

            EditEventScreen(
                eventId = eventId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

// Alternatively, for simpler integration without full navigation component:

@Composable
fun SimpleAppScreens() {
    // State to track current screen
    val appState = remember { AppState() }

    when (appState.currentScreen) {
        Screen.EVENTS -> {
            EventsScreen(
                onEditEvent = { eventId ->
                    appState.currentEventId = eventId
                    appState.currentScreen = Screen.EDIT_EVENT
                }
            )
        }
        Screen.EDIT_EVENT -> {
            EditEventScreen(
                eventId = appState.currentEventId,
                onNavigateBack = {
                    appState.currentScreen = Screen.EVENTS
                }
            )
        }
    }
}

// Simple state management without Navigation component
class AppState {
    var currentScreen = Screen.EVENTS
    var currentEventId: Int = 0
}

enum class Screen {
    EVENTS,
    EDIT_EVENT
}