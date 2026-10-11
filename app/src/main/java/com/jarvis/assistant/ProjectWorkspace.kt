package com.jarvis.assistant

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private val WorkCyan = Color(0xFF4DEBFF)

@Composable
fun ProjectWorkspace(title: String, description: String, onClose: () -> Unit, onRefine: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("jarvis_workspace", Context.MODE_PRIVATE) }
    val strokes = remember {
        mutableStateListOf<List<Offset>>().apply {
            prefs.getString("strokes", "").orEmpty().split(";").filter { it.isNotBlank() }.forEach { encoded ->
                val points = encoded.split("|").mapNotNull { pair ->
                    val xy = pair.split(",")
                    if (xy.size == 2) runCatching { Offset(xy[0].toFloat(), xy[1].toFloat()) }.getOrNull() else null
                }
                if (points.isNotEmpty()) add(points)
            }
        }
    }
    var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF0C1420), RoundedCornerShape(18.dp)).padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("PRACOVNÝ PRIESTOR", color = WorkCyan, style = MaterialTheme.typography.labelSmall)
                Text(title.ifBlank { "Nový projekt" }, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = {
                strokes.clear(); activeStroke = emptyList()
                prefs.edit().remove("strokes").apply()
            }) { Text("Vymazať", color = WorkCyan) }
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
        Spacer(Modifier.height(8.dp))
        Text("ČO NAVRHUJEME ĎALEJ?", color = WorkCyan, style = MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { onRefine("Navrhni 3 odlišné alternatívy aktuálneho návrhu, porovnaj ich výhody a odporuč jednu.") }) { Text("Alternatívy", color = WorkCyan) }
            TextButton(onClick = { onRefine("Vylepši aktuálny návrh a uveď konkrétne zmeny a dôvody.") }) { Text("Vylepšiť", color = WorkCyan) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { onRefine("Zjednoduš aktuálny návrh, zníž zložitosť a zachovaj najdôležitejšie funkcie.") }) { Text("Zjednodušiť", color = WorkCyan) }
            TextButton(onClick = { onRefine("Navrhni futuristickejšiu verziu aktuálneho návrhu s tmavým sci-fi vzhľadom a tyrkysovými prvkami.") }) { Text("Futuristický štýl", color = WorkCyan) }
        }
        Spacer(Modifier.height(10.dp))
        Text("INTERAKTÍVNY 3D NÁHĽAD • otáčaj prstom", color = WorkCyan, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(6.dp))
        WireframeCube()
        Spacer(Modifier.height(12.dp))
        Text("Náčrtová plocha • kresli prstom", color = Color(0xFF9BAFC2), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().height(190.dp)
                .background(Color(0xFF07101A), RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { activeStroke = listOf(it) },
                        onDrag = { change, _ -> activeStroke = activeStroke + change.position },
                        onDragEnd = {
                            if (activeStroke.isNotEmpty()) strokes.add(activeStroke)
                            activeStroke = emptyList()
                            val saved = strokes.joinToString(";") { stroke ->
                                stroke.joinToString("|") { point -> point.x.toString() + "," + point.y.toString() }
                            }
                            prefs.edit().putString("strokes", saved).apply()
                        },
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
        Text("Náčrt sa ukladá lokálne v tablete.", color = Color(0xFF8195A8), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun WireframeCube() {
    var rotationY by remember { mutableFloatStateOf(0.55f) }
    var rotationX by remember { mutableFloatStateOf(-0.35f) }
    Canvas(
        Modifier.fillMaxWidth().height(160.dp)
            .background(Color(0xFF07101A), RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    rotationY += drag.x * 0.012f
                    rotationX = (rotationX + drag.y * 0.012f).coerceIn(-1.4f, 1.4f)
                }
            }
    ) {
        val vertices = listOf(
            floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f),
            floatArrayOf(1f, 1f, -1f), floatArrayOf(-1f, 1f, -1f),
            floatArrayOf(-1f, -1f, 1f), floatArrayOf(1f, -1f, 1f),
            floatArrayOf(1f, 1f, 1f), floatArrayOf(-1f, 1f, 1f)
        )
        val projected = vertices.map { v ->
            val x1 = v[0] * cos(rotationY) - v[2] * sin(rotationY)
            val z1 = v[0] * sin(rotationY) + v[2] * cos(rotationY)
            val y2 = v[1] * cos(rotationX) - z1 * sin(rotationX)
            val z2 = v[1] * sin(rotationX) + z1 * cos(rotationX)
            val perspective = 1.0f / (2.8f - z2 * 0.25f)
            Offset(size.width / 2f + x1 * size.minDimension * 0.28f * perspective,
                size.height / 2f + y2 * size.minDimension * 0.28f * perspective)
        }
        val edges = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 0,
            4 to 5, 5 to 6, 6 to 7, 7 to 4,
            0 to 4, 1 to 5, 2 to 6, 3 to 7
        )
        val gridColor = Color(0xFF183244)
        var x = 0f
        while (x < size.width) { drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f); x += 28.dp.toPx() }
        var y = 0f
        while (y < size.height) { drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f); y += 28.dp.toPx() }
        edges.forEach { (a, b) ->
            drawLine(WorkCyan, projected[a], projected[b], strokeWidth = 2.dp.toPx())
        }
        projected.forEach { point ->
            drawCircle(WorkCyan, radius = 3.dp.toPx(), center = point)
        }
    }
}
