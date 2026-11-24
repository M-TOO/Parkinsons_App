package com.example.android.ui.screens

import androidx.compose.foundation.background
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

    // Intercept back button to perform proper sign out instead of leaking session
    androidx.activity.compose.BackHandler {
        com.example.android.data.api.ApiClient.clearToken()
        navController.navigate("login") {
            popUpTo(0) { inclusive = true }
        }
    }

    var filterOnlyFlagged by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.example.android.ui.components.NeuroScreenEmblem(size = 36.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Clinician Portal", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(6.dp))
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
                            val doctorName = com.example.android.data.api.ApiClient.currentUser?.full_name ?: "Dr. Mercy Too (Neurologist)"
                            Text(
                                text = "Attending: $doctorName",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { refreshData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MedicalPrimary)
                    }
                    IconButton(onClick = {
                        com.example.android.data.api.ApiClient.clearToken()
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }) {
                        Icon(
                            Icons.Default.ExitToApp,
                            contentDescription = "Sign Out",
                            tint = StatusAtypical
                        )
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
                        val flaggedCount = flaggedSessions.count {
                            (it.overall_stability_score ?: 100.0) < 75.0 ||
                            it.risk_label?.contains("Atypical", ignoreCase = true) == true ||
                            it.risk_label?.contains("Review", ignoreCase = true) == true ||
                            it.risk_label?.contains("Monitor", ignoreCase = true) == true
                        }
                        val displayedSessions = if (filterOnlyFlagged) {
                            flaggedSessions.filter {
                                (it.overall_stability_score ?: 100.0) < 75.0 ||
                                it.risk_label?.contains("Atypical", ignoreCase = true) == true ||
                                it.risk_label?.contains("Review", ignoreCase = true) == true ||
                                it.risk_label?.contains("Monitor", ignoreCase = true) == true
                            }
                        } else {
                            flaggedSessions
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Filter Controls
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = filterOnlyFlagged,
                                    onClick = { filterOnlyFlagged = true },
                                    label = { Text("Flagged Only ($flaggedCount)", fontSize = 12.sp) },
                                    leadingIcon = {
                                        if (filterOnlyFlagged) {
                                            Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = StatusAtypical.copy(alpha = 0.15f),
                                        selectedLabelColor = StatusAtypical
                                    )
                                )
                                FilterChip(
                                    selected = !filterOnlyFlagged,
                                    onClick = { filterOnlyFlagged = false },
                                    label = { Text("All Screenings (${flaggedSessions.size})", fontSize = 12.sp) }
                                )
                            }

                            if (displayedSessions.isEmpty()) {
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
                                        if (filterOnlyFlagged) "No atypical screenings found. Switch to 'All Screenings' to view all tests." else "No tests recorded yet.",
                                        color = TextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(displayedSessions) { session ->
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
                    }
                    selectedTab == 1 -> {
                        // ── Tab 1: Grouped Patient Roster (e.g. Alicia -> all her tests) ──
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
                                    val patientTests = flaggedSessions.filter { it.patient_id == patient.id }
                                    GroupedPatientRosterCard(
                                        patient = patient,
                                        tests = patientTests,
                                        onReviewTestClick = { selectedSession ->
                                            reviewingSession = selectedSession
                                            clinicalNotesText = selectedSession.clinical_notes ?: ""
                                            selectedReviewStatus = selectedSession.review_status
                                        }
                                    )
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
fun GroupedPatientRosterCard(
    patient: PatientSummary,
    tests: List<FlaggedSession>,
    onReviewTestClick: (FlaggedSession) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MedicalSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MedicalPrimary.copy(alpha = 0.1f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = patient.full_name.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = MedicalPrimary,
                                fontSize = 18.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(patient.full_name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            text = "${patient.email} • Born ${patient.year_of_birth}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Text(
                            text = "${tests.size} Recorded Screenings",
                            fontSize = 11.sp,
                            color = MedicalSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (patient.latest_score != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${patient.latest_score.toInt()}/100",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (patient.latest_score >= 75) StatusSteady else StatusAtypical
                            )
                            Text(
                                text = patient.latest_risk_label ?: "STABLE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (patient.latest_score >= 75) StatusSteady else StatusAtypical
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = MedicalPrimary
                        )
                    }
                }
            }

            // Expanded view: Shows all individual tests done by this patient!
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MedicalSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Screening History for ${patient.full_name}:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MedicalPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (tests.isEmpty()) {
                    Text(
                        text = "No tests recorded yet for this patient.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    tests.forEachIndexed { index, test ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MedicalSurfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Test #${tests.size - index} • ${test.created_at.take(16)}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    val score = test.overall_stability_score?.toInt() ?: 0
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (score >= 75) StatusSteady.copy(alpha = 0.15f) else StatusAtypical.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "$score/100 (${test.risk_label ?: "Screened"})",
                                            color = if (score >= 75) StatusSteady else StatusAtypical,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Spiral: ${test.drawing_status ?: "Complete"} • Wave: ${test.drawing_tremor_level ?: "Standard"} • Typing: ${test.tapping_status ?: "Recorded"}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )

                                if (!test.clinical_notes.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = androidx.compose.ui.graphics.Color(0xFFF0FDF4),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Attending Note: \"${test.clinical_notes}\"",
                                            fontSize = 11.sp,
                                            color = TextPrimary,
                                            modifier = Modifier.padding(6.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { onReviewTestClick(test) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MedicalPrimary),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (test.clinical_notes.isNullOrBlank()) "Add Neurologist Notes" else "Edit Clinical Review",
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
