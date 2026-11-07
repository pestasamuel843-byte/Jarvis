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
 <button onclick="toggle3D()">3D POHĽAD</button><button onclick="toggleGlobe()">GLOBUS</button><button onclick="toggleSatellite()">SATELIT</button>
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
 antialias:true,attributionControl:true,projection:{type:'globe'}
});
map.addControl(new maplibregl.NavigationControl({visualizePitch:true}),'bottom-right');
map.addControl(new maplibregl.ScaleControl({unit:'metric'}),'bottom-left');
let marker=null,routeLayer=false,routeStart=null,threeD=true;
map.on('load',()=>{
 try{
  map.addSource('jarvis-satellite',{type:'raster',tiles:['https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}'],tileSize:256,attribution:'Imagery © Esri, Maxar, Earthstar Geographics and GIS User Community'});
  map.addLayer({id:'jarvis-satellite-layer',type:'raster',source:'jarvis-satellite',layout:{visibility:'none'},paint:{'raster-opacity':1}});
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
function toggleGlobe(){const globe=map.getProjection().type!=='globe';map.setProjection({type:globe?'globe':'mercator'});status(globe?'Pohľad na zemeguľu aktivovaný':'Plochá projekcia mapy aktivovaná')}
function toggleSatellite(){const visible=map.getLayoutProperty('jarvis-satellite-layer','visibility')!=='visible';map.setLayoutProperty('jarvis-satellite-layer','visibility',visible?'visible':'none');status(visible?'Satelitná vrstva zapnutá • Esri imagery':'Vektorová mapa zapnutá')}
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
    val html = remember {
        """
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>
*{box-sizing:border-box}html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#07101a;color:#eafaff;font:13px system-ui}
#view{position:absolute;inset:0}#tools{position:absolute;z-index:3;left:8px;right:8px;top:8px;display:flex;gap:5px;flex-wrap:wrap}
button{background:#0b2434;border:1px solid #236079;color:#4deBff;border-radius:7px;padding:8px 10px;font-weight:650}
#status{position:absolute;z-index:3;bottom:8px;left:8px;padding:6px 9px;border-radius:7px;background:#06111ddd;color:#9cc4d2;font-size:10px}
</style></head><body><div id="view"></div>
<div id="tools">
<button onclick="setShape('KOCKA')">Kocka</button><button onclick="setShape('GUĽA')">Guľa</button><button onclick="setShape('PYRAMÍDA')">Kužeľ</button><button onclick="setShape('DOM')">Dom</button><button onclick="setShape('AUTO')">Auto</button>
<button onclick="addCube()">+ Objekt</button><button onclick="duplicateSelected()">Duplikovať</button><button onclick="removeSelected()">Odstrániť</button><button onclick="moveSelected(-.25,0,0)">X−</button><button onclick="moveSelected(.25,0,0)">X+</button><button onclick="moveSelected(0,.25,0)">Y+</button><button onclick="moveSelected(0,0,.25)">Z+</button><button onclick="rotateSelected()">↻ 15°</button><button onclick="scaleSelected(.1)">Zväčšiť</button><button onclick="scaleSelected(-.1)">Zmenšiť</button><button onclick="resetCamera()">Reset kamery</button><button onclick="saveScene()">Uložiť</button><button onclick="loadScene()">Načítať</button><button onclick="exportScene()">Export JSON</button>
</div><div id="status">3D VIEWPORT • potiahnutím otáčaj, dvoma prstami približuj</div>
<script type="importmap">{"imports":{"three":"https://unpkg.com/three@0.160.0/build/three.module.js","three/addons/":"https://unpkg.com/three@0.160.0/examples/jsm/"}}</script>
<script type="module">
import * as THREE from 'three';
import {OrbitControls} from 'three/addons/controls/OrbitControls.js';
const host=document.getElementById('view');
const scene=new THREE.Scene();scene.background=new THREE.Color(0x07101a);
scene.add(new THREE.HemisphereLight(0x9beeff,0x102033,2.0));
const key=new THREE.DirectionalLight(0x9beeff,2.6);key.position.set(5,8,6);scene.add(key);
const camera=new THREE.PerspectiveCamera(45,1,.1,1000);camera.position.set(5,4,7);
const renderer=new THREE.WebGLRenderer({antialias:true,alpha:false});renderer.setPixelRatio(Math.min(devicePixelRatio,2));host.appendChild(renderer.domElement);
const controls=new OrbitControls(camera,renderer.domElement);controls.enableDamping=true;controls.target.set(0,1,0);
const grid=new THREE.GridHelper(20,40,0x236079,0x153246);scene.add(grid);
const objects=[];let selected=null;
const mat=()=>new THREE.MeshStandardMaterial({color:0x4deBff,metalness:.55,roughness:.28,emissive:0x07303a});
function add(geometry,x=0,y=1,z=0){const mesh=new THREE.Mesh(geometry,mat());mesh.position.set(x,y,z);scene.add(mesh);objects.push(mesh);selected=mesh;document.getElementById('status').textContent='Objektov: '+objects.length+' • vybraný objekt';return mesh}
function clear(){for(const o of objects){scene.remove(o);o.geometry.dispose();o.material.dispose()}objects.length=0;selected=null}
window.setShape=(name)=>{clear();if(name==='GUĽA')add(new THREE.SphereGeometry(1,48,32));else if(name==='PYRAMÍDA')add(new THREE.ConeGeometry(1,2,4));else if(name==='DOM'){add(new THREE.BoxGeometry(2,1.5,2),0,.75,0);const roof=add(new THREE.ConeGeometry(1.55,.9,4),0,1.95,0);roof.rotation.y=Math.PI/4;}else if(name==='AUTO'){add(new THREE.BoxGeometry(3,.65,1.4),0,.75,0);add(new THREE.BoxGeometry(1.5,.65,1.15),-.15,1.35,0);for(const x of [-1,1])for(const z of [-.62,.62]){const w=add(new THREE.CylinderGeometry(.34,.34,.18,24),x,.35,z);w.rotation.x=Math.PI/2;w.material.color.set(0x172b3a)}}else if(name==='ULICA'){add(new THREE.BoxGeometry(18,.12,5),0,.06,0).material.color.set(0x263b4a);for(let x=-7;x<=7;x+=2.5){const mark=add(new THREE.BoxGeometry(1,.025,.12),x,.13,0);mark.material.color.set(0xb5eaf4)}for(const x of [-6,6]){const building=add(new THREE.BoxGeometry(3,3,3),x,1.5,-4);building.material.color.set(0x17617a)}}else add(new THREE.BoxGeometry(2,2,2));};
window.addCube=()=>add(new THREE.BoxGeometry(1,1,1),(Math.random()-.5)*2,1,(Math.random()-.5)*2);
window.removeSelected=()=>{if(!selected)return;scene.remove(selected);objects.splice(objects.indexOf(selected),1);selected.geometry.dispose();selected.material.dispose();selected=null;document.getElementById('status').textContent='Objekt odstránený • zostáva '+objects.length};
window.duplicateSelected=()=>{if(!selected){document.getElementById('status').textContent='Najprv vyber objekt';return}const copy=new THREE.Mesh(selected.geometry.clone(),selected.material.clone());copy.position.copy(selected.position).add(new THREE.Vector3(.6,0,.6));copy.rotation.copy(selected.rotation);copy.scale.copy(selected.scale);scene.add(copy);objects.push(copy);selected=copy;document.getElementById('status').textContent='Objekt duplikovaný • spolu '+objects.length};
window.moveSelected=(x,y,z)=>{if(!selected){document.getElementById('status').textContent='Najprv vyber objekt';return}selected.position.add(new THREE.Vector3(x,y,z));document.getElementById('status').textContent='Poloha: '+selected.position.toArray().map(v=>v.toFixed(2)).join(', ')};
window.rotateSelected=()=>{if(!selected){document.getElementById('status').textContent='Najprv vyber objekt';return}selected.rotation.y+=Math.PI/12;document.getElementById('status').textContent='Otočenie: '+(selected.rotation.y*180/Math.PI).toFixed(0)+'°'};
window.scaleSelected=(delta)=>{if(!selected){document.getElementById('status').textContent='Najprv vyber objekt';return}const factor=delta>0?1.1:.9;selected.scale.multiplyScalar(factor);document.getElementById('status').textContent='Mierka: '+selected.scale.x.toFixed(2)};
window.resetCamera=()=>{camera.position.set(5,4,7);controls.target.set(0,1,0);controls.update()};
function sceneData(){return {format:'jarvis-scene-v1',savedAt:new Date().toISOString(),objects:objects.map(o=>({geometry:o.geometry.type,parameters:o.geometry.parameters,position:o.position.toArray(),rotation:o.rotation.toArray(),scale:o.scale.toArray(),color:'#'+o.material.color.getHexString()}))}}
window.saveScene=()=>{try{localStorage.setItem('jarvis-scene',JSON.stringify(sceneData()));document.getElementById('status').textContent='Scéna uložená lokálne v tomto tablete • '+objects.length+' objektov'}catch(e){document.getElementById('status').textContent='Uloženie zlyhalo: '+e.message}};
window.loadScene=()=>{try{const raw=localStorage.getItem('jarvis-scene');if(!raw)throw Error('Zatiaľ nie je uložená scéna');const data=JSON.parse(raw);if(data.format!=='jarvis-scene-v1'||!Array.isArray(data.objects))throw Error('Neplatný formát projektu');clear();for(const o of data.objects){let g;const p=o.parameters||{};switch(o.geometry){case 'BoxGeometry':g=new THREE.BoxGeometry(p.width||1,p.height||1,p.depth||1);break;case 'SphereGeometry':g=new THREE.SphereGeometry(p.radius||1,p.widthSegments||32,p.heightSegments||16);break;case 'ConeGeometry':g=new THREE.ConeGeometry(p.radius||1,p.height||2,p.radialSegments||8);break;case 'CylinderGeometry':g=new THREE.CylinderGeometry(p.radiusTop||1,p.radiusBottom||1,p.height||1,p.radialSegments||8);break;default:continue}const m=add(g);m.position.fromArray(o.position||[0,1,0]);m.rotation.set(...(o.rotation||[0,0,0]));m.scale.fromArray(o.scale||[1,1,1]);m.material.color.set(o.color||'#4deBff')}document.getElementById('status').textContent='Načítaná scéna • '+objects.length+' objektov'}catch(e){document.getElementById('status').textContent='Načítanie zlyhalo: '+e.message}};
window.exportScene=()=>{const data=JSON.stringify(sceneData(),null,2);let panel=document.getElementById('scene-export');if(!panel){panel=document.createElement('textarea');panel.id='scene-export';panel.style='position:absolute;z-index:10;left:8px;right:8px;bottom:40px;width:calc(100% - 16px);height:110px;background:#071a29;color:#dffaff;border:1px solid #4deBff;border-radius:8px;padding:8px;font:10px monospace';panel.title='Export JSON – podržte a skopírujte';document.body.appendChild(panel)}panel.value=data;panel.focus();panel.select();document.getElementById('status').textContent='JSON export pripravený na skopírovanie • GLB/STL export zatiaľ nie je dostupný'};
const raycaster=new THREE.Raycaster(),pointer=new THREE.Vector2();
renderer.domElement.addEventListener('pointerdown',e=>{const r=renderer.domElement.getBoundingClientRect();pointer.x=((e.clientX-r.left)/r.width)*2-1;pointer.y=-((e.clientY-r.top)/r.height)*2+1;raycaster.setFromCamera(pointer,camera);const hit=raycaster.intersectObjects(objects)[0];if(hit){selected=hit.object;selected.material.emissive.set(0x07505c)}});
function resize(){const w=host.clientWidth,h=host.clientHeight;renderer.setSize(w,h,false);camera.aspect=w/h;camera.updateProjectionMatrix()}
new ResizeObserver(resize).observe(host);resize();window.setShape('KOCKA');
function animate(){requestAnimationFrame(animate);controls.update();renderer.render(scene,camera)}animate();
</script></body></html>
        """.trimIndent()
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("3D EDITOR • výber objektov, posun, otočenie, mierka a duplikovanie", color = WorkCyan, fontSize = 10.sp)
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(340.dp).background(Color(0xFF07101A), RoundedCornerShape(12.dp)),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowContentAccess = false
                    settings.allowFileAccess = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    setBackgroundColor(android.graphics.Color.rgb(7,16,26))
                    loadDataWithBaseURL("https://jarvis.local/", html, "text/html", "UTF-8", null)
                }
            },
            update = { view ->
                val safeModel = model.replace("'", "").replace("\\", "")
                if (view.tag != safeModel) {
                    view.tag = safeModel
                    view.evaluateJavascript("window.setShape && window.setShape('$safeModel')", null)
                }
            }
        )
        Text("Model: $model • základné úpravy geometrie sú aktívne. Scény možno uložiť a načítať lokálne; export je zatiaľ JSON. Presné CAD rozmery, mesh editovanie a GLB/STL export nie sú implementované.", color = Color(0xFF9BAFC2), fontSize = 9.sp)
    }
}
