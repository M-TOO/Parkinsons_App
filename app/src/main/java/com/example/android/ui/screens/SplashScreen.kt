package com.example.android.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.android.ui.components.NeuroScreenBranding
import com.example.android.ui.components.NeuroScreenEmblem
import com.example.android.ui.theme.MedicalPrimary
import com.example.android.ui.theme.MedicalPrimaryDark
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    // Pulse animation for the medical logo
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Class Concept: Activity Lifecycle & Delayed Intent Transition (Activities.pdf)
    LaunchedEffect(key1 = true) {
        delay(2500L) // Show splash for 2.5 seconds
        onTimeout()  // Transition to Home
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MedicalPrimary,
                        MedicalPrimaryDark
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier.scale(scale),
                contentAlignment = Alignment.Center
            ) {
                NeuroScreenEmblem(size = 96.dp)
            }

            Spacer(modifier = Modifier.height(28.dp))

            NeuroScreenBranding()

            Spacer(modifier = Modifier.height(48.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Color.White.copy(alpha = 0.85f),
                strokeWidth = 3.dp
            )
        }
    }
}