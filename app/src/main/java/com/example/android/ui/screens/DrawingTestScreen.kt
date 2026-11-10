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
import com.example.android.ui.theme.*
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
            // AI Spec Instruction Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MedicalSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = if (isSpiral) "Archimedean Spiral Protocol" else "Sinusoidal Wave Protocol",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MedicalPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isSpiral) {
                            "Draw a spiral from the center outward in one continuous motion filling the square canvas."
                        } else {
                            "Draw a smooth continuous wave from the left edge across to the right edge."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI SPEC RULE: Strict 1:1 Square Drawing Canvas (aspectRatio 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f) // STRICT SQUARE CANVAS
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(2.dp, MedicalOutline, RoundedCornerShape(16.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> currentLine = listOf(offset) },
                                onDrag = { change, _ -> currentLine = currentLine + change.position },
                                onDragEnd = {
                                    if (currentLine.isNotEmpty()) {
                                        lines.add(currentLine)
                                        currentLine = emptyList()
                                    }
                                }
                            )
                        }
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    // 1. Draw User's Strokes in solid medical ink
                    lines.forEach { line ->
                        if (line.size > 1) {
                            val path = Path().apply {
                                moveTo(line.first().x, line.first().y)
                                for (p in line) lineTo(p.x, p.y)
                            }
                            drawPath(
                                path = path,
                                color = Color.Black, // Clean high-contrast ink
                                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }

                    // 2. Active stroke currently being drawn
                    if (currentLine.size > 1) {
                        val activePath = Path().apply {
                            moveTo(currentLine.first().x, currentLine.first().y)
                            for (p in currentLine) lineTo(p.x, p.y)
                        }
                        drawPath(
                            path = activePath,
                            color = MedicalPrimary,
                            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        lines.clear()
                        testCompleted = false
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
                            // AI SPEC REQUIREMENT: Export clean 512x512 plain bitmap (pure white, black ink, no UI)
                            val exportedBytes = exportClean512Bitmap(lines)
                            testCompleted = true
                            onDrawingExported(task, exportedBytes)
                        }
                    },
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalPrimary),
                    enabled = lines.isNotEmpty()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Complete & Export")
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
                        Column {
                            Text(
                                text = "Square PNG Ready (512x512)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MedicalPrimary
                            )
                            Text(
                                text = "Formatted for $task AI Model",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
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
        // Find bounds to normalize to 512x512
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