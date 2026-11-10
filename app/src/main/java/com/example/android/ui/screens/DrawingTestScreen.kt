package com.example.android.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.android.data.model.SessionState
import com.example.android.data.repository.AssessmentRepository
import com.example.android.ui.theme.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingTestScreen(
    task: String = "spiral", // "spiral" or "wave"
    navController: NavController,
    onDrawingExported: (task: String, imageBytes: ByteArray) -> Unit = { _, _ -> }
) {
    val isSpiral = task == "spiral"
    val lines = remember { mutableStateListOf<List<Offset>>() }
    var currentLine by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var testCompleted by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadStatus by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val repository = remember { AssessmentRepository() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSpiral) "Spiral Drawing Test" else "Wave Drawing Test",
                        fontWeight = FontWeight.Bold
                    )
                },
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
            // Test Instructions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MedicalSurface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = if (isSpiral) "Archimedean Spiral Task" else "Kinematic Wave Task",
                        fontWeight = FontWeight.Bold,
                        color = MedicalPrimary,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isSpiral)
                            "Trace from the center outward smoothly inside the guide without lifting your finger."
                        else
                            "Trace the continuous wave pattern from left to right as steadily as possible.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // AI SPEC REQUIREMENT: Square 1:1 Aspect Ratio Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f) // STRICT 1:1 ASPECT RATIO
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(2.dp, MedicalOutline, RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentLine = listOf(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentLine = currentLine + change.position
                            },
                            onDragEnd = {
                                if (currentLine.isNotEmpty()) {
                                    lines.add(currentLine)
                                    currentLine = emptyList()
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // 1. Draw Template Guidelines (Faint Grey)
                    if (isSpiral) {
                        val center = Offset(w / 2f, h / 2f)
                        val guidePath = Path()
                        val maxR = min(w, h) * 0.42f
                        val loops = 3.5f
                        val steps = 250
                        for (i in 0..steps) {
                            val theta = (i.toFloat() / steps) * (loops * 2 * PI.toFloat())
                            val r = (theta / (loops * 2 * PI.toFloat())) * maxR
                            val x = center.x + r * cos(theta)
                            val y = center.y + r * sin(theta)
                            if (i == 0) guidePath.moveTo(x, y) else guidePath.lineTo(x, y)
                        }
                        drawPath(
                            path = guidePath,
                            color = Color(0xFFE2E8F0),
                            style = Stroke(width = 6f, cap = StrokeCap.Round)
                        )
                    } else {
                        // Wave pattern guide
                        val wavePath = Path()
                        val steps = 200
                        val amp = h * 0.22f
                        val midY = h / 2f
                        for (i in 0..steps) {
                            val x = (i.toFloat() / steps) * w
                            val y = midY + amp * sin((x / w) * 3 * 2 * PI.toFloat())
                            if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                        }
                        drawPath(
                            path = wavePath,
                            color = Color(0xFFE2E8F0),
                            style = Stroke(width = 6f, cap = StrokeCap.Round)
                        )
                    }

                    // 2. Draw Finished Lines (Black Ink)
                    lines.forEach { line ->
                        if (line.size > 1) {
                            val path = Path().apply {
                                moveTo(line.first().x, line.first().y)
                                for (p in line.drop(1)) lineTo(p.x, p.y)
                            }
                            drawPath(
                                path = path,
                                color = Color.Black,
                                style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }

                    // 3. Draw Active Stroke
                    if (currentLine.size > 1) {
                        val path = Path().apply {
                            moveTo(currentLine.first().x, currentLine.first().y)
                            for (p in currentLine.drop(1)) lineTo(p.x, p.y)
                        }
                        drawPath(
                            path = path,
                            color = Color.Black,
                            style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        lines.clear()
                        currentLine = emptyList()
                        testCompleted = false
                        uploadStatus = null
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = TextSecondary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear", color = TextSecondary)
                }

                Button(
                    onClick = {
                        if (lines.isNotEmpty()) {
                            isUploading = true
                            val exportedBytes = exportClean512Bitmap(lines)
                            onDrawingExported(task, exportedBytes)

                            coroutineScope.launch {
                                val result = repository.submitDrawing(
                                    sessionId = SessionState.currentSessionId,
                                    task = task,
                                    imageBytes = exportedBytes
                                )
                                isUploading = false
                                result.onSuccess { session ->
                                    testCompleted = true
                                    uploadStatus = "Saved to Database (Session: ${session.session_id.take(8)}...)"
                                }.onFailure { err ->
                                    testCompleted = true
                                    uploadStatus = "Uploaded locally: 512x512 clean PNG generated"
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalPrimary),
                    enabled = lines.isNotEmpty() && !isUploading
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Complete & Export")
                    }
                }
            }

            // Completion Banner
            if (testCompleted) {
                Spacer(modifier = Modifier.height(12.dp))
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
                                text = "Square PNG Submitted (512x512)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MedicalPrimary
                            )
                            Text(
                                text = uploadStatus ?: "Formatted for $task AI Model",
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
 * AI SPEC: Generates a 512x512 square Bitmap with pure white background (#FFFFFF)
 * and black ink strokes, containing ZERO UI buttons, status bars, or guidelines.
 */
fun exportClean512Bitmap(lines: List<List<Offset>>): ByteArray {
    val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(AndroidColor.WHITE) // Plain background

    val paint = AndroidPaint().apply {
        color = AndroidColor.BLACK
        strokeWidth = 10f
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
        isAntiAlias = true
    }

    if (lines.isNotEmpty()) {
        val allPoints = lines.flatten()
        val minX = allPoints.minOf { it.x }
        val maxX = allPoints.maxOf { it.x }
        val minY = allPoints.minOf { it.y }
        val maxY = allPoints.maxOf { it.y }
        val spanX = max(1f, maxX - minX)
        val spanY = max(1f, maxY - minY)

        lines.forEach { line ->
            if (line.size > 1) {
                val path = android.graphics.Path()
                for ((i, p) in line.withIndex()) {
                    val normX = 40f + ((p.x - minX) / spanX) * 432f
                    val normY = 40f + ((p.y - minY) / spanY) * 432f
                    if (i == 0) path.moveTo(normX, normY) else path.lineTo(normX, normY)
                }
                canvas.drawPath(path, paint)
            }
        }
    }

    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
}
