package com.example.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.android.data.model.DetailedScreeningReview
import com.example.android.data.model.SessionState
import com.example.android.data.repository.AssessmentRepository
import com.example.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    var showMenu by remember { mutableStateOf(false) }
    val repository = remember { AssessmentRepository() }
    var latestSessionReview by remember { mutableStateOf<DetailedScreeningReview?>(null) }
    var hasPastSessions by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = repository.getSessions()
        result.onSuccess { list ->
            if (list.isNotEmpty()) {
                val latest = list.first()
                val cleanDate = try {
                    latest.created_at.replace("T", " ").take(16)
                } catch (e: Exception) {
                    latest.created_at
                }
                val label = when (latest.risk_label) {
                    "No flags", "Typical pattern" -> "STABLE"
                    "Inconclusive" -> "MONITOR"
                    "Review suggested", "Atypical pattern" -> "ATYPICAL"
                    else -> latest.risk_label ?: "STABLE"
                }
                latestSessionReview = DetailedScreeningReview(
                    sessionId = latest.session_id,
                    date = cleanDate,
                    overallScore = latest.overall_stability_score?.toInt() ?: 80,
                    riskLabel = label,
                    spiralTremorLevel = latest.drawing_tremor_level ?: "Low Tremor",
                    spiralStatus = latest.drawing_status ?: "Fluid",
                    waveTremorLevel = latest.drawing_tremor_level ?: "Low Tremor",
                    waveStatus = latest.drawing_status ?: "Fluid",
                    tappingStabilityMatch = latest.overall_stability_score?.toFloat() ?: 80f,
                    tappingStatus = latest.tapping_status ?: "Steady",
                    clinicalNotes = latest.clinical_notes,
                    reviewedBy = latest.reviewed_by
                )
                hasPastSessions = true
            } else {
                hasPastSessions = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "NeuroScreen",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AI Digital Biomarker Screening",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Screening History") },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                navController.navigate("history")
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Sign Out", color = StatusAtypical) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = StatusAtypical) },
                            onClick = {
                                showMenu = false
                                com.example.android.data.api.ApiClient.clearToken()
                                navController.navigate("login") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
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

            // Hero Quick-Start Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MedicalPrimary)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "AI Motor Screening Protocol",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Complete 3 validated tests: Spiral Drawing, Wave Drawing, and 5 Keystroke Excerpts.",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                SessionState.startNewSession()
                                navController.navigate("drawing_spiral")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = MedicalPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Start Test Sequence", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }

            // Latest / Past Screening Movement Report Card
            if (hasPastSessions && latestSessionReview != null) {
                item {
                    OutlinedButton(
                        onClick = {
                            SessionState.selectedSessionForReview = latestSessionReview
                            navController.navigate("results")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null, tint = MedicalPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Latest Movement Report (${latestSessionReview!!.date})", color = MedicalPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item {
                Text(
                    text = "AI Model Tests (Trained Spec)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Test 1: Spiral Drawing
            item {
                ModuleCard(
                    title = "1. Spiral Drawing Test",
                    description = "Draw an Archimedean spiral on a 1:1 square canvas. Evaluated by the Drawing Vision Model.",
                    icon = Icons.Default.Gesture,
                    badgeText = "Vision Model (32x32)",
                    onClick = {
                        SessionState.startNewSession()
                        navController.navigate("drawing_spiral")
                    }
                )
            }

            // Test 2: Wave Drawing
            item {
                ModuleCard(
                    title = "2. Wave Drawing Test",
                    description = "Draw a sinusoidal wave pattern across a square canvas to evaluate kinematic tremor.",
                    icon = Icons.Default.Gesture,
                    badgeText = "Vision Model (32x32)",
                    onClick = {
                        SessionState.startNewSession()
                        navController.navigate("drawing_wave")
                    }
                )
            }

            // Test 3: Typing Dynamics (5 Excerpts)
            item {
                ModuleCard(
                    title = "3. Typing Dynamics Test",
                    description = "Collect 5 text excerpts capturing millisecond press/release times and touch pressure.",
                    icon = Icons.Default.Keyboard,
                    badgeText = "iPrognosis Spec (5 Excerpts)",
                    onClick = {
                        SessionState.startNewSession()
                        navController.navigate("typing_test")
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun ModuleCard(
    title: String,
    description: String,
    icon: ImageVector,
    badgeText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MedicalSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(10.dp),
                color = MedicalSurfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = MedicalPrimary)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MedicalPrimaryLight.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = badgeText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MedicalPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted
            )
        }
    }
}
