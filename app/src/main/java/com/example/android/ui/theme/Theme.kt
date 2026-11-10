package com.example.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = MedicalPrimary,
    onPrimary = MedicalSurface,
    secondary = MedicalSecondary,
    onSecondary = MedicalSurface,
    tertiary = MedicalTertiary,
    background = MedicalBackground,
    surface = MedicalSurface,
    onSurface = TextPrimary,
    surfaceVariant = MedicalSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = MedicalOutline
)

@Composable
fun ParkinsonsAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}