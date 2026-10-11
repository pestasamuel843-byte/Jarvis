package com.jarvis.assistant

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.GeolocationPermissions
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.viewinterop.AndroidView
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
fun ProjectWorkspace(title: String, description: String, onClose: () -> Unit, onRefine: (String) -> Unit, initialMapMode: Boolean = false) {
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
    var mapMode by remember { mutableStateOf(initialMapMode) }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { mapMode = false }, colors = ButtonDefaults.buttonColors(containerColor = if (!mapMode) WorkCyan.copy(alpha = 0.18f) else Color(0xFF172638))) { Text("PROJEKT / 3D", color = WorkCyan) }
            Button(onClick = { mapMode = true }, colors = ButtonDefaults.buttonColors(containerColor = if (mapMode) WorkCyan.copy(alpha = 0.18f) else Color(0xFF172638))) { Text("HOLOGRAFICKÁ MAPA", color = WorkCyan) }
        }
        if (mapMode) {
            NavigationProjection()
        } else {
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
}

@Composable
private fun NavigationProjection() {
    val context = LocalContext.current
    var pendingGeoCallback by remember { mutableStateOf<GeolocationPermissions.Callback?>(null) }
    var pendingGeoOrigin by remember { mutableStateOf("") }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingGeoCallback?.invoke(pendingGeoOrigin, granted, false)
        pendingGeoCallback = null
    }
    val html = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<style>
*{box-sizing:border-box}body{margin:0;background:#07101a;color:#e7faff;font:14px Arial,sans-serif}
.top{padding:12px;background:linear-gradient(135deg,#102b40,#07101a);border:1px solid #21495d;border-radius:14px}
.brand{color:#4deBff;font-size:11px;letter-spacing:2px;font-weight:bold;margin-bottom:8px}
input,button{width:100%;padding:11px;margin:4px 0;border-radius:8px;border:1px solid #2b5368;background:#0b1a28;color:#fff;font-size:14px}
button{background:#123c50;color:#4deBff;font-weight:bold;border-color:#28748a}
#map{height:360px;margin-top:10px;border-radius:12px;border:1px solid #24556a;overflow:hidden}
.status{color:#9dbac9;font-size:12px;padding:8px 2px;line-height:1.4}
.leaflet-container{background:#07101a}
</style></head><body>
<div class="top"><div class="brand">J.A.R.V.I.S. / TACTICAL ROUTE DISPLAY</div>
<input id="from" placeholder="Odkiaľ? (adresa alebo miesto)">
<input id="to" placeholder="Kam? Napr. škola Komenského, mesto">
<button onclick="route()">VYPOČÍTAŤ A PREMietnuť TRASU</button>
<button onclick="locate()">POUŽIŤ MOJU POLOHU</button>
<div id="status" class="status">Skutočné mapové dáta OpenStreetMap. Zadajte začiatok a cieľ; pri nejednoznačnom názve doplňte mesto.</div></div>
<div id="map"></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
const map=L.map('map',{zoomControl:true}).setView([48.1486,17.1077],6);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors'}).addTo(map);
let routeLayers=[],markers=[];
function status(s){document.getElementById('status').textContent=s}
async function geocode(q){const u='https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q='+encodeURIComponent(q);const r=await fetch(u,{headers:{'Accept-Language':'sk'}});if(!r.ok)throw Error('Vyhľadanie miesta zlyhalo');const a=await r.json();if(!a.length)throw Error('Miesto sa nenašlo: '+q);return [Number(a[0].lon),Number(a[0].lat),a[0].display_name]}
async function route(){const a=document.getElementById('from').value.trim(),b=document.getElementById('to').value.trim();if(!a||!b){status('Vyplňte začiatok aj cieľ.');return}status('Vyhľadávam miesta a počítam trasu…');try{const [s,e]=await Promise.all([geocode(a),geocode(b)]);const u='https://router.project-osrm.org/route/v1/driving/'+s[0]+','+s[1]+';'+e[0]+','+e[1]+'?overview=full&geometries=geojson&alternatives=true&steps=true';const r=await fetch(u);if(!r.ok)throw Error('Služba trás momentálne neodpovedá');const d=await r.json();if(d.code!=='Ok'||!d.routes.length)throw Error('Trasu sa nepodarilo vypočítať');routeLayers.forEach(x=>map.removeLayer(x));markers.forEach(x=>map.removeLayer(x));routeLayers=[];markers=[];d.routes.forEach((rt,i)=>{const l=L.geoJSON(rt.geometry,{style:{color:i===0?'#4deBff':'#f4c95d',weight:i===0?6:4,opacity:i===0?0.95:0.65,dashArray:i===0?null:'8 8'}}).addTo(map);routeLayers.push(l)});markers.push(L.marker([s[1],s[0]]).addTo(map).bindPopup('Štart: '+s[2]));markers.push(L.marker([e[1],e[0]]).addTo(map).bindPopup('Cieľ: '+e[2]));map.fitBounds(routeLayers[0].getBounds(),{padding:[25,25]});const km=(d.routes[0].distance/1000).toFixed(1),mins=Math.round(d.routes[0].duration/60);status('TRASA VYKRESLENÁ • '+km+' km • približne '+mins+' min autom. Tyrkysová = odporúčaná trasa; žltá = alternatíva, ak je dostupná. Odhad OSRM, nie živá doprava. '+s[2]+' → '+e[2]);}catch(e){status('Nepodarilo sa: '+e.message+'. Skúste presnejšiu adresu alebo názov mesta.')}}
function locate(){if(!navigator.geolocation){status('Poloha nie je dostupná v tomto zobrazení. Zadajte adresu ručne.');return}status('Zisťujem polohu…');navigator.geolocation.getCurrentPosition(async p=>{try{const r=await fetch('https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat='+p.coords.latitude+'&lon='+p.coords.longitude);const d=await r.json();document.getElementById('from').value=d.display_name||p.coords.latitude+','+p.coords.longitude;map.setView([p.coords.latitude,p.coords.longitude],15);status('Začiatok nastavený podľa polohy zariadenia. Teraz zadajte cieľ.')}catch(e){status('Polohu sa nepodarilo preložiť na adresu. Zadajte ju ručne.')}},()=>status('Povolenie polohy nie je dostupné. Zadajte začiatok ručne.'),{enableHighAccuracy:false,timeout:10000})}
</script></body></html>
    """.trimIndent()
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(510.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                        val hasLocation = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasLocation) callback.invoke(origin, true, false) else {
                            pendingGeoOrigin = origin
                            pendingGeoCallback = callback
                            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setGeolocationEnabled(true)
                setBackgroundColor(android.graphics.Color.rgb(7, 16, 26))
                loadDataWithBaseURL("https://jarvis.local/", html, "text/html", "UTF-8", null)
            }
        },
        update = { }
    )
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
