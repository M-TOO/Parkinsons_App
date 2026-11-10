package com.example.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.android.data.model.DetailedScreeningReview
import com.example.android.data.model.SessionState
import com.example.android.data.repository.AssessmentRepository
import com.example.android.ui.theme.*

// ── Data model for a past session ──────────────────────────────────────────
data class SessionHistory(
    val sessionId: String = "",
    val date: String,
    val score: Int,
    val riskLabel: String,       // "STABLE" | "MONITOR" | "ATYPICAL"
    val spiralResult: String,
    val waveResult: String,
    val typingResult: String,
    val clinicalNotes: String? = null,
    val reviewedBy: String? = null
)

// ── Demo fallback data (shown when new account has 0 tests yet) ────────────
private val fallbackHistory = listOf(
    SessionHistory(
        sessionId = "sample-1",
        date = "Sample Screening",
        score = 88,
        riskLabel = "STABLE",
        spiralResult = "Fluid",
        waveResult = "Fluid",
        typingResult = "Steady"
    )
)

// ── Main Screen ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController) {
    val repository = remember { AssessmentRepository() }
    var sessions by remember { mutableStateOf<List<SessionHistory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        val result = repository.getSessions()
        result.onSuccess { list ->
            if (list.isNotEmpty()) {
                sessions = list.map { s ->
                    val cleanDate = try {
                        s.created_at.replace("T", " ").take(16)
                    } catch (e: Exception) {
                        s.created_at
                    }
                    val label = when (s.risk_label) {
                        "No flags", "Typical pattern" -> "STABLE"
                        "Inconclusive" -> "MONITOR"
                        "Review suggested", "Atypical pattern" -> "ATYPICAL"
                        else -> s.risk_label ?: "STABLE"
                    }
                    SessionHistory(
                        sessionId = s.session_id,
                        date = cleanDate,
                        score = s.overall_stability_score?.toInt() ?: 80,
                        riskLabel = label,
                        spiralResult = s.drawing_status ?: "Fluid",
                        waveResult = s.drawing_tremor_level ?: "Low",
                        typingResult = s.tapping_status ?: "Steady",
                        clinicalNotes = s.clinical_notes,
                        reviewedBy = s.reviewed_by
                    )
                }
            } else {
                sessions = emptyList()
            }
        }.onFailure { err ->
            errorMessage = err.localizedMessage
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Screening History", fontWeight = FontWeight.Bold)
                        Text(
                            "Live Database Sessions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MedicalPrimaryLight
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MedicalSurface
                )
            )
        },
        containerColor = MedicalBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MedicalPrimary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Loading sessions from server...", color = TextMuted, fontSize = 14.sp)
                    }
                }
                sessions.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = TextMuted
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No assessments recorded yet",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "When you complete motor assessments, your results and stability scores will appear here in real time.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { navController.navigate("home") },
                            colors = ButtonDefaults.buttonColors(containerColor = MedicalPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Start an Assessment")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${sessions.size} session(s) in database",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextMuted
                                )
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = StatusSteady.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "LIVE SYNC",
                                        color = StatusSteady,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        items(sessions) { session ->
                            SessionHistoryCard(
                                session = session,
                                onClick = {
                                    SessionState.selectedSessionForReview = DetailedScreeningReview(
                                        sessionId = session.sessionId,
                                        date = session.date,
                                        overallScore = session.score,
                                        riskLabel = session.riskLabel,
                                        spiralTremorLevel = session.spiralResult,
                                        spiralStatus = session.spiralResult,
                                        waveTremorLevel = session.waveResult,
                                        waveStatus = session.waveResult,
                                        tappingStabilityMatch = session.score.toFloat(),
                                        tappingStatus = session.typingResult,
                                        clinicalNotes = session.clinicalNotes,
                                        reviewedBy = session.reviewedBy
                                    )
                                    navController.navigate("results")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Individual Session Card ────────────────────────────────────────────────
@Composable
fun SessionHistoryCard(
    session: SessionHistory,
    onClick: () -> Unit = {}
) {
    val (statusColor, statusBg) = when (session.riskLabel) {
        "STABLE"  -> Pair(StatusSteady,   Color(0xFFD1FAE5))  // green
        "MONITOR" -> Pair(StatusReview,   Color(0xFFFEF3C7))  // amber
        else      -> Pair(StatusAtypical, Color(0xFFFFE4E6))  // red
    }
    val scoreColor = when {
        session.score >= 80 -> StatusSteady
        session.score >= 60 -> StatusReview
        else                -> StatusAtypical
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MedicalSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Top row: date + risk badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MedicalPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(session.date, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = statusBg
                ) {
                    Text(
                        text = session.riskLabel,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFE5E7EB))
            Spacer(modifier = Modifier.height(10.dp))

            // Score row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    tint = scoreColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Stability Score  ", fontSize = 13.sp, color = TextMuted)
                Text(
                    "${session.score}/100",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = scoreColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Modality breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ModalityChip(label = "Spiral", value = session.spiralResult)
                ModalityChip(label = "Wave",   value = session.waveResult)
                ModalityChip(label = "Typing", value = session.typingResult)
            }

            // Attending Neurologist Review Indicator & Notes preview
            if (!session.clinicalNotes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MedicalPrimaryLight.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MedicalPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Neurologist Notes Available",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MedicalPrimary
                            )
                            Text(
                                text = "\"${session.clinicalNotes}\"",
                                fontSize = 11.sp,
                                color = TextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap to review full report & download",
                    fontSize = 11.sp,
                    color = MedicalPrimary,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MedicalPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun ModalityChip(label: String, value: String) {
    val color = when (value) {
        "Fluid", "Steady"       -> StatusSteady    // green
        "Mild", "Variable"      -> StatusReview    // amber
        else                    -> StatusAtypical  // red
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = TextMuted)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
