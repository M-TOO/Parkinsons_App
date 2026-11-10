package com.example.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.android.ui.theme.*

// ── Data model for a past session ──────────────────────────────────────────
data class SessionHistory(
    val date: String,
    val score: Int,
    val riskLabel: String,       // "STABLE" | "MONITOR" | "ATYPICAL"
    val spiralResult: String,
    val waveResult: String,
    val typingResult: String
)

// ── Demo data (replaced by GET /sessions in Section 9) ────────────────────
private val mockHistory = listOf(
    SessionHistory("10 Nov 2026", 88, "STABLE",   "Fluid",    "Fluid",    "Steady"),
    SessionHistory("03 Nov 2026", 74, "MONITOR",  "Mild",     "Fluid",    "Variable"),
    SessionHistory("27 Oct 2026", 61, "MONITOR",  "Mild",     "Mild",     "Variable"),
    SessionHistory("15 Oct 2026", 45, "ATYPICAL", "Elevated", "Elevated", "Irregular"),
    SessionHistory("01 Oct 2026", 79, "STABLE",   "Fluid",    "Mild",     "Steady")
)

// ── Main Screen ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Screening History", fontWeight = FontWeight.Bold)
                        Text(
                            "Past motor assessments",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
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
        containerColor = MedicalBackground   // ← was BackgroundLight (doesn't exist)
    ) { padding ->
        if (mockHistory.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = TextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No assessments yet", color = TextMuted, fontSize = 16.sp)
                    Text(
                        "Complete your first screening from the Home screen",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "${mockHistory.size} sessions recorded",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(mockHistory) { session ->
                    SessionHistoryCard(session)
                }
            }
        }
    }
}

// ── Individual Session Card ────────────────────────────────────────────────
@Composable
fun SessionHistoryCard(session: SessionHistory) {
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
        modifier = Modifier.fillMaxWidth(),
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
