package com.example.android.ui.screens

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.android.ui.theme.*
import java.util.UUID

// AI Spec Data Models (Matching Page 3 of Spec)
data class RawKeyEvent(
    val press_ms: Long,
    val release_ms: Long,
    val pressure: Float
)

data class TextExcerpt(
    val excerpt_id: Int,
    val events: List<RawKeyEvent>
)

data class TypingSessionPayload(
    val session_id: String,
    val device_model: String,
    val app_version: String,
    val excerpts: List<TextExcerpt>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypingTestScreen(
    navController: NavController,
    onSessionCompleted: (payload: TypingSessionPayload) -> Unit = {}
) {
    // 5 Standard Text Prompts as specified by AI team (1 long, 4 standard)
    val promptSentences = remember {
        listOf(
            "The quick brown fox jumps over the lazy dog repeatedly until the rhythm of the typing motion is recorded clearly.",
            "Pack my box with five dozen liquor jugs at the station.",
            "How vexingly quick daft zebras jump over the low garden fence.",
            "Sphinx of black quartz judge my vow in the morning daylight.",
            "Two driven jocks help fax my big quiz to the clinical laboratory."
        )
    }

    var currentExcerptIndex by remember { mutableIntStateOf(0) } // 0 to 4 (5 excerpts)
    val completedExcerpts = remember { mutableStateListOf<TextExcerpt>() }

    // Active excerpt recording
    val activeEvents = remember { mutableStateListOf<RawKeyEvent>() }
    var typedDisplayBuffer by remember { mutableStateOf("") }
    var testFinished by remember { mutableStateOf(false) }

    val currentTargetPrompt = promptSentences[currentExcerptIndex]
    val minEventsRequired = if (currentExcerptIndex == 0) 40 else 25

    val excerptProgress by animateFloatAsState(
        targetValue = (activeEvents.size.toFloat() / minEventsRequired.toFloat()).coerceIn(0f, 1f),
        label = "eventsProgress"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Typing Dynamics (5 Excerpts)", fontWeight = FontWeight.Bold) },
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
            // Excerpt Counter Badge & Overall Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MedicalPrimaryLight.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "EXCERPT ${currentExcerptIndex + 1} OF 5",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MedicalPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Events: ${activeEvents.size} / $minEventsRequired min",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { excerptProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MedicalPrimary,
                trackColor = MedicalSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Target Prompt Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MedicalSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Type the prompt below:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentTargetPrompt,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // User Typed Text Buffer Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, MedicalOutline)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Text(
                        text = if (typedDisplayBuffer.isEmpty()) "Start typing on the keypad below..." else typedDisplayBuffer,
                        color = if (typedDisplayBuffer.isEmpty()) TextMuted else TextPrimary,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // AI SPEC ON-SCREEN KEYBOARD (Captures press_ms, release_ms, and pressure)
            OnScreenTypingKeypad(
                onKeyRecorded = { event, charPressed ->
                    if (!testFinished) {
                        activeEvents.add(event)
                        if (charPressed == "⌫") {
                            if (typedDisplayBuffer.isNotEmpty()) {
                                typedDisplayBuffer = typedDisplayBuffer.dropLast(1)
                            }
                        } else {
                            typedDisplayBuffer += charPressed
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Navigation to Next Excerpt or Finish Test
            if (activeEvents.size >= minEventsRequired) {
                Button(
                    onClick = {
                        // Save current excerpt
                        completedExcerpts.add(
                            TextExcerpt(
                                excerpt_id = currentExcerptIndex + 1,
                                events = activeEvents.toList()
                            )
                        )
                        activeEvents.clear()
                        typedDisplayBuffer = ""

                        if (currentExcerptIndex < 4) {
                            currentExcerptIndex++
                        } else {
                            // All 5 excerpts complete! Generate payload
                            testFinished = true
                            val finalPayload = TypingSessionPayload(
                                session_id = UUID.randomUUID().toString(),
                                device_model = Build.MODEL,
                                app_version = "1.0.0",
                                excerpts = completedExcerpts.toList()
                            )
                            onSessionCompleted(finalPayload)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalPrimary)
                ) {
                    Text(
                        text = if (currentExcerptIndex < 4) "Save Excerpt & Continue (${currentExcerptIndex + 1}/5)" else "Complete Typing Test",
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        if (currentExcerptIndex < 4) Icons.Default.NavigateNext else Icons.Default.Check,
                        contentDescription = null
                    )
                }
            }

            // Test Completed Banner
            if (testFinished) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MedicalSurfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "5 Excerpts Successfully Recorded",
                                fontWeight = FontWeight.Bold,
                                color = MedicalPrimary,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Total Keystrokes: ${completedExcerpts.sumOf { it.events.size }} (AI Spec Ready)",
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
        }
    }
}

/**
 * On-Screen Medical Touchpad Keyboard that captures:
 * 1. press_ms (SystemClock.uptimeMillis())
 * 2. release_ms (SystemClock.uptimeMillis())
 * 3. pressure (Touch Pressure normalized 0.0 to 1.0)
 */
@Composable
fun OnScreenTypingKeypad(
    onKeyRecorded: (event: RawKeyEvent, char: String) -> Unit
) {
    val keyboardRows = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("Z", "X", "C", "V", "B", "N", "M", "⌫")
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keyboardRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { keyLabel ->
                    KeyCapButton(
                        label = keyLabel,
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .weight(if (keyLabel == "⌫") 1.5f else 1f),
                        onKeyRecord = { event ->
                            onKeyRecorded(event, keyLabel.lowercase())
                        }
                    )
                }
            }
        }

        // Spacebar Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            KeyCapButton(
                label = "SPACE",
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .padding(top = 4.dp),
                onKeyRecord = { event ->
                    onKeyRecorded(event, " ")
                }
            )
        }
    }
}

@Composable
fun KeyCapButton(
    label: String,
    modifier: Modifier = Modifier,
    onKeyRecord: (RawKeyEvent) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    var pressDownTime by remember { mutableLongStateOf(0L) }
    var pressPressure by remember { mutableFloatStateOf(0.5f) }

    Surface(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(6.dp))
            .pointerInput(label) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        val change = down.changes.firstOrNull() ?: continue

                        if (change.pressed && !isPressed) {
                            // Key Pressed Down
                            isPressed = true
                            pressDownTime = SystemClock.uptimeMillis()
                            pressPressure = change.pressure.coerceIn(0.1f, 1.0f)
                            change.consume()
                        } else if (!change.pressed && isPressed) {
                            // Key Released Up
                            isPressed = false
                            val releaseTime = SystemClock.uptimeMillis()
                            val event = RawKeyEvent(
                                press_ms = pressDownTime,
                                release_ms = releaseTime,
                                pressure = pressPressure
                            )
                            onKeyRecord(event)
                            change.consume()
                        }
                    }
                }
            },
        color = if (isPressed) MedicalPrimaryLight.copy(alpha = 0.35f) else MedicalSurface,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MedicalOutline),
        shadowElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = if (label == "SPACE") 12.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}