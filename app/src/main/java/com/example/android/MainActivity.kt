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
import com.example.android.ui.screens.DrawingTestScreen
import com.example.android.ui.screens.HomeScreen
import com.example.android.ui.screens.SplashScreen
import com.example.android.ui.screens.TypingTestScreen
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
                                    navController.navigate("home") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // 2. Home Dashboard
                        composable("home") {
                            HomeScreen(navController = navController)
                        }

                        // 3. AI Spec: Spiral Drawing (Square 1:1 Canvas)
                        composable("drawing_spiral") {
                            DrawingTestScreen(task = "spiral", navController = navController)
                        }

                        // 4. AI Spec: Wave Drawing (Square 1:1 Canvas)
                        composable("drawing_wave") {
                            DrawingTestScreen(task = "wave", navController = navController)
                        }

                        // 5. Typing Test
                        composable("typing_test") {
                            TypingTestScreen(navController = navController)
                        }
                    }
                }
            }
        }
    }
}