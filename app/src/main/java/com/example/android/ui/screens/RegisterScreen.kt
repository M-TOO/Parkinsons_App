package com.example.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.android.data.repository.AssessmentRepository
import com.example.android.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    navController: NavController,
    onRegisterSuccess: (name: String, email: String) -> Unit = { _, _ -> }
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var yearOfBirth by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val repository = remember { AssessmentRepository() }
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(MedicalPrimary, Color(0xFF042F2E))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Header
            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text("Create Patient Account", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Register to begin your motor screening", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MedicalSurface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Full Name
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it; errorMessage = null },
                        label = { Text("Full Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MedicalPrimary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MedicalPrimary,
                            focusedLabelColor = MedicalPrimary
                        )
                    )

                    // Email
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = null },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = MedicalPrimary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MedicalPrimary,
                            focusedLabelColor = MedicalPrimary
                        )
                    )

                    // Year of Birth
                    OutlinedTextField(
                        value = yearOfBirth,
                        onValueChange = { if (it.length <= 4) yearOfBirth = it; errorMessage = null },
                        label = { Text("Year of Birth (e.g. 1968)") },
                        leadingIcon = { Icon(Icons.Default.Cake, contentDescription = null, tint = MedicalPrimary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        supportingText = {
                            Text(
                                text = "Required for age-calibrated motor performance baselines",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MedicalPrimary,
                            focusedLabelColor = MedicalPrimary
                        )
                    )

                    // Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MedicalPrimary) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = TextMuted
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MedicalPrimary,
                            focusedLabelColor = MedicalPrimary
                        )
                    )

                    // Confirm Password
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMessage = null },
                        label = { Text("Confirm Password") },
                        leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null, tint = MedicalPrimary) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MedicalPrimary,
                            focusedLabelColor = MedicalPrimary
                        )
                    )

                    // Error Message
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = StatusAtypical,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // Register Button
                    Button(
                        onClick = {
                            val yob = yearOfBirth.toIntOrNull()
                            when {
                                fullName.isBlank() -> errorMessage = "Please enter your full name."
                                email.isBlank() || !email.contains("@") -> errorMessage = "Please enter a valid email."
                                yob == null || yob < 1900 || yob > currentYear -> errorMessage = "Enter a valid year of birth (1900 - $currentYear)."
                                password.length < 8 -> errorMessage = "Password must be at least 8 characters."
                                password != confirmPassword -> errorMessage = "Passwords do not match."
                                else -> {
                                    isLoading = true
                                    errorMessage = null
                                    coroutineScope.launch {
                                        val result = repository.register(fullName.trim(), email.trim(), password, yob)
                                        isLoading = false
                                        result.onSuccess {
                                            onRegisterSuccess(fullName, email)
                                        }.onFailure { err ->
                                            errorMessage = err.localizedMessage ?: "Registration failed"
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MedicalPrimary)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.PersonAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Account", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    // Back to Login
                    TextButton(
                        onClick = { navController.navigateUp() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MedicalPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Already have an account? Sign In", color = MedicalPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
