package com.example.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.android.ui.theme.MedicalPrimary
import com.example.android.ui.theme.MedicalPrimaryDark
import com.example.android.ui.theme.MedicalSecondary

/**
 * Standardized NeuroScreen Logo Component
 * Consistent neurological AI branding across Splash, Login, Register, Home, and Clinician Portal.
 */
@Composable
fun NeuroScreenEmblem(
    size: Dp = 64.dp,
    iconSize: Dp = size * 0.6f,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MedicalSecondary,
                        MedicalPrimary
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Psychology,
            contentDescription = "NeuroScreen Brain Emblem",
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun NeuroScreenBranding(
    textColor: Color = Color.White,
    subtitleColor: Color = Color.White.copy(alpha = 0.85f),
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = "NeuroScreen",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "AI Parkinson's Motor Biomarker Screening",
            fontSize = 13.sp,
            color = subtitleColor
        )
    }
}
