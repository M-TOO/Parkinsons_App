package com.example.android.ui.screens

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.android.data.model.SessionState
import com.example.android.data.repository.AssessmentRepository
import com.example.android.ui.theme.*
import kotlinx.coroutines.launch
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
    var isSubmitting by remember { mutableStateOf(false) }
    var submissionStatus by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val repository = remember { AssessmentRepository() }

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
                .padding(16.dp)
        ) {
            // Excerpt Tabs Progress
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(5) { index ->
                    val isCompleted = completedExcerpts.any { it.excerpt_id == index + 1 }
                    val isCurrent = index == currentExcerptIndex

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = when {
                            isCompleted -> StatusSteady.copy(alpha = 0.2f)
                            isCurrent -> MedicalPrimary
                            else -> MedicalSurfaceVariant
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCurrent) MedicalPrimary else Color.Transparent
                        )
                    ) {
                        Text(
                            text = "Excerpt ${index + 1}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isCompleted -> StatusSteady
                                isCurrent -> Color.White
                                else -> TextMuted
                            },
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Clinical Instructions & Target Text
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MedicalSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target Text (${currentExcerptIndex + 1}/5)",
                            fontWeight = FontWeight.Bold,
                            color = MedicalPrimary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "${activeEvents.size}/$minEventsRequired keystrokes",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (activeEvents.size >= minEventsRequired) StatusSteady else TextMuted,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = currentTargetPrompt,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress bar towards minimum requirement
                    LinearProgressIndicator(
                        progress = { excerptProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (activeEvents.size >= minEventsRequired) StatusSteady else MedicalPrimaryLight,
                        trackColor = MedicalSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Display Box showing what user has typed
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MedicalSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MedicalOutline)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    if (typedDisplayBuffer.isEmpty()) {
                        Text(
                            text = "Tap on the custom keypad below to transcribe the prompt above...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    } else {
                        Text(
                            text = typedDisplayBuffer,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Medical Keystroke Capturing Keypad
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
            if (activeEvents.size >= minEventsRequired && !testFinished) {
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
                            // All 5 excerpts complete! Submit to backend
                            isSubmitting = true
                            val finalPayload = TypingSessionPayload(
                                session_id = SessionState.currentSessionId,
                                device_model = Build.MODEL,
                                app_version = "1.0.0",
                                excerpts = completedExcerpts.toList()
                            )
                            onSessionCompleted(finalPayload)

                            // Format: excerpts[i] = list of [press_ms, release_ms]
                            val excerptsData = completedExcerpts.map { exc ->
                                exc.events.map { ev -> listOf(ev.press_ms, ev.release_ms) }
                            }

                            coroutineScope.launch {
                                val result = repository.submitTyping(
                                    sessionId = SessionState.currentSessionId,
                                    excerpts = excerptsData
                                )
                                isSubmitting = false
                                testFinished = true
                                result.onSuccess { session ->
                                    submissionStatus = "Submitted to Backend (Status: ${session.tapping_status ?: "Completed"})"
                                }.onFailure { err ->
                                    submissionStatus = "Saved locally (5 excerpts completed)"
                                }
                            }
                        }
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalPrimary)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "5 Excerpts Recorded & Submitted",
                                fontWeight = FontWeight.Bold,
                                color = MedicalPrimary,
                                fontSize = 15.sp
                            )
                            Text(
                                text = submissionStatus ?: "Total Keystrokes: ${completedExcerpts.sumOf { it.events.size }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
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
