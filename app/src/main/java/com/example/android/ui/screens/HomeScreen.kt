package com.example.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    var showMenu by remember { mutableStateOf(false) }

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
                            text = "Digital Motor Biomarkers",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    // Class Lecture Concept: Menus & Toolbars (Menus.pdf)
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Menu Options")
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
                                // Will navigate to history
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clinician Mode (Admin)") },
                            leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                // Will navigate to admin dashboard
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("About Assessment") },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = { showMenu = false }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MedicalSurface
                )
            )
        },
        containerColor = MedicalBackground
    ) { padding ->
        // Class Lecture Concept: RecyclerView Dynamic List (RecyclerView.pdf)
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
                            text = "Comprehensive Motor Assessment",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Complete 4 quick motor tests (approx. 3-5 mins) to generate your digital biomarker stability score.",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                 navController.navigate("drawing_test")  // <-- Updated
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = MedicalPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Begin Assessment", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }

            // Section Header
            item {
                Text(
                    text = "Individual Test Modules",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Module 1: Drawing Test
            item {
                ModuleCard(
                    title = "1. Spiral & Wave Drawing",
                    description = "Draw guided spiral and wave patterns to detect kinematic tremors.",
                    icon = Icons.Default.Gesture,
                    badgeText = "AI Vision Model",
                    onClick = { navController.navigate("drawing_test") }
                )
            }

            // Module 2: Typing Dynamics
            item {
                ModuleCard(
                    title = "2. Typing Dynamics Test",
                    description = "Measure millisecond key press and flight intervals.",
                    icon = Icons.Default.Keyboard,
                    badgeText = "iPrognosis Model",
                    onClick = { navController.navigate("typing_test") }
                )
            }

            // Module 3: Tremor Sensor Test
            item {
                ModuleCard(
                    title = "3. 10s Rest Tremor Sensor",
                    description = "Hold phone flat to analyze 4-6 Hz resting tremor frequencies.",
                    icon = Icons.Default.Vibration,
                    badgeText = "Sensor API",
                    onClick = { /* Navigate to Tremor */ }
                )
            }

            // Module 4: Symptom Survey
            item {
                ModuleCard(
                    title = "4. Clinical Symptom Survey",
                    description = "MDS-UPDRS standardized questionnaire for daily motor symptoms.",
                    icon = Icons.Default.Assignment,
                    badgeText = "Clinical Scale",
                    onClick = { /* Navigate to Survey */ }
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