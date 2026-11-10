package com.example.android.ui.screens

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.android.ui.theme.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypingTestScreen(
    navController: NavController,
    onComplete: (stabilityScore: Float) -> Unit = {}
) {
    val targetKeystrokes = 30
    var keystrokeCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    val tapIntervals = remember { mutableStateListOf<Long>() }

    var activeKey by remember { mutableStateOf<String?>(null) }
    var testFinished by remember { mutableStateOf(false) }
    var calculatedStability by remember { mutableStateOf(0f) }

    val progress by animateFloatAsState(
        targetValue = min(1f, keystrokeCount.toFloat() / targetKeystrokes.toFloat()),
        label = "progress"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Typing Dynamics Test", fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Instructions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MedicalSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Finger Tapping Rhythm Exercise",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MedicalPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap the two alternating buttons below (LEFT and RIGHT) as rapidly and steadily as possible with your index finger.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Progress Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Taps: $keystrokeCount / $targetKeystrokes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${(progress * 100).toInt()}% Complete",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MedicalPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MedicalPrimary,
                trackColor = MedicalSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Dual Alternating Tapping Pads
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Button LEFT
                Button(
                    onClick = {
                        if (!testFinished) {
                            val now = SystemClock.uptimeMillis()
                            if (lastTapTime > 0) {
                                tapIntervals.add(now - lastTapTime)
                            }
                            lastTapTime = now
                            keystrokeCount++
                            activeKey = "LEFT"

                            if (keystrokeCount >= targetKeystrokes) {
                                testFinished = true
                                // Calculate variance in tap intervals (stability metric)
                                val avg = tapIntervals.average()
                                val variance = tapIntervals.map { abs(it - avg) }.average()
                                calculatedStability = max(70f, min(96f, (100f - (variance * 0.15f)).toFloat()))
                                onComplete(calculatedStability)
                            }
                        }
                    },
                    modifier = Modifier
                        .size(130.dp)
                        .border(
                            width = if (activeKey == "LEFT") 3.dp else 1.dp,
                            color = if (activeKey == "LEFT") MedicalPrimaryLight else MedicalOutline,
                            shape = CircleShape
                        ),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeKey == "LEFT") MedicalPrimaryLight.copy(alpha = 0.2f) else MedicalSurface,
                        contentColor = MedicalPrimary
                    ),
                    enabled = !testFinished
                ) {
                    Text(
                        text = "LEFT\nTAP",
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                // Button RIGHT
                Button(
                    onClick = {
                        if (!testFinished) {
                            val now = SystemClock.uptimeMillis()
                            if (lastTapTime > 0) {
                                tapIntervals.add(now - lastTapTime)
                            }
                            lastTapTime = now
                            keystrokeCount++
                            activeKey = "RIGHT"

                            if (keystrokeCount >= targetKeystrokes) {
                                testFinished = true
                                val avg = tapIntervals.average()
                                val variance = tapIntervals.map { abs(it - avg) }.average()
                                calculatedStability = max(70f, min(96f, (100f - (variance * 0.15f)).toFloat()))
                                onComplete(calculatedStability)
                            }
                        }
                    },
                    modifier = Modifier
                        .size(130.dp)
                        .border(
                            width = if (activeKey == "RIGHT") 3.dp else 1.dp,
                            color = if (activeKey == "RIGHT") MedicalPrimaryLight else MedicalOutline,
                            shape = CircleShape
                        ),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeKey == "RIGHT") MedicalPrimaryLight.copy(alpha = 0.2f) else MedicalSurface,
                        contentColor = MedicalPrimary
                    ),
                    enabled = !testFinished
                ) {
                    Text(
                        text = "RIGHT\nTAP",
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Real-Time Feedback Card
            if (tapIntervals.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MedicalSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Last Interval", style = MaterialTheme.typography.labelSmall)
                            Text("${tapIntervals.last()} ms", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MedicalPrimary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Rhythm", style = MaterialTheme.typography.labelSmall)
                            Text("Cadence Steady", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = StatusSteady)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Completion Banner
            if (testFinished) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MedicalSurfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "Rhythm Cadence Computed",
                                    fontWeight = FontWeight.Bold,
                                    color = MedicalPrimary,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Tap Interval Stability: $calculatedStability% (Steady)",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Button(
                                onClick = { navController.navigateUp() },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusSteady),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Done")
                            }
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        keystrokeCount = 0
                        tapIntervals.clear()
                        lastTapTime = 0L
                        activeKey = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = TextSecondary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Tapping Sequence", color = TextSecondary)
                }
            }
        }
    }
}