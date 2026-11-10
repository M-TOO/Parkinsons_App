package com.example.android.ui.screens

import androidx.compose.foundation.background
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
import com.example.android.data.model.*
import com.example.android.data.repository.AssessmentRepository
import com.example.android.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(navController: NavController) {
    val repository = remember { AssessmentRepository() }
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Flagged Triage, 1 = Patient Roster
    var stats by remember { mutableStateOf<AdminDashboardStats?>(null) }
    var patients by remember { mutableStateOf<List<PatientSummary>>(emptyList()) }
    var flaggedSessions by remember { mutableStateOf<List<FlaggedSession>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Dialog state for clinical review
    var reviewingSession by remember { mutableStateOf<FlaggedSession?>(null) }
    var clinicalNotesText by remember { mutableStateOf("") }
    var selectedReviewStatus by remember { mutableStateOf("Follow-up Required") }
    var isSubmittingReview by remember { mutableStateOf(false) }

    // Helper to refresh all admin data
    fun refreshData() {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            val statsRes = repository.getAdminStats()
            statsRes.onSuccess { stats = it }

            val flaggedRes = repository.getFlaggedSessions()
            flaggedRes.onSuccess { flaggedSessions = it }

            val patientsRes = repository.getAdminPatients()
            patientsRes.onSuccess { patients = it }

            if (statsRes.isFailure && flaggedRes.isFailure && patientsRes.isFailure) {
                errorMessage = "Could not connect to Clinician API. Ensure you are signed in as an administrator."
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Clinician Portal", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MedicalSecondary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "NEUROLOGY",
                                    color = MedicalSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Attending: Dr. Mercy Too",
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
                actions = {
                    IconButton(onClick = { refreshData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MedicalPrimary)
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
        ) {
            // ── KPI Overview Cards ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AdminStatCard(
                    title = "Patients",
                    value = "${stats?.total_patients ?: patients.size}",
                    icon = Icons.Default.People,
                    color = MedicalPrimary,
                    modifier = Modifier.weight(1f)
                )
                AdminStatCard(
                    title = "Flagged Triage",
                    value = "${stats?.flagged_assessments ?: flaggedSessions.size}",
                    icon = Icons.Default.WarningAmber,
                    color = StatusAtypical,
                    modifier = Modifier.weight(1f)
                )
                AdminStatCard(
                    title = "Pending",
                    value = "${stats?.pending_reviews ?: flaggedSessions.count { it.review_status == "Pending Review" }}",
                    icon = Icons.Default.PendingActions,
                    color = StatusReview,
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Tab Navigation ─────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MedicalSurface,
                contentColor = MedicalPrimary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Flagged Triage Queue", fontWeight = FontWeight.SemiBold)
                            if (flaggedSessions.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = StatusAtypical
                                ) {
                                    Text(
                                        text = "${flaggedSessions.size}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Patient Roster (${patients.size})", fontWeight = FontWeight.SemiBold) }
                )
            }

            // ── Tab Content ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when {
                    isLoading -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MedicalPrimary)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Loading clinical records...", color = TextMuted)
                        }
                    }
                    errorMessage != null && flaggedSessions.isEmpty() && patients.isEmpty() -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MedicalSurfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Notice", fontWeight = FontWeight.Bold, color = MedicalPrimary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    errorMessage!!,
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { refreshData() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MedicalPrimary)
                                ) {
                                    Text("Retry Connection")
                                }
                            }
                        }
                    }
                    selectedTab == 0 -> {
                        // ── Tab 0: Flagged Triage Queue ────────────────────
                        if (flaggedSessions.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = StatusSteady,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "No Flagged Assessments",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "All patient screenings are currently within stable motor thresholds.",
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(flaggedSessions) { session ->
                                    FlaggedSessionCard(
                                        session = session,
                                        onReviewClick = {
                                            reviewingSession = session
                                            clinicalNotesText = session.clinical_notes ?: ""
                                            selectedReviewStatus = session.review_status
                                        }
                                    )
                                }
                            }
                        }
                    }
                    selectedTab == 1 -> {
                        // ── Tab 1: Patient Roster ──────────────────────────
                        if (patients.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.GroupOff, contentDescription = null, tint = TextMuted, modifier = Modifier.size(56.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("No patients registered yet", color = TextMuted)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(patients) { patient ->
                                    PatientRosterCard(patient = patient)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Neurologist Review & Clinical Notes Dialog ─────────────────────────
    if (reviewingSession != null) {
        val session = reviewingSession!!
        AlertDialog(
            onDismissRequest = { if (!isSubmittingReview) reviewingSession = null },
            title = {
                Text(
                    text = "Neurologist Clinical Review",
                    fontWeight = FontWeight.Bold,
                    color = MedicalPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Patient: ${session.patient_name} (Born ${session.patient_birth_year ?: "N/A"})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "AI Stability Score: ${session.overall_stability_score?.toInt() ?: "--"}/100 • Flag: ${session.risk_label ?: "Atypical"}",
                        color = StatusAtypical,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    HorizontalDivider()

                    Text("Clinical Review Status", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Follow-up Required", "Reviewed - Stable").forEach { statusOption ->
                            val isSelected = selectedReviewStatus == statusOption
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedReviewStatus = statusOption },
                                label = { Text(statusOption, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (statusOption.contains("Follow")) StatusReview.copy(alpha = 0.2f) else StatusSteady.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = clinicalNotesText,
                        onValueChange = { clinicalNotesText = it },
                        label = { Text("Attending Neurologist Notes") },
                        placeholder = { Text("e.g. Kinematic tremor observed on Archimedean spiral. Prescribe 7-day motor log.") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSubmittingReview = true
                        coroutineScope.launch {
                            val res = repository.submitClinicalReview(
                                sessionId = session.session_id,
                                notes = clinicalNotesText.ifBlank { "Reviewed by attending neurologist." },
                                status = selectedReviewStatus
                            )
                            isSubmittingReview = false
                            reviewingSession = null
                            res.onSuccess { refreshData() }
                        }
                    },
                    enabled = !isSubmittingReview,
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalPrimary)
                ) {
                    if (isSubmittingReview) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                    } else {
                        Text("Save Clinical Review")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { reviewingSession = null }, enabled = !isSubmittingReview) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ── Supporting UI Components ───────────────────────────────────────────────
@Composable
fun AdminStatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MedicalSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
            Text(title, fontSize = 11.sp, color = TextMuted)
        }
    }
}

@Composable
fun FlaggedSessionCard(
    session: FlaggedSession,
    onReviewClick: () -> Unit
) {
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
                Column {
                    Text(session.patient_name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        text = "Born ${session.patient_birth_year ?: "N/A"} • ${session.patient_email}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StatusAtypical.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = session.risk_label ?: "FLAGGED",
                        color = StatusAtypical,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Stability Score", fontSize = 11.sp, color = TextMuted)
                    Text("${session.overall_stability_score?.toInt() ?: "--"}/100", fontWeight = FontWeight.Bold, color = StatusAtypical)
                }
                Column {
                    Text("Tremor Level", fontSize = 11.sp, color = TextMuted)
                    Text(session.drawing_tremor_level ?: "Moderate", fontWeight = FontWeight.SemiBold, color = StatusReview)
                }
                Column {
                    Text("Review Status", fontSize = 11.sp, color = TextMuted)
                    Text(
                        text = session.review_status,
                        fontWeight = FontWeight.SemiBold,
                        color = if (session.review_status.contains("Reviewed")) StatusSteady else StatusReview
                    )
                }
            }

            if (!session.clinical_notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MedicalSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Notes: \"${session.clinical_notes}\"",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onReviewClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MedicalPrimary)
            ) {
                Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Neurologist Review & Notes")
            }
        }
    }
}

@Composable
fun PatientRosterCard(patient: PatientSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MedicalSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(patient.full_name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    text = "${patient.email} • Born ${patient.year_of_birth}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Total Screenings: ${patient.total_assessments} • Last: ${patient.latest_screening_date ?: "No tests yet"}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            if (patient.latest_score != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${patient.latest_score.toInt()}/100",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (patient.latest_score >= 70) StatusSteady else StatusAtypical
                    )
                    Text(
                        text = patient.latest_risk_label ?: "STABLE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMuted
                    )
                }
            }
        }
    }
}
