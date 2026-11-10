package com.example.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
                        DropdownMenuItem(
                            text = { Text("Clinician Mode (Admin)") },
                            leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                            onClick = { showMenu = false }
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
                            onClick = { navController.navigate("drawing_spiral") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = MedicalPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Start Test Sequence", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { navController.navigate("results") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Assessment, contentDescription = null, tint = MedicalPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Sample Movement Report", color = MedicalPrimary)
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
                    onClick = { navController.navigate("drawing_spiral") }
                )
            }

            // Test 2: Wave Drawing
            item {
                ModuleCard(
                    title = "2. Wave Drawing Test",
                    description = "Draw a sinusoidal wave pattern across a square canvas to evaluate kinematic tremor.",
                    icon = Icons.Default.Gesture,
                    badgeText = "Vision Model (32x32)",
                    onClick = { navController.navigate("drawing_wave") }
                )
            }

            // Test 3: Typing Dynamics (5 Excerpts)
            item {
                ModuleCard(
                    title = "3. Typing Dynamics Test",
                    description = "Collect 5 text excerpts capturing millisecond press/release times and touch pressure.",
                    icon = Icons.Default.Keyboard,
                    badgeText = "iPrognosis Spec (5 Excerpts)",
                    onClick = { navController.navigate("typing_test") }
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
