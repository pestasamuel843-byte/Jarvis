package com.jarvis.assistant

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

private val WorkCyan = Color(0xFF4DEBFF)

@Composable
fun ProjectWorkspace(title: String, description: String, onClose: () -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF0C1420), RoundedCornerShape(18.dp)).padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("PRACOVNÝ PRIESTOR", color = WorkCyan, style = MaterialTheme.typography.labelSmall)
                Text(title.ifBlank { "Nový projekt" }, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = { strokes.clear(); activeStroke = emptyList() }) { Text("Vymazať", color = WorkCyan) }
            TextButton(onClick = onClose) { Text("Zavrieť", color = WorkCyan) }
        }
        Spacer(Modifier.height(8.dp))
        Surface(color = Color(0xFF111F2D), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Text("NÁVRH OD JARVIS", color = WorkCyan, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                Text(description, color = Color(0xFFE2EDF7), style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Náčrtová plocha • kresli prstom", color = Color(0xFF9BAFC2), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().height(190.dp)
                .background(Color(0xFF07101A), RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { activeStroke = listOf(it) },
                        onDrag = { change, _ -> activeStroke = activeStroke + change.position },
                        onDragEnd = { if (activeStroke.isNotEmpty()) strokes.add(activeStroke); activeStroke = emptyList() },
                        onDragCancel = { activeStroke = emptyList() }
                    )
                }
        ) {
            val gridColor = Color(0xFF183244)
            val step = 24.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = 0f
            while (y < size.height) { drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f); y += step }
            (strokes.toList() + listOf(activeStroke)).forEach { points ->
                if (points.size > 1) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, WorkCyan, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Náčrt sa zatiaľ ukladá iba počas otvorenia tejto obrazovky.", color = Color(0xFF8195A8), style = MaterialTheme.typography.labelSmall)
    }
}
