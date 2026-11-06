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
