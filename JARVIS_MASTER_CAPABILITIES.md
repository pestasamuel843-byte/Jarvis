# JARVIS Ultimate — Master capability specification

This file is the product contract for the Android tablet app. It is not a claim that every capability is already implemented.

## Identity and interaction
- Slovak-first assistant; formal vykanie; concise, calm, professional responses.
- Wake phrase: "Hej Jarvis" / "Hey Jarvis"; after wake, accept one natural spoken command and return to wake-word mode.
- Preferred voice: Slovak male voice closest to Lukáš, if installed in Android TTS.
- Landscape tablet-first, dark futuristic cyan HUD, animated central core, personal panels.
- User can stop listening and control microphone permission. Never claim background/locked-screen listening is reliable until tested on the target tablet.

## Personal dashboard
- Personal overview, school grades by subject, timetable, notes, daily check-ins, trends and charts.
- Health/wellbeing panel displays only information explicitly entered or connected by the user. It is not medical diagnosis.
- Instagram panel may show only content available through a supported, authorized integration. A decorative panel is not a live feed.
- Keep private data on-device where practical; never place API keys or personal data in source control.

## Tablet actions
- Open installed apps, browser, YouTube, Instagram, camera, calculator, files and Android settings.
- Notes, saved preferences, local timetable, reminders/alarms/calendar hand-off, date/time and battery.
- Wi-Fi, Bluetooth, display, sound and app-permission actions must use Android system settings unless a documented Android API permits more. Do not claim a toggle happened merely because a settings screen opened.
- Sending messages, placing calls, deleting data, purchases or other consequential actions require a clear confirmation.
- Do not bypass Android permission prompts, lock screen, app authentication, or platform restrictions.

## AI and internet
- Gemini/OpenAI-compatible Chat Completions provider configured in Settings; key stored only on device.
- Internet search for current facts, source URLs, comparisons and research; be transparent if results are missing or uncertain.
- AI may plan and explain actions, but may only execute actions implemented by the Android app. It must never fabricate successful actions, citations, sensor readings or user data.
- No-key operation remains available for local/offline functions. Online AI requires an API key and internet unless a genuinely keyless hosted service is later integrated.

## Maps and navigation
- Futuristic interactive map, search, pan/zoom, tilt/rotation, 3D building extrusion, current location after permission, route preview.
- Be explicit that map detail depends on online tile coverage and public service availability; this is not a complete photorealistic 3D globe or live-traffic guarantee.

## Project workspace
- Voice-guided design workflow for cars, devices, buildings, rooms, streets and other concepts.
- 2D sketching and current rotatable primitive/wireframe previews.
- Future target: editable scene graph, real 3D assets, import/export, undo/redo and richer model generation.
- Do not describe the existing primitive preview as arbitrary CAD or full 3D model generation.

## Explicit exclusions
- No Termux, root, ADB or shell-based dependency.
- No smart-home controls unless requested later.
- No emergency-call feature.
- No unauthorized access to Instagram/private messages or other apps; use official supported integrations and user confirmation.
- No hidden surveillance or always-on screen capture. Any screen/camera access must be explicit, visible and stoppable.

## Current implementation snapshot (must be kept honest)
- Local: Slovak speech recognition/TTS, wake-word loop while the app is active, notes/preferences, timetable and local progress records, basic arithmetic, battery/time/date, selected Android app/settings intents, landscape HUD.
- AI: Gemini-compatible endpoint and API-key settings; API connectivity test.
- Web: DuckDuckGo HTML search helper used as context for selected current-information queries; public web results can be incomplete.
- Map: MapLibre/OpenFreeMap 3D extruded building layer, place search and optional routing.
- Workspace: freehand sketch and predefined rotatable wireframe primitives.
- Not yet delivered/verified: robust background wake word while locked, fully offline Slovak speech model, automatic live Instagram feed/messages, arbitrary editable 3D/CAD generation, full autonomous control of third-party apps, complete health-platform integration, final APK/device QA.


## Ultimate build target — unrestricted scope

The product target is a premium, cinematic tablet command system. The interface must feel like a live instrument panel, not a collection of ordinary Android cards.

### Visual system
- Full-screen landscape command deck with layered depth, fine grid, scan lines, orbital geometry, animated central reactor/core and state-driven light effects.
- Distinct visible states: standby, wake-word detected, listening, reasoning, web research, mapping, speaking, offline and fault.
- Floating telemetry for clock, battery, network, model state, microphone state and active task. Every indicator must reflect real app state; no fake sensor values or fake “online” claims.
- Personal dashboard, command console, map/navigation and 3D project studio should feel like coordinated parts of one system.
- Responsive tablet layout, readable typography, restrained cyan/blue light, subtle motion and no childish styling.

### Intelligence architecture
- Local model manager with separately downloadable model packages; support a compact model and a larger advanced model when a compatible LiteRT-LM package is verified.
- Offline reasoning, memory retrieval, summarization and local command planning.
- Optional online model provider for stronger reasoning and current web research. Clearly label which engine is active.
- Tool/action layer: model proposes a structured action; Android app validates permissions and arguments; consequential actions require confirmation; result is reported only after Android confirms it.
- Model downloads need checksum/size validation, cancellation, retry/resume, storage-space checks and clear progress.

### 3D spatial and design systems
- Map workspace: 3D extruded buildings, camera tilt/orbit, search, selected-place details, route overlays and user-controlled location permission.
- Project studio: actual editable 3D scene objects, transform handles, dimensions, materials, layers, undo/redo, save/open/export and voice-directed edits.
- A wireframe placeholder must never be presented as a finished 3D model or CAD editor.

### Voice system
- Explicitly enabled wake-word session with visible microphone indicator and immediate stop control.
- Wake phrase: “Hej Jarvis”; one request is processed per wake cycle, then returns to wake detection while the app is active.
- Slovak speech recognition and preferred Slovak male TTS voice when installed.
- No covert recording, no hidden screen capture and no claim of reliable locked-screen listening until device testing confirms it.

### Delivery gates
1. Source changes compile in GitHub Actions.
2. APK installs on the target Android tablet.
3. Permissions, offline model download, voice loop, 3D map and project workspace are tested individually.
4. UI is tested in tablet landscape and remains responsive.
5. Only then publish an installable APK and call it ready.

Current offline-model code adds a Qwen3 0.6B INT4 download path and LiteRT-LM integration, but the repository has not yet reported a successful CI build or device test. This is a prototype integration, not proof of a working offline AI release.
