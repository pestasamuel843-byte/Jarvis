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
import androidx.compose.ui.unit.sp
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
    val redoStrokes = remember { mutableStateListOf<List<Offset>>() }
    var mapMode by remember { mutableStateOf(initialMapMode) }
    var sceneModel by remember { mutableStateOf("AUTO") }
    var sceneCommand by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF050A11), RoundedCornerShape(20.dp)).padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("J.A.R.V.I.S.  /  DESIGN SYSTEM  /  LIVE WORKSPACE", color = Color(0xFF4DEBFF), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.6.sp)
                Text(title.ifBlank { "NOVÝ PROJEKT" }.uppercase(), color = Color.White, style = MaterialTheme.typography.titleLarge, letterSpacing = 1.1.sp)
                Text("SCÉNA AKTÍVNA  •  UPRAVITEĽNÝ NÁHĽAD", color = Color(0xFF7894A8), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = {
                if (strokes.isNotEmpty()) {
                    redoStrokes.add(strokes.removeAt(strokes.lastIndex))
                    val saved = strokes.joinToString(";") { stroke -> stroke.joinToString("|") { point -> "${point.x},${point.y}" } }
                    prefs.edit().putString("strokes", saved).apply()
                }
            }, enabled = strokes.isNotEmpty()) { Text("Späť", color = WorkCyan) }
            TextButton(onClick = {
                if (redoStrokes.isNotEmpty()) {
                    strokes.add(redoStrokes.removeAt(redoStrokes.lastIndex))
                    val saved = strokes.joinToString(";") { stroke -> stroke.joinToString("|") { point -> "${point.x},${point.y}" } }
                    prefs.edit().putString("strokes", saved).apply()
                }
            }, enabled = redoStrokes.isNotEmpty()) { Text("Znova", color = WorkCyan) }
            TextButton(onClick = {
                strokes.clear(); redoStrokes.clear(); activeStroke = emptyList()
                prefs.edit().remove("strokes").apply()
            }) { Text("Vymazať", color = WorkCyan) }
            TextButton(onClick = onClose) { Text("Zavrieť", color = WorkCyan) }
        }
        Spacer(Modifier.height(8.dp))
        Surface(color = Color(0xFF0A1521), shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1D4053))) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Text("SYSTEM ANALYSIS  /  NÁVRH OD JARVIS", color = WorkCyan, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(4.dp))
                Text(description, color = Color(0xFFE2EDF7), style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { mapMode = false }, colors = ButtonDefaults.buttonColors(containerColor = if (!mapMode) Color(0xFF12394B) else Color(0xFF0A1521)), border = androidx.compose.foundation.BorderStroke(1.dp, if (!mapMode) WorkCyan.copy(alpha = 0.7f) else Color(0xFF20394B)), shape = RoundedCornerShape(8.dp)) { Text("◈  3D SCÉNA", color = WorkCyan, letterSpacing = 1.sp) }
            Button(onClick = { mapMode = true }, colors = ButtonDefaults.buttonColors(containerColor = if (mapMode) Color(0xFF12394B) else Color(0xFF0A1521)), border = androidx.compose.foundation.BorderStroke(1.dp, if (mapMode) WorkCyan.copy(alpha = 0.7f) else Color(0xFF20394B)), shape = RoundedCornerShape(8.dp)) { Text("⌖  GEO PRIESTOR", color = WorkCyan, letterSpacing = 1.sp) }
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
        Text("VIEWPORT 01  /  INTERAKTÍVNA GEOMETRIA  /  POTIAHNI PRE OTOČENIE", color = WorkCyan, style = MaterialTheme.typography.labelSmall, letterSpacing = 0.7.sp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = sceneCommand,
                onValueChange = { sceneCommand = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Povedz, čo mám zmeniť…", color = Color(0xFF7894A8)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = WorkCyan,
                    unfocusedBorderColor = Color(0xFF294454),
                    cursorColor = WorkCyan
                )
            )
            Button(onClick = {
                val command = sceneCommand.trim()
                if (command.isNotBlank()) {
                    val q = command.lowercase()
                    sceneModel = when {
                        listOf("auto", "auto", "vozid", "športiak", "sportiak", "spoiler", "koles").any { q.contains(it) } -> "AUTO"
                        listOf("izb", "posteľ", "postel", "nábytok", "nabytok", "okno", "dvere").any { q.contains(it) } -> "IZBA"
                        listOf("ulic", "cest", "chodník", "chodnik", "križovat", "krizovat", "budov").any { q.contains(it) } -> "ULICA"
                        listOf("dom", "strech", "komín", "komin").any { q.contains(it) } -> "DOM"
                        listOf("guľ", "gul", "lopta", "planéta", "planeta").any { q.contains(it) } -> "GUĽA"
                        q.contains("pyram") -> "PYRAMÍDA"
                        q.contains("kock") || q.contains("kocka") -> "KOCKA"
                        else -> sceneModel
                    }
                    onRefine("Uprav aktívny projekt podľa tejto požiadavky: $command. Zachovaj existujúci kontext a opíš konkrétne zmeny. Ak požiadavka vyžaduje zmenu geometrie, uveď presné prvky, rozmery a umiestnenie.")
                    sceneCommand = ""
                }
            }, enabled = sceneCommand.isNotBlank()) { Text("UPRAVIŤ") }
        }
        WireframeScene(model = sceneModel, onModelChange = { sceneModel = it })
        Spacer(Modifier.height(12.dp))
        Text("SKETCH LAYER  /  2D NÁČRT • KRESLI PRSTOM", color = Color(0xFF9BAFC2), style = MaterialTheme.typography.labelSmall, letterSpacing = 0.7.sp)
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().height(230.dp)
                .background(Color(0xFF07101A), RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { activeStroke = listOf(it) },
                        onDrag = { change, _ -> activeStroke = activeStroke + change.position },
                        onDragEnd = {
                            if (activeStroke.isNotEmpty()) {
                                strokes.add(activeStroke)
                                redoStrokes.clear()
                            }
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
            val gridColor = Color(0xFF102A3A)
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
        Text("LOCAL PROJECT CACHE  •  NÁČRT SA UKLADÁ V TABLETE", color = Color(0xFF8195A8), style = MaterialTheme.typography.labelSmall, letterSpacing = 0.5.sp)
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
<!doctype html><html lang="sk"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<link href="https://unpkg.com/maplibre-gl@4.7.1/dist/maplibre-gl.css" rel="stylesheet">
<style>
*{box-sizing:border-box}html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#050b13;color:#e8faff;font:14px system-ui}
#map{position:absolute;inset:0}
.hud{position:absolute;z-index:5;left:12px;right:12px;top:12px;padding:12px;border:1px solid #28718a;border-radius:14px;background:linear-gradient(135deg,rgba(5,24,38,.96),rgba(7,13,24,.84));box-shadow:0 8px 32px #0008;backdrop-filter:blur(12px)}
.brand{font-size:10px;letter-spacing:2.4px;color:#4deBff;font-weight:700;margin-bottom:8px}
.controls{display:flex;gap:7px}
input,button{min-width:0;border:1px solid #28566b;border-radius:8px;background:#081b2a;color:#f1fcff;padding:10px;font-size:13px}
input{flex:1;width:100%}button{color:#4deBff;font-weight:700}
button:active{background:#17455b}
.status{font-size:11px;color:#a6c4d2;margin-top:8px;line-height:1.35}
.bottom{position:absolute;z-index:5;bottom:12px;left:12px;right:12px;display:flex;gap:7px;flex-wrap:wrap}
.bottom button{background:rgba(5,22,35,.92);backdrop-filter:blur(8px)}
.tag{position:absolute;z-index:4;right:14px;top:150px;color:#4deBff;font-size:9px;letter-spacing:2px;text-shadow:0 0 10px #00dfff}
.maplibregl-ctrl-group{background:#081b2a!important;border:1px solid #286078!important}
.maplibregl-ctrl button{background-color:transparent!important}
.maplibregl-ctrl button span{filter:invert(1) sepia(1) saturate(5) hue-rotate(145deg)}
</style></head><body>
<div id="map"></div>
<div class="hud">
 <div class="brand">J.A.R.V.I.S. / 3D GEOSPATIAL INTERFACE</div>
 <div class="controls"><input id="place" value="Svit, Slovensko" placeholder="Mesto, adresa alebo budova"><button onclick="searchPlace()">HĽADAŤ</button></div>
 <div class="status" id="status">Načítavam 3D mapu sveta a mestskú geometriu…</div>
</div>
<div class="tag">LIVE 3D CITY • VECTOR MAP</div>
<div class="bottom">
 <button onclick="toggle3D()">3D POHĽAD</button>
 <button onclick="locate()">MOJA POLOHA</button>
 <button onclick="routeMode()">TRASA</button>
 <button onclick="resetView()">RESET POHĽADU</button>
</div>
<script src="https://unpkg.com/maplibre-gl@4.7.1/dist/maplibre-gl.js"></script>
<script>
const statusEl=document.getElementById('status');
function status(s){statusEl.textContent=s}
const map=new maplibregl.Map({
 container:'map',
 style:'https://tiles.openfreemap.org/styles/dark',
 center:[20.206,49.060],zoom:13.6,pitch:58,bearing:-18,
 antialias:true,attributionControl:true
});
map.addControl(new maplibregl.NavigationControl({visualizePitch:true}),'bottom-right');
map.addControl(new maplibregl.ScaleControl({unit:'metric'}),'bottom-left');
let marker=null,routeLayer=false,routeStart=null,threeD=true;
map.on('load',()=>{
 try{
  const layers=map.getStyle().layers||[];
  const label=layers.find(l=>l.type==='symbol'&&l.layout&&l.layout['text-field']);
  if(!map.getSource('openfreemap')) map.addSource('openfreemap',{type:'vector',url:'https://tiles.openfreemap.org/planet'});
  map.addLayer({
   id:'jarvis-3d-buildings',type:'fill-extrusion',source:'openfreemap','source-layer':'building',
   filter:['!=',['get','hide_3d'],true],minzoom:13,
   paint:{
    'fill-extrusion-color':['interpolate',['linear'],['coalesce',['get','render_height'],['get','height'],8],0,'#15394d',30,'#17617a',90,'#2795ad',180,'#63d8e8'],
    'fill-extrusion-height':['interpolate',['linear'],['zoom'],13,0,15,['coalesce',['get','render_height'],['get','height'],8]],
    'fill-extrusion-base':['coalesce',['get','render_min_height'],['get','min_height'],0],
    'fill-extrusion-opacity':0.88
   }
  },label?label.id:undefined);
  status('3D mapa pripravená • naklonené mestá a extrudované budovy • potiahnutím otáčaj kameru');
 }catch(e){status('Mapa je načítaná; 3D budovy nie sú v tomto výreze dostupné: '+e.message)}
});
map.on('error',e=>{if(e&&e.error)status('Mapové dáta sa načítavajú; skontroluj internetové pripojenie.')});
function toggle3D(){
 threeD=!threeD;
 map.easeTo({pitch:threeD?60:0,bearing:threeD?-18:0,duration:900});
 status(threeD?'3D perspektíva aktívna':'2D mapa aktívna');
}
function resetView(){map.flyTo({center:[20.206,49.060],zoom:13.6,pitch:58,bearing:-18,duration:1200})}
async function geocode(q){
 const u='https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q='+encodeURIComponent(q);
 const r=await fetch(u,{headers:{'Accept-Language':'sk'}});
 if(!r.ok)throw Error('Vyhľadávanie momentálne neodpovedá');
 const a=await r.json();if(!a.length)throw Error('Miesto sa nenašlo');
 return {center:[Number(a[0].lon),Number(a[0].lat)],name:a[0].display_name};
}
async function searchPlace(){
 const q=document.getElementById('place').value.trim();if(!q)return;
 status('Vyhľadávam: '+q);
 try{
  const p=await geocode(q);map.flyTo({center:p.center,zoom:16,pitch:62,bearing:-20,duration:1400});
  if(marker)marker.remove();marker=new maplibregl.Marker({color:'#4deBff'}).setLngLat(p.center).setPopup(new maplibregl.Popup().setText(p.name)).addTo(map);
  status('Zobrazené: '+p.name);
 }catch(e){status(e.message)}
}
let choosingRoute=false;
function routeMode(){choosingRoute=true;routeStart=null;status('Ťukni na mapu na začiatok trasy a potom na cieľ.')}
map.on('click',async e=>{
 if(!choosingRoute)return;
 const point=[e.lngLat.lng,e.lngLat.lat];
 if(!routeStart){routeStart=point;status('Začiatok uložený. Teraz vyber cieľ.');return}
 choosingRoute=false;const start=routeStart,end=point;routeStart=null;
 status('Vypočítavam trasu…');
 try{
  const url='https://router.project-osrm.org/route/v1/driving/'+start.join(',')+';'+end.join(',')+'?overview=full&geometries=geojson';
  const r=await fetch(url);const d=await r.json();if(d.code!=='Ok')throw Error('Trasu sa nepodarilo vypočítať');
  if(map.getSource('jarvis-route')){map.getSource('jarvis-route').setData(d.routes[0].geometry)}
  else{
   map.addSource('jarvis-route',{type:'geojson',data:d.routes[0].geometry});
   map.addLayer({id:'jarvis-route-line',type:'line',source:'jarvis-route',layout:{'line-cap':'round','line-join':'round'},paint:{'line-color':'#4deBff','line-width':6,'line-opacity':.95}});
  }
  const km=(d.routes[0].distance/1000).toFixed(1),mins=Math.round(d.routes[0].duration/60);
  status('TRASA VYKRESLENÁ • '+km+' km • približne '+mins+' min autom. Odhad bez živých dopravných dát.');
 }catch(e){status(e.message)}
});
function locate(){
 if(!navigator.geolocation){status('Poloha nie je dostupná.');return}
 status('Zisťujem polohu…');
 navigator.geolocation.getCurrentPosition(p=>{
  const c=[p.coords.longitude,p.coords.latitude];map.flyTo({center:c,zoom:16,pitch:62,bearing:-20});
  if(marker)marker.remove();marker=new maplibregl.Marker({color:'#4deBff'}).setLngLat(c).setPopup(new maplibregl.Popup().setText('Vaša poloha')).addTo(map);
  status('Mapa vycentrovaná na vašu aktuálnu polohu.');
 },()=>status('Povoľ prístup k polohe v Androide.'),{enableHighAccuracy:true,timeout:12000});
}
</script></body></html>
    """.trimIndent()
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(520.dp),
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
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setBackgroundColor(android.graphics.Color.rgb(5, 11, 19))
                loadDataWithBaseURL("https://jarvis.local/", html, "text/html", "UTF-8", null)
            }
        }
    )
}


@Composable
private fun WireframeScene(model: String, onModelChange: (String) -> Unit) {
    var rotation by remember { mutableFloatStateOf(0.45f) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf("KOCKA", "PYRAMÍDA", "DOM", "AUTO", "ULICA", "GUĽA").forEach { shape ->
                TextButton(onClick = { onModelChange(shape) }, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 2.dp)) {
                    Text(shape, color = if (model == shape) Color.White else WorkCyan, fontSize = 9.sp)
                }
            }
        }
        Canvas(
            Modifier.fillMaxWidth().height(240.dp)
                .background(Color(0xFF07101A), RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        rotation += drag.x * 0.01f
                    }
                }
        ) {
            val vertices = listOf(
                floatArrayOf(-1f,-1f,-1f), floatArrayOf(1f,-1f,-1f),
                floatArrayOf(1f,1f,-1f), floatArrayOf(-1f,1f,-1f),
                floatArrayOf(-1f,-1f,1f), floatArrayOf(1f,-1f,1f),
                floatArrayOf(1f,1f,1f), floatArrayOf(-1f,1f,1f)
            )
            val edges = listOf(0 to 1,1 to 2,2 to 3,3 to 0,4 to 5,5 to 6,6 to 7,7 to 4,0 to 4,1 to 5,2 to 6,3 to 7)
            val points = vertices.map { v ->
                val x = v[0] * cos(rotation) - v[2] * sin(rotation)
                val z = v[0] * sin(rotation) + v[2] * cos(rotation)
                Offset(size.width/2f + x*size.minDimension*.27f, size.height/2f + (v[1] + z*.22f)*size.minDimension*.27f)
            }
            var x = 0f
            while (x < size.width) { drawLine(Color(0xFF153246), Offset(x,0f), Offset(x,size.height), .7.dp.toPx()); x += 28.dp.toPx() }
            var y = 0f
            while (y < size.height) { drawLine(Color(0xFF153246), Offset(0f,y), Offset(size.width,y), .7.dp.toPx()); y += 28.dp.toPx() }
            edges.forEach { (a,b) -> drawLine(WorkCyan.copy(alpha=.9f), points[a], points[b], 2.dp.toPx()) }
            points.forEach { drawCircle(WorkCyan, 3.dp.toPx(), it) }
        }
        Text("MODEL: $model • POTIAHNI PRE OTOČENIE", color = Color(0xFF9BAFC2), fontSize = 9.sp)
    }
}
