package com.powertrack.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.powertrack.app.data.local.SessionManager
import com.powertrack.app.ui.screens.*
import com.powertrack.app.ui.theme.PowerTrackTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionManager = SessionManager(this)

        setContent {
            PowerTrackTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PowerTrackNavigation(
                        isLoggedIn = sessionManager.isLoggedIn
                    )
                }
            }
        }
    }
}

@Composable
fun PowerTrackNavigation(isLoggedIn: Boolean) {
    val navController = rememberNavController()
    val startDest = if (isLoggedIn) "home" else "login"

    NavHost(
        navController = navController,
        startDestination = startDest
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate("register")
                }
            )
        }

        composable("register") {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate("home") {
                        popUpTo("register") { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("home") {
            HomeScreen(
                onNavigateToLiveTracking = { navController.navigate("live_tracking") },
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToBattery = { navController.navigate("battery") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToOfflineSync = { navController.navigate("offline_sync") },
                onNavigateToAccount = { navController.navigate("account") },
                onNavigateToPermissions = { navController.navigate("permissions") }
            )
        }

        composable("live_tracking") {
            LiveTrackingScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("history") {
            LocationHistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("battery") {
            BatteryStatusScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("permissions") {
            PermissionExplanationScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            TrackingSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("offline_sync") {
            OfflineSyncScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("account") {
            AccountScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
    }
}
