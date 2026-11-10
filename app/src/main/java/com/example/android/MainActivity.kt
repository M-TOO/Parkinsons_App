package com.example.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.android.ui.screens.*
import com.example.android.ui.theme.ParkinsonsAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ParkinsonsAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "splash"
                    ) {
                        // 1. Splash Screen
                        composable("splash") {
                            SplashScreen(
                                onTimeout = {
                                    navController.navigate("login") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // 2. Login Screen (Role-Based Access Control)
                        composable("login") {
                            LoginScreen(
                                navController = navController,
                                onLoginSuccess = { role ->
                                    val destination = if (role == "admin") "admin_dashboard" else "home"
                                    navController.navigate(destination) {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // 3. Register Screen
                        composable("register") {
                            RegisterScreen(
                                navController = navController,
                                onRegisterSuccess = { _, _ ->
                                    navController.navigate("home") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // 4. Home Dashboard
                        composable("home") {
                            HomeScreen(navController = navController)
                        }

                        // 5. Spiral Drawing
                        composable("drawing_spiral") {
                            DrawingTestScreen(task = "spiral", navController = navController)
                        }

                        // 6. Wave Drawing
                        composable("drawing_wave") {
                            DrawingTestScreen(task = "wave", navController = navController)
                        }

                        // 7. Typing Dynamics Test
                        composable("typing_test") {
                            TypingTestScreen(navController = navController)
                        }

                        // 8. Screening History
                        composable("history") {
                            HistoryScreen(navController = navController)
                        }

                        // 9. Movement Report
                        composable("results") {
                            ResultsScreen(
                                navController = navController,
                                result = AssessmentResult()
                            )
                        }

                        // 10. Clinician & Neurologist Portal (M-TOO)
                        composable("admin_dashboard") {
                            AdminDashboardScreen(navController = navController)
                        }
                    }
                }
            }
        }
    }
}
