package com.jarvis.assistant

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

/**
 * Interactive WebGL scene editor. It uses Three.js for real perspective geometry,
 * orbit controls, object selection and primitive editing. Internet is required
 * for the Three.js ES modules loaded from jsDelivr.
 */
@Composable
fun ThreeDSceneEditor(initialModel: String = "AUTO") {
    val html = """
<!doctype html>
<html lang="sk">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>
*{box-sizing:border-box}html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#050b13;color:#e8faff;font:13px system-ui}
#view{position:absolute;inset:0}canvas{display:block}
.panel{position:absolute;z-index:2;left:9px;right:9px;top:9px;padding:9px;border:1px solid #24536a;border-radius:12px;background:#071522eF}
.brand{color:#4deBff;font-size:9px;font-weight:700;letter-spacing:2px;margin-bottom:7px}
.tools{display:flex;gap:5px;flex-wrap:wrap}
button{border:1px solid #28566b;border-radius:7px;background:#0b2435;color:#4deBff;padding:8px 10px;font-weight:650;font-size:11px}
button:active,button.active{background:#17516a;color:white}
#status{font-size:10px;color:#a8c7d5;margin-top:7px}
.bottom{position:absolute;z-index:2;left:9px;right:9px;bottom:9px;padding:8px;border:1px solid #24536a;border-radius:10px;background:#071522eF}
.bottom .tools{justify-content:space-between}
</style>
</head>
<body>
<div id="view"></div>
<div class="panel">
 <div class="brand">J.A.R.V.I.S. / 3D DESIGN ENGINE</div>
 <div class="tools">
  <button onclick="add('box')">+ KOCKA</button><button onclick="add('sphere')">+ GUĽA</button>
  <button onclick="add('cylinder')">+ VALEC</button><button onclick="add('cone')">+ KUŽEĽ</button>
  <button onclick="add('torus')">+ TORUS</button><button onclick="removeSelected()">ODSTRÁNIŤ</button>
 </div>
 <div id="status">Načítavam 3D engine…</div>
</div>
<div class="bottom">
 <div class="tools">
  <button id="move" class="active" onclick="mode('translate')">POSUN</button>
  <button id="rotate" onclick="mode('rotate')">ROTÁCIA</button>
  <button id="scale" onclick="mode('scale')">VEĽKOSŤ</button>
  <button onclick="resetCamera()">KAMERA</button>
  <button onclick="saveScene()">ULOŽIŤ SCÉNU</button>
 </div>
</div>
<script type="importmap">
{"imports":{"three":"https://cdn.jsdelivr.net/npm/three@0.164.1/build/three.module.js","three/addons/":"https://cdn.jsdelivr.net/npm/three@0.164.1/examples/jsm/"}}
</script>
<script type="module">
import * as THREE from 'three';
import {OrbitControls} from 'three/addons/controls/OrbitControls.js';
import {TransformControls} from 'three/addons/controls/TransformControls.js';
const host=document.getElementById('view'),statusEl=document.getElementById('status');
const scene=new THREE.Scene();scene.background=new THREE.Color(0x050b13);
const camera=new THREE.PerspectiveCamera(48,1,.1,1000);camera.position.set(7,6,9);
const renderer=new THREE.WebGLRenderer({antialias:true,alpha:false});renderer.setPixelRatio(Math.min(devicePixelRatio,2));renderer.shadowMap.enabled=true;host.appendChild(renderer.domElement);
const orbit=new OrbitControls(camera,renderer.domElement);orbit.enableDamping=true;
const transform=new TransformControls(camera,renderer.domElement);scene.add(transform.getHelper());
transform.addEventListener('dragging-changed',e=>orbit.enabled=!e.value);
const hemi=new THREE.HemisphereLight(0xbcefff,0x122033,2.0);scene.add(hemi);
const key=new THREE.DirectionalLight(0xffffff,2.2);key.position.set(5,9,7);key.castShadow=true;scene.add(key);
const grid=new THREE.GridHelper(30,30,0x1b7892,0x153044);scene.add(grid);
const axes=new THREE.AxesHelper(2.2);scene.add(axes);
const material=()=>new THREE.MeshStandardMaterial({color:0x39cbe8,metalness:.38,roughness:.34});
let selected=null;let count=0;
function status(s){statusEl.textContent=s}
function select(o){selected=o;transform.attach(o);status('Vybrané: '+o.name+' • potiahni osi na úpravu')}
window.add=function(type){
 let geometry;
 if(type==='box')geometry=new THREE.BoxGeometry(1.5,1.5,1.5);
 else if(type==='sphere')geometry=new THREE.SphereGeometry(.85,32,20);
 else if(type==='cylinder')geometry=new THREE.CylinderGeometry(.7,.7,1.8,32);
 else if(type==='cone')geometry=new THREE.ConeGeometry(.85,1.8,32);
 else geometry=new THREE.TorusGeometry(.75,.25,16,48);
 const mesh=new THREE.Mesh(geometry,material());mesh.position.set((count%3-1)*1.8,1,(Math.floor(count/3)%3)*1.8);mesh.name=type.toUpperCase()+'_'+(++count);mesh.castShadow=true;mesh.receiveShadow=true;scene.add(mesh);select(mesh);
};
window.removeSelected=function(){if(!selected)return;scene.remove(selected);selected.geometry.dispose();selected.material.dispose();transform.detach();selected=null;status('Objekt odstránený')};
window.mode=function(m){transform.setMode(m);['move','rotate','scale'].forEach(id=>document.getElementById(id).classList.toggle('active',id===({translate:'move',rotate:'rotate',scale:'scale'}[m])));status('Režim úprav: '+({translate:'posun',rotate:'rotácia',scale:'veľkosť'}[m]))};
window.resetCamera=function(){camera.position.set(7,6,9);orbit.target.set(0,0,0);orbit.update()};
window.saveScene=function(){const data=scene.toJSON();try{localStorage.setItem('jarvis-three-scene',JSON.stringify(data));status('Scéna uložená lokálne v tablete')}catch(e){status('Uloženie zlyhalo: '+e.message)}};
function restore(){try{const raw=localStorage.getItem('jarvis-three-scene');if(!raw)return;const loaded=new THREE.ObjectLoader().parse(JSON.parse(raw));loaded.children.forEach(o=>{if(o.isMesh){scene.add(o);count++}});status('Obnovená uložená 3D scéna')}catch(e){status('Nová scéna pripravená')}}
const raycaster=new THREE.Raycaster(),pointer=new THREE.Vector2();
renderer.domElement.addEventListener('pointerdown',e=>{if(transform.dragging)return;const r=renderer.domElement.getBoundingClientRect();pointer.x=((e.clientX-r.left)/r.width)*2-1;pointer.y=-((e.clientY-r.top)/r.height)*2+1;raycaster.setFromCamera(pointer,camera);const hits=raycaster.intersectObjects(scene.children.filter(o=>o.isMesh),false);if(hits.length)select(hits[0].object)});
function resize(){const w=host.clientWidth,h=host.clientHeight;camera.aspect=w/h;camera.updateProjectionMatrix();renderer.setSize(w,h)}
new ResizeObserver(resize).observe(host);resize();restore();
if(count===0){const preset="\${initialModel}".toUpperCase();const shape=({AUTO:"box",KOCKA:"box",GUĽA:"sphere",PYRAMÍDA:"cone",DOM:"box",IZBA:"box",ULICA:"box"})[preset]||"box";add(shape)}
function frame(){requestAnimationFrame(frame);orbit.update();renderer.render(scene,camera)}frame();
status('3D engine pripravený • pridaj objekt, vyber ho a uprav osi');
</script>
</body>
</html>
    """.trimIndent()
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(390.dp),
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                setBackgroundColor(android.graphics.Color.rgb(5, 11, 19))
                loadDataWithBaseURL("https://jarvis.local/", html, "text/html", "UTF-8", null)
            }
        }
    )
}
