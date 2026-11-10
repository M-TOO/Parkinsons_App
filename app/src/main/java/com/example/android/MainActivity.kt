package com.example.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.android.ui.screens.SplashScreen
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

                        // 2. Home Destination (Will connect full HomeScreen next)
                        composable("home") {
                            // Temporary placeholder until we paste HomeScreen
                            Greeting("NeuroScreen Dashboard")
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun Greeting(name: String) {
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = "Welcome to $name!",
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge
        )
    }
}