package com.jarvis.assistant

import android.content.Context
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import java.io.File
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.GeolocationPermissions
import org.json.JSONObject
import org.json.JSONArray
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

private class JarvisGlbExporter(context: Context, private val onSaved: (String) -> Unit) {
    private val appContext = context.applicationContext

    @JavascriptInterface
    fun saveGlb(encoded: String) {
        runCatching {
            require(encoded.length <= 70_000_000) { "Model je príliš veľký na export z tabletu" }
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            require(bytes.size >= 12 && bytes[0] == 0x67.toByte() && bytes[1] == 0x6C.toByte() &&
                bytes[2] == 0x54.toByte() && bytes[3] == 0x46.toByte()) { "Súbor nemá platnú GLB hlavičku" }
            val name = "JARVIS-model-${System.currentTimeMillis()}.glb"
            val savedPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "model/gltf-binary")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/JARVIS")
                }
                val uri = appContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Android nevytvoril súbor v priečinku Stiahnuté")
                appContext.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("Súbor sa nepodarilo zapísať")
                "Stiahnuté/JARVIS/$name"
            } else {
                val dir = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "JARVIS")
                if (!dir.exists()) dir.mkdirs()
                File(dir, name).apply { writeBytes(bytes) }.absolutePath
            }
            Handler(Looper.getMainLooper()).post { onSaved(savedPath) }
        }.onFailure {
            Handler(Looper.getMainLooper()).post { onSaved("Export zlyhal: ${it.message ?: "neznáma chyba"}") }
        }
    }
}

@Composable
fun ProjectWorkspace(title: String, description: String, onClose: () -> Unit, onRefine: (String) -> Unit, initialMapMode: Boolean = false, voiceCommand: String = "") {
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
    var mapMode by remember { mutableStateOf(initialMapMode || WorkspaceCommandProcessor.load(context).mapMode) }
    var sceneModel by remember { mutableStateOf(WorkspaceCommandProcessor.load(context).model) }
    var sceneEdits by remember { mutableStateOf(WorkspaceCommandProcessor.load(context).edits) }
    var sceneCommand by remember { mutableStateOf("") }
    var sceneEditCommand by remember { mutableStateOf("") }
    LaunchedEffect(voiceCommand) {
        if (voiceCommand.isNotBlank()) {
            val restored = WorkspaceCommandProcessor.process(context, voiceCommand)?.state ?: WorkspaceCommandProcessor.load(context)
            sceneModel = restored.model
            sceneEdits = restored.edits
            mapMode = restored.mapMode
        }
    }
    LaunchedEffect(description) {
        val restored = WorkspaceCommandProcessor.load(context)
        sceneModel = restored.model
        sceneEdits = restored.edits
        mapMode = restored.mapMode
    }
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
            Button(onClick = { mapMode = false; prefs.edit().putBoolean("workspace_map_mode", false).apply() }, colors = ButtonDefaults.buttonColors(containerColor = if (!mapMode) Color(0xFF12394B) else Color(0xFF0A1521)), border = androidx.compose.foundation.BorderStroke(1.dp, if (!mapMode) WorkCyan.copy(alpha = 0.7f) else Color(0xFF20394B)), shape = RoundedCornerShape(8.dp)) { Text("◈  3D SCÉNA", color = WorkCyan, letterSpacing = 1.sp) }
            Button(onClick = { mapMode = true; prefs.edit().putBoolean("workspace_map_mode", true).apply() }, colors = ButtonDefaults.buttonColors(containerColor = if (mapMode) Color(0xFF12394B) else Color(0xFF0A1521)), border = androidx.compose.foundation.BorderStroke(1.dp, if (mapMode) WorkCyan.copy(alpha = 0.7f) else Color(0xFF20394B)), shape = RoundedCornerShape(8.dp)) { Text("⌖  GEO PRIESTOR", color = WorkCyan, letterSpacing = 1.sp) }
        }
        if (mapMode) {
            NavigationProjection(voiceCommand)
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
                    val savedProject = WorkspaceCommandProcessor.process(context, command)?.state
                    if (savedProject != null) {
                        sceneModel = savedProject.model
                        sceneEdits = savedProject.edits
                        mapMode = savedProject.mapMode
                    }
                    sceneEditCommand = command
                    onRefine("Uprav aktívny projekt podľa tejto požiadavky: $command. Zachovaj existujúci kontext a opíš konkrétne zmeny. Ak požiadavka vyžaduje zmenu geometrie, uveď presné prvky, rozmery a umiestnenie.")
                    sceneCommand = ""
                }
            }, enabled = sceneCommand.isNotBlank()) { Text("UPRAVIŤ") }
        }
        ThreeDScene(model = sceneModel, edits = sceneEdits)
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
private fun NavigationProjection(voiceCommand: String = "") {
    val context = LocalContext.current
    var mapWebView by remember { mutableStateOf<WebView?>(null) }
    LaunchedEffect(voiceCommand, mapWebView) {
        if (voiceCommand.isNotBlank() && mapWebView != null) {
            mapWebView?.evaluateJavascript("handleVoiceCommand(${JSONObject.quote(voiceCommand)})", null)
        }
    }
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
 <button onclick="showGlobe()">ZEMEGUĽA</button>
 <button onclick="showStreets()">ULICE</button>
 <button onclick="toggle3D()">3D POHĽAD</button>
 <button onclick="locate()">MOJA POLOHA</button>
 <button onclick="routeMode()">TRASA</button>
 <button onclick="resetView()">RESET</button>
</div>
<script src="https://unpkg.com/maplibre-gl@4.7.1/dist/maplibre-gl.js"></script>
<script>
const statusEl=document.getElementById('status');
function status(s){statusEl.textContent=s}
const map=new maplibregl.Map({
 container:'map',
 style:'https://tiles.openfreemap.org/styles/dark',
 center:[10,20],zoom:1.7,pitch:0,bearing:0,
 antialias:true,attributionControl:true
});
map.addControl(new maplibregl.NavigationControl({visualizePitch:true}),'bottom-right');
map.addControl(new maplibregl.ScaleControl({unit:'metric'}),'bottom-left');
let marker=null,routeLayer=false,routeStart=null,threeD=true;
map.on('load',()=>{
 try{
  map.setProjection({type:'globe'});
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
  status('Holografická zemeguľa pripravená • vyhľadaj mesto alebo ulicu a priblížim sa na úroveň ulíc.');
 }catch(e){status('Mapa je načítaná; 3D budovy nie sú v tomto výreze dostupné: '+e.message)}
});
map.on('error',e=>{if(e&&e.error)status('Mapové dáta sa načítavajú; skontroluj internetové pripojenie.')});
function toggle3D(){
 threeD=!threeD;
 map.easeTo({pitch:threeD?60:0,bearing:threeD?-18:0,duration:900});
 status(threeD?'3D perspektíva aktívna':'2D mapa aktívna');
}
function handleVoiceCommand(raw){
 const q=(raw||'').trim();if(!q)return;
 const lower=q.toLocaleLowerCase('sk');
 if(lower.includes('zemeguľ')||lower.includes('zemegul')||lower.includes('globe mode')){showGlobe();return}
 if(lower.includes('späť na zem')||lower.includes('celú zem')){showGlobe();return}
 const streetMode=lower.includes('ulic')||lower.includes('street mode')||lower.includes('úroveň ulíc');
 const match=q.match(/(?:ukáž|zobraz|nájdi|vyhľadaj|priblíž(?: sa)? na|prejdi na)\s+(?:mi\s+)?(.+)/i);
 if(match&&match[1]&&!streetMode){document.getElementById('place').value=match[1].replace(/\s+(?:na map[euy]|na ulicu)$/i,'').trim();showStreets();return}
 if(streetMode){
  const streetPlace=q.match(/ulic(?:u|e)?\\s+(?:v\\s+)?(.+)/i);
  if(streetPlace&&streetPlace[1])document.getElementById('place').value=streetPlace[1].trim();
  showStreets();return
 }
 status('Hlasový príkaz mapy: '+q);
}
function showGlobe(){
 map.setProjection({type:'globe'});
 map.flyTo({center:[10,20],zoom:1.7,pitch:0,bearing:0,duration:1600});
 status('GLOBE MODE • celá Zem • vyhľadaj ľubovoľné mesto alebo ulicu.');
}
function showStreets(){
 const q=document.getElementById('place').value.trim();
 if(!q){status('Najprv zadaj mesto, adresu alebo ulicu do vyhľadávania.');document.getElementById('place').focus();return}
 map.setProjection({type:'mercator'});
 searchPlace();
}
function resetView(){showGlobe()}
async function geocode(q){
 const u='https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q='+encodeURIComponent(q);
 const r=await fetch(u,{headers:{'Accept-Language':'sk'}});
 if(!r.ok)throw Error('Vyhľadávanie momentálne neodpovedá');
 const a=await r.json();if(!a.length)throw Error('Miesto sa nenašlo');
 return {center:[Number(a[0].lon),Number(a[0].lat)],name:a[0].display_name};
}
async function searchPlace(){
 const q=document.getElementById('place').value.trim();if(!q)return;
 map.setProjection({type:'mercator'});
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
                mapWebView = this
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


/**
 * Real WebGL/Three.js viewport. Geometry is rendered in a WebView and can be
 * rotated with touch gestures. The model selector is driven by voice/text commands
 * already routed through ProjectWorkspace.
 */
@Composable
private fun ThreeDScene(model: String, edits: List<String> = emptyList()) {
    val context = LocalContext.current
    var exportStatus by remember { mutableStateOf("Model sa ukladá lokálne v projekte.") }
    val html = remember {
        """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>
html,body,#v{margin:0;width:100%;height:100%;overflow:hidden;background:#07101a}
#hud{position:absolute;z-index:2;left:10px;top:8px;color:#4deBff;font:10px system-ui;letter-spacing:1.5px;text-shadow:0 0 8px #00dfff}
</style></head><body><div id="hud">JARVIS • CAD / POLYGONÁLNA TOPOLOGIA</div><div id="v"></div>
<div id="tools"><button onclick="toggleJarvisTopology()">VRCHOLY</button><button onclick="extrudeJarvisFace(.5)">EXTRUDOVAŤ STENU</button><button id="export" onclick="exportJarvisModel()">EXPORT GLB</button></div>
<style>#tools{position:absolute;z-index:3;right:8px;top:8px;display:flex;gap:5px}#tools button{border:1px solid #4deBff;border-radius:7px;background:#082334;color:#4deBff;padding:7px 8px;font-weight:700;font-size:9px;letter-spacing:.5px}#tools #export{position:static}</style>
<script type="module">
import * as THREE from 'https://unpkg.com/three@0.160.0/build/three.module.js';
import { OrbitControls } from 'https://unpkg.com/three@0.160.0/examples/jsm/controls/OrbitControls.js';
import { GLTFExporter } from 'https://unpkg.com/three@0.160.0/examples/jsm/exporters/GLTFExporter.js';
const host=document.getElementById('v');
const scene=new THREE.Scene();scene.background=new THREE.Color(0x07101a);
const camera=new THREE.PerspectiveCamera(42,host.clientWidth/host.clientHeight,.1,1000);camera.position.set(5,4,7);
const renderer=new THREE.WebGLRenderer({antialias:true,alpha:false});renderer.setPixelRatio(Math.min(devicePixelRatio,2));renderer.setSize(host.clientWidth,host.clientHeight);renderer.shadowMap.enabled=true;renderer.shadowMap.type=THREE.PCFSoftShadowMap;host.appendChild(renderer.domElement);
scene.add(new THREE.HemisphereLight(0x9beeff,0x102030,2.0));
const key=new THREE.DirectionalLight(0xffffff,3);key.position.set(4,8,5);key.castShadow=true;scene.add(key);
const rim=new THREE.PointLight(0x00dfff,18,20);rim.position.set(-4,2,-3);scene.add(rim);
const grid=new THREE.GridHelper(16,32,0x1b6078,0x123044);grid.position.y=-1.2;scene.add(grid);
const controls=new OrbitControls(camera,renderer.domElement);controls.enableDamping=true;controls.dampingFactor=.06;controls.target.set(0,0,0);
const material=new THREE.MeshStandardMaterial({color:0x1685a4,metalness:.68,roughness:.28});
const edgeMaterial=new THREE.LineBasicMaterial({color:0x65edff,transparent:true,opacity:.7});
let object=null;const appliedEdits=new Set();
window.applyJarvisEdit=function(raw){
 const q=(raw||'').toLocaleLowerCase('sk');
 if(!object||!q.trim()||appliedEdits.has(q))return;
 appliedEdits.add(q);
 const editGroup=new THREE.Group();object.add(editGroup);
 const metal=new THREE.MeshStandardMaterial({color:0x9cefff,metalness:.85,roughness:.18});
 const addMatch=q.match(/(?:pridaj|vytvor|vymodeluj|create|add)\s+(?:mi\s+)?(?:nový\s+|novy\s+)?(valec|cylinder|guľu|gulu|sphere|kužeľ|kuzel|cone|torus|prstenec|rovinu|plochu|plane|kváder|kvader|kocku|box|kapsulu|capsule|dvanásťsten|dvanaststen|osemsten|icosahedron|dodecahedron|tetrahedron|torusknot)/i);
 if(addMatch){
  const kind=addMatch[1].toLocaleLowerCase('sk');let geometry;
  if(kind.includes('valec')||kind.includes('cylinder'))geometry=new THREE.CylinderGeometry(.65,.65,1.8,48);
  else if(kind.includes('guľ')||kind.includes('gul')||kind==='sphere')geometry=new THREE.SphereGeometry(.9,48,32);
  else if(kind.includes('kuže')||kind.includes('kuzel')||kind==='cone')geometry=new THREE.ConeGeometry(.8,1.8,48);
  else if(kind==='torus'||kind.includes('prstenec'))geometry=new THREE.TorusGeometry(.75,.2,20,64);
  else if(kind==='torusknot')geometry=new THREE.TorusKnotGeometry(.7,.18,160,20);
  else if(kind.includes('kapsul')||kind==='capsule')geometry=new THREE.CapsuleGeometry(.55,1.1,8,24);
  else if(kind.includes('dvanásť')||kind.includes('dvanast')||kind==='dodecahedron')geometry=new THREE.DodecahedronGeometry(.95,0);
  else if(kind.includes('osemsten')||kind==='icosahedron')geometry=new THREE.IcosahedronGeometry(.95,0);
  else if(kind==='tetrahedron')geometry=new THREE.TetrahedronGeometry(1.1,0);
  else if(kind==='plane'||kind.includes('rovinu')||kind.includes('plochu'))geometry=new THREE.PlaneGeometry(2,2,12,12);
  else if(kind.includes('kváder')||kind.includes('kvader'))geometry=new THREE.BoxGeometry(2,1,1);
  else geometry=new THREE.BoxGeometry(1.5,1.5,1.5);
  const color=q.includes('červen')?0xb52c42:q.includes('modr')?0x176fc1:q.includes('zelen')?0x238b62:0x1685a4;
  const part=new THREE.Mesh(geometry,new THREE.MeshStandardMaterial({color,metalness:.55,roughness:.3}));
  part.position.y=.1;part.castShadow=true;part.receiveShadow=true;editGroup.add(part);addEdges(part);
 }
 if(q.includes('odstráň posledný')||q.includes('odstran posledny')||q.includes('vymaž posledný')||q.includes('vymaz posledny')){
  const last=object.children.filter(o=>o.isMesh||o.isGroup).slice(-1)[0];if(last){object.remove(last);last.traverse(o=>{if(o.geometry)o.geometry.dispose()});}
 }
 const scaleMatch=q.match(/(?:zväčši|zvacsi|zmenši|zmensi)(?:\s+objekt)?(?:\s+na|\s+o)?\s+(\d+(?:[.,]\d+)?)/);
 if(scaleMatch){const amount=Math.max(.1,Math.min(10,Number(scaleMatch[1].replace(',','.'))));const factor=(q.includes('zmenši')||q.includes('zmensi'))?1/amount:amount;object.scale.multiplyScalar(factor);}
 const moveMatch=q.match(/posuň\s+(?:objekt\s+)?(-?\d+(?:[.,]\d+)?)\s+(-?\d+(?:[.,]\d+)?)\s+(-?\d+(?:[.,]\d+)?)/);
 if(moveMatch)object.position.set(Number(moveMatch[1].replace(',','.')),Number(moveMatch[2].replace(',','.')),Number(moveMatch[3].replace(',','.')));
 const rotateMatch=q.match(/otoč(?:\s+objekt)?\s+(\d+(?:[.,]\d+)?)/);
 if(rotateMatch)object.rotation.y=THREE.MathUtils.degToRad(Number(rotateMatch[1].replace(',','.')));
 if(q.includes('červen')) object.traverse(o=>{if(o.isMesh&&o.material&&o.material.color&&o.material.color.getHex()===0x1685a4)o.material.color.set(0xb52c42);});
 if(q.includes('modr')) object.traverse(o=>{if(o.isMesh&&o.material&&o.material.color&&o.material.color.getHex()===0x1685a4)o.material.color.set(0x176fc1);});
 if(q.includes('spoiler')||q.includes('krídlo')||q.includes('kridlo')){
  const wing=new THREE.Mesh(new THREE.BoxGeometry(1.65,.09,.32),metal);wing.position.set(-1.12,.72,0);editGroup.add(wing);
  for(const z of [-.48,.48]){const support=new THREE.Mesh(new THREE.BoxGeometry(.08,.32,.08),metal);support.position.set(-1.12,.53,z);editGroup.add(support);}
 }
 if(q.includes('svetl')||q.includes('reflektor')||q.includes('led')){
  for(const z of [-.48,.48]){const lamp=new THREE.Mesh(new THREE.BoxGeometry(.12,.18,.32),new THREE.MeshStandardMaterial({color:0xd8fbff,emissive:0x54dfff,emissiveIntensity:2}));lamp.position.set(1.57,.02,z);editGroup.add(lamp);}
 }
 if(q.includes('disk')||q.includes('ráfik')||q.includes('rafik')) object.traverse(o=>{if(o.isMesh&&o.geometry&&o.geometry.type==='CylinderGeometry'){const rim=new THREE.Mesh(new THREE.TorusGeometry(.22,.045,10,28),metal);rim.rotation.y=Math.PI/2;rim.position.copy(o.position);editGroup.add(rim);}});
 document.getElementById('hud').textContent='JARVIS • VOICE EDIT • '+raw.slice(0,45);
};
function geometryFromTopology(vertices,faces){
 const positions=[],triangleFaces=[];faces.forEach((face,fi)=>{for(let i=1;i<face.length-1;i++){[face[0],face[i],face[i+1]].forEach(k=>{const v=vertices[k];positions.push(v[0],v[1],v[2]);});triangleFaces.push(fi);}});
 const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));g.computeVertexNormals();g.computeBoundingSphere();g.userData.triangleFaces=triangleFaces;return g;
}
let topologyMode=false,activeTopologyMesh=null,selectedVertex=-1,selectedFace=-1,draggingVertex=false;
const topologyHandles=new THREE.Group();scene.add(topologyHandles);const topoRay=new THREE.Raycaster(),topoNdc=new THREE.Vector2(),topoPlane=new THREE.Plane();
function refreshTopologyHandles(target){topologyHandles.clear();activeTopologyMesh=target||null;selectedVertex=-1;selectedFace=-1;if(!topologyMode||!activeTopologyMesh?.userData.topology)return;activeTopologyMesh.userData.topology.vertices.forEach((v,index)=>{const h=new THREE.Mesh(new THREE.SphereGeometry(.06,10,8),new THREE.MeshBasicMaterial({color:0x4deBff,depthTest:false}));h.position.set(...v);h.userData.vertexIndex=index;topologyHandles.add(h);});}
function updateTopologyMesh(target){const d=target.userData.topology;target.geometry.dispose();target.geometry=geometryFromTopology(d.vertices,d.faces);const old=target.children.find(c=>c.userData.topologyEdges);if(old){target.remove(old);old.geometry.dispose();}const line=new THREE.LineSegments(new THREE.EdgesGeometry(target.geometry),edgeMaterial);line.userData.topologyEdges=true;target.add(line);refreshTopologyHandles(target);}
function topoPointer(e){const r=renderer.domElement.getBoundingClientRect();topoNdc.set((e.clientX-r.left)/r.width*2-1,-((e.clientY-r.top)/r.height*2-1));topoRay.setFromCamera(topoNdc,camera);}
renderer.domElement.addEventListener('pointerdown',e=>{if(!topologyMode||!activeTopologyMesh)return;topoPointer(e);const h=topoRay.intersectObjects(topologyHandles.children)[0];if(h){selectedVertex=h.object.userData.vertexIndex;draggingVertex=true;topoPlane.setFromNormalAndCoplanarPoint(camera.getWorldDirection(new THREE.Vector3()),h.object.getWorldPosition(new THREE.Vector3()));renderer.domElement.setPointerCapture(e.pointerId);e.preventDefault();return;}const hit=topoRay.intersectObject(activeTopologyMesh)[0];if(hit&&hit.faceIndex!==undefined){selectedFace=activeTopologyMesh.geometry.userData.triangleFaces[hit.faceIndex]??-1;document.getElementById('hud').textContent='JARVIS • VYBRANÁ STENA '+(selectedFace+1);}});
renderer.domElement.addEventListener('pointermove',e=>{if(!draggingVertex||selectedVertex<0||!activeTopologyMesh)return;topoPointer(e);const point=new THREE.Vector3();if(!topoRay.ray.intersectPlane(topoPlane,point))return;const local=activeTopologyMesh.worldToLocal(point),d=activeTopologyMesh.userData.topology;d.vertices[selectedVertex]=[local.x,local.y,local.z];updateTopologyMesh(activeTopologyMesh);});
renderer.domElement.addEventListener('pointerup',()=>{draggingVertex=false;});
window.toggleJarvisTopology=function(){topologyMode=!topologyMode;if(!topologyMode){topologyHandles.clear();activeTopologyMesh=null;}else refreshTopologyHandles(object?.children.find(c=>c.isMesh&&c.userData.topology)||null);document.getElementById('hud').textContent=topologyMode?'JARVIS • VRCHOLY: POTIAHNITE BOD':'JARVIS • CAD / TOPOLOGY';};
window.extrudeJarvisFace=function(distance){if(!activeTopologyMesh||selectedFace<0){document.getElementById('hud').textContent='JARVIS • VYBERTE STENU';return;}const d=activeTopologyMesh.userData.topology,face=d.faces[selectedFace];if(!face)return;const a=new THREE.Vector3(...d.vertices[face[0]]),b=new THREE.Vector3(...d.vertices[face[1]]),c=new THREE.Vector3(...d.vertices[face[2]]);const offset=new THREE.Vector3().subVectors(b,a).cross(new THREE.Vector3().subVectors(c,a)).normalize().multiplyScalar(THREE.MathUtils.clamp(Number(distance)||.5,-5,5));const top=face.map(i=>{const v=new THREE.Vector3(...d.vertices[i]).add(offset);d.vertices.push([v.x,v.y,v.z]);return d.vertices.length-1;});d.faces[selectedFace]=top.slice().reverse();d.faces.push(top);for(let i=0;i<face.length;i++){const j=(i+1)%face.length;d.faces.push([face[i],face[j],top[j],top[i]]);}selectedFace=-1;updateTopologyMesh(activeTopologyMesh);document.getElementById('hud').textContent='JARVIS • EXTRÚZIA HOTOVÁ';};
function addEdges(mesh){const edges=new THREE.EdgesGeometry(mesh.geometry);const line=new THREE.LineSegments(edges,edgeMaterial);if(mesh.userData.topology)line.userData.topologyEdges=true;mesh.add(line);}
function build(name){
 appliedEdits.clear();
 if(object){scene.remove(object);object.traverse(o=>{if(o.geometry)o.geometry.dispose()});}
 object=new THREE.Group();scene.add(object);
 let mesh;
 if(name==='GUĽA') mesh=new THREE.Mesh(new THREE.SphereGeometry(1.35,48,32),material.clone());
 else if(name==='PYRAMÍDA') mesh=new THREE.Mesh(new THREE.ConeGeometry(1.5,2.4,4),material.clone());
 else if(name==='DOM'){
  const body=new THREE.Mesh(new THREE.BoxGeometry(2.4,1.7,2),material.clone());body.position.y=-.25;object.add(body);addEdges(body);
  const roof=new THREE.Mesh(new THREE.ConeGeometry(1.8,1.25,4),material.clone());roof.rotation.y=Math.PI/4;roof.position.y=1.15;object.add(roof);addEdges(roof);
  const door=new THREE.Mesh(new THREE.BoxGeometry(.42,.95,.06),new THREE.MeshStandardMaterial({color:0x062637,metalness:.4}));door.position.set(0,-.62,1.04);object.add(door);
 } else if(name==='AUTO'){
  const body=new THREE.Mesh(new THREE.BoxGeometry(3.1,.65,1.35),material.clone());body.position.y=-.15;object.add(body);addEdges(body);
  const cabin=new THREE.Mesh(new THREE.BoxGeometry(1.65,.72,1.15),material.clone());cabin.position.set(-.15,.48,0);object.add(cabin);addEdges(cabin);
  for(const x of [-1,1])for(const z of [-.72,.72]){const wheel=new THREE.Mesh(new THREE.CylinderGeometry(.38,.38,.22,32),new THREE.MeshStandardMaterial({color:0x101820,metalness:.2,roughness:.8}));wheel.rotation.x=Math.PI/2;wheel.position.set(x,-.48,z);object.add(wheel);}
 } else if(name==='ULICA'){
  const road=new THREE.Mesh(new THREE.BoxGeometry(5,.12,1.8),new THREE.MeshStandardMaterial({color:0x263b4a,roughness:.9}));road.position.y=-.7;object.add(road);
  for(let i=0;i<4;i++){const b=new THREE.Mesh(new THREE.BoxGeometry(.85,1.2+i*.35,.85),material.clone());b.position.set(-1.65+i*1.1,.0+(i%2)*.25,-.85);object.add(b);addEdges(b);}
  for(let i=0;i<5;i++){const stripe=new THREE.Mesh(new THREE.BoxGeometry(.55,.015,.08),new THREE.MeshBasicMaterial({color:0x4deBff}));stripe.position.set(-1.5+i*.72,-.625,0);object.add(stripe);}
 } else if(name==='IZBA'){
  const floor=new THREE.Mesh(new THREE.BoxGeometry(3.8,.12,2.8),material.clone());floor.position.y=-.9;object.add(floor);addEdges(floor);
  const bed=new THREE.Mesh(new THREE.BoxGeometry(1.7,.35,1.1),material.clone());bed.position.set(-.65,-.62,-.35);object.add(bed);addEdges(bed);
  const desk=new THREE.Mesh(new THREE.BoxGeometry(1.0,.12,.65),material.clone());desk.position.set(1,-.15,-.7);object.add(desk);addEdges(desk);
 } else if(name==='CUSTOM'){ /* Start with an empty editable scene. */ }
 else mesh=new THREE.Mesh(new THREE.BoxGeometry(2.2,2.2,2.2),material.clone());
 if(name!=='DOM'&&name!=='AUTO'&&name!=='ULICA'&&name!=='IZBA'&&name!=='CUSTOM'){object.add(mesh);addEdges(mesh);}
 document.getElementById('hud').textContent='JARVIS • '+name+' • WEBGL SCENE';
}
window.setJarvisModel=build;
window.applyJarvisSceneSpec=function(spec){
 (spec.objects||[]).forEach(def=>{
  let geometry;
  switch(def.type){
   case 'mesh':geometry=geometryFromTopology(def.vertices||[],def.faces||[]);break;
   case 'box':{const d=def.dimensions||[1.4,1.4,1.4];geometry=new THREE.BoxGeometry(Math.max(.01,d[0]),Math.max(.01,d[1]),Math.max(.01,d[2]));break;}
   case 'sphere':geometry=new THREE.SphereGeometry(.8,40,28);break;
   case 'cylinder':geometry=new THREE.CylinderGeometry(.65,.65,1.6,40);break;
   case 'cone':geometry=new THREE.ConeGeometry(.8,1.6,40);break;
   case 'torus':geometry=new THREE.TorusGeometry(.7,.18,18,56);break;
   case 'plane':geometry=new THREE.PlaneGeometry(1.8,1.8,10,10);break;
   case 'capsule':geometry=new THREE.CapsuleGeometry(.45,1,8,20);break;
   case 'dodecahedron':geometry=new THREE.DodecahedronGeometry(.85,0);break;
   case 'icosahedron':geometry=new THREE.IcosahedronGeometry(.85,0);break;
   case 'tetrahedron':geometry=new THREE.TetrahedronGeometry(1,0);break;
   case 'torusKnot':geometry=new THREE.TorusKnotGeometry(.6,.15,120,16);break;
   default:geometry=new THREE.BoxGeometry(1.4,1.4,1.4);
  }
  const color=new THREE.Color(def.color||'#1685a4');
  const mesh=new THREE.Mesh(geometry,new THREE.MeshStandardMaterial({color,metalness:.5,roughness:.32,side:THREE.DoubleSide}));
  mesh.name=def.name||'JARVIS object';
  if(def.type==='mesh')mesh.userData.topology={vertices:(def.vertices||[]).map(v=>[Number(v[0]),Number(v[1]),Number(v[2])]),faces:(def.faces||[]).map(f=>f.map(Number))};
  const p=def.position||[0,0,0],r=def.rotation||[0,0,0],s=def.scale||[1,1,1];
  mesh.position.set(Number(p[0])||0,Number(p[1])||0,Number(p[2])||0);
  mesh.rotation.set(Number(r[0])||0,Number(r[1])||0,Number(r[2])||0);
  mesh.scale.set(Math.max(.05,Math.abs(Number(s[0])||1)),Math.max(.05,Math.abs(Number(s[1])||1)),Math.max(.05,Math.abs(Number(s[2])||1)));
  mesh.castShadow=true;mesh.receiveShadow=true;object.add(mesh);addEdges(mesh);
 });
 document.getElementById('hud').textContent='JARVIS • AI SCENE • '+(spec.objects||[]).length+' OBJECTS';
};
window.applyJarvisEdits=function(items){
 (items||[]).forEach(item=>{
  try{const spec=JSON.parse(item);if(spec&&Array.isArray(spec.objects)){window.applyJarvisSceneSpec(spec);return;}}catch(e){}
  window.applyJarvisEdit(item);
 });
};
window.exportJarvisModel=function(){
 if(!object){document.getElementById('hud').textContent='JARVIS • NIČ NA EXPORT';return;}
 const exporter=new GLTFExporter();
 exporter.parse(object,result=>{
  try{
   const bytes=new Uint8Array(result);let binary='';const chunk=0x8000;
   for(let i=0;i<bytes.length;i+=chunk)binary+=String.fromCharCode.apply(null,bytes.subarray(i,Math.min(i+chunk,bytes.length)));
   const encoded=btoa(binary);
   if(window.JarvisExport&&window.JarvisExport.saveGlb){window.JarvisExport.saveGlb(encoded);document.getElementById('hud').textContent='JARVIS • GLB EXPORT ODOSLANÝ';}
   else document.getElementById('hud').textContent='JARVIS • EXPORT BRIDGE NEDOSTUPNÝ';
  }catch(e){document.getElementById('hud').textContent='JARVIS • EXPORT CHYBA: '+e.message;}
 },error=>{document.getElementById('hud').textContent='JARVIS • EXPORT CHYBA: '+error;},{binary:true,onlyVisible:true});
};
build('KOCKA');
function tick(){requestAnimationFrame(tick);if(object)object.rotation.y+=.002;controls.update();renderer.render(scene,camera)}tick();
new ResizeObserver(()=>{const w=host.clientWidth,h=host.clientHeight;camera.aspect=w/h;camera.updateProjectionMatrix();renderer.setSize(w,h)}).observe(host);
</script></body></html>
        """.trimIndent()
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(exportStatus, color = Color(0xFF9BAFC2), fontSize = 10.sp, modifier = Modifier.weight(1f))
            Text("EXPORT: GLB", color = WorkCyan, fontSize = 10.sp)
        }
        AndroidView(
        modifier = Modifier.fillMaxWidth().height(300.dp)
            .background(Color(0xFF07101A), RoundedCornerShape(12.dp)),
        factory = { ctx ->
            WebView(ctx).apply {
                addJavascriptInterface(JarvisGlbExporter(context) { exportStatus = it }, "JarvisExport")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript("if(window.setJarvisModel)window.setJarvisModel(${JSONObject.quote(model)});", null)
                        val editsJson = JSONArray(edits).toString()
                        view.evaluateJavascript("if(window.applyJarvisEdits)window.applyJarvisEdits($editsJson);", null)
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setBackgroundColor(android.graphics.Color.rgb(7,16,26))
                loadDataWithBaseURL("https://jarvis.local/", html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.evaluateJavascript("if(window.setJarvisModel)window.setJarvisModel(${JSONObject.quote(model)});", null)
            val editsJson = JSONArray(edits).toString()
            webView.evaluateJavascript("if(window.applyJarvisEdits)window.applyJarvisEdits($editsJson);", null)
        }
    )
    }
}
