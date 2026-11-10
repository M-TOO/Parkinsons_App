package com.example.android.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.android.ui.theme.*

// Data model passed into the report from each completed test
data class AssessmentResult(
    val overallScore: Int = 88,
    val riskLabel: String = "Stable",
    val spiralTremorLevel: String = "Low Tremor",
    val spiralStatus: String = "Fluid",
    val waveTremorLevel: String = "Low Tremor",
    val waveStatus: String = "Fluid",
    val tappingStabilityMatch: Float = 92.0f,
    val tappingStatus: String = "Steady",
    val modulesCompleted: Int = 3
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    navController: NavController,
    result: AssessmentResult = AssessmentResult() // Default demo values
) {
    // Animate the donut chart drawing in from 0 to the final score
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(result.overallScore) {
        animatedProgress.animateTo(
            targetValue = result.overallScore / 100f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
        )
    }

    val riskColor = when (result.riskLabel) {
        "Stable" -> StatusSteady
        "Review Suggested" -> StatusAtypical
        else -> StatusReview
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Movement Report", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MedicalSurface)
            )
        },
        containerColor = MedicalBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 1. Overall Stability Donut Score Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MedicalSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Composite Motor & Clinics Risk",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${result.modulesCompleted} Modules Assessed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Animated Donut Chart
                        DonutScoreChart(
                            progress = animatedProgress.value,
                            score = (animatedProgress.value * 100).toInt(),
                            primaryColor = MedicalPrimary,
                            trackColor = MedicalSurfaceVariant,
                            size = 150.dp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Risk Label Badge
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = riskColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Risk Status: ${result.riskLabel.uppercase()}",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = riskColor
                            )
                        }
                    }
                }
            }

            // 2. Biomarker Modality Breakdown Section Header
            item {
                Text(
                    text = "Biomarker Modality Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 3. Spiral Drawing Row
            item {
                MetricRow(
                    icon = Icons.Default.Gesture,
                    title = "Spiral Drawing",
                    value = result.spiralTremorLevel,
                    badge = result.spiralStatus,
                    badgeColor = if (result.spiralStatus == "Fluid") StatusSteady else StatusReview
                )
            }

            // 4. Wave Drawing Row
            item {
                MetricRow(
                    icon = Icons.Default.Gesture,
                    title = "Wave Drawing",
                    value = result.waveTremorLevel,
                    badge = result.waveStatus,
                    badgeColor = if (result.waveStatus == "Fluid") StatusSteady else StatusReview
                )
            }

            // 5. Typing Dynamics Row
            item {
                MetricRow(
                    icon = Icons.Default.Keyboard,
                    title = "Typing Dynamics",
                    value = "${result.tappingStabilityMatch}% Interval Stability",
                    badge = result.tappingStatus,
                    badgeColor = if (result.tappingStatus == "Steady") StatusSteady else StatusReview
                )
            }

            // 6. Medical Disclaimer
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusAtypical.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = StatusAtypical,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "This AI analysis is not a clinical diagnosis. These digital biomarker results must be reviewed by a qualified neurologist before any clinical decisions are made.",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusAtypical
                        )
                    }
                }
            }

            // 7. Recommended Next Steps Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MedicalSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSteady, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Recommended Next Steps", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        listOf(
                            "Perform daily self-monitoring test for a consistent baseline.",
                            "Maintain regular physical exercise and healthy lifestyle habits.",
                            "Share this report with your neurologist at the next clinical appointment.",
                            "Re-screen every 3-6 months or if symptoms change."
                        ).forEach { step ->
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text("• ", color = MedicalPrimary, fontWeight = FontWeight.Bold)
                                Text(step, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // 8. Done Button
            item {
                Button(
                    onClick = {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalPrimary)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Return to Home Dashboard", fontWeight = FontWeight.Bold)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun DonutScoreChart(
    progress: Float,
    score: Int,
    primaryColor: Color,
    trackColor: Color,
    size: Dp
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 20.dp.toPx()
            val radius = (this.size.minDimension - strokeWidth) / 2f
            val topLeft = Offset(this.size.width / 2 - radius, this.size.height / 2 - radius)
            val arcSize = Size(radius * 2, radius * 2)

            // Track (background ring)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress ring (animated)
            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MedicalPrimary
            )
            Text(
                text = "/ 100",
                fontSize = 13.sp,
                color = TextSecondary
            )
            Text(
                text = "Stability",
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
fun MetricRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    badge: String,
    badgeColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MedicalSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MedicalPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(text = value, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = badgeColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = badge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = badgeColor
                )
            }
        }
    }
}