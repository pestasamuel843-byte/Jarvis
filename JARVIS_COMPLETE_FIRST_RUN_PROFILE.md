# JARVIS Ultimate — Complete First-Run Profile and Feature Requirements

Status: implementation specification, not proof that every feature is already implemented or tested.

## 1. First launch: prepared profile
- On first launch show “Vitajte Samuel”, then the JARVIS identity/logo and the full tablet dashboard.
- Preload the known profile below from this project specification. Do not pretend the app has imported every ChatGPT conversation: only information explicitly included in the app or imported by the user is available.
- Provide a private profile screen where Samuel can inspect, correct, export and delete stored facts. Keep personal memory local by default. Clearly show when any information is sent to an online AI service.
- Never invent an exact favorite song, artist, contact, school mark, password or personal fact. Ask only when a missing fact is necessary.
- Default language Slovak; concise, calm, professional, helpful. Preferred spoken address: “pane”. Male Slovak voice preference: sk-SK-LukasNeural when available; speech rate 0.95 and pitch 0.85 where supported.
- User profile facts that may be preloaded: likes futuristic Iron-Man/JARVIS design; music interests include Christian rap, guitar, dark UK drill around 140 BPM, 808s, drums, piano, guitar and trumpet; interested in athletics, medicine/neurosurgery and emergency care; school context includes 9th grade. Personal context about Zuzka is private and should be surfaced only when directly relevant. Do not fabricate exact favorite tracks or current relationship status.

## 2. Voice interaction
- Wake phrase “Hej Jarvis” / “Hey Jarvis” is required only to start a voice session.
- After wake-up, keep a conversational session active. User can say “Jarvis…”, “Počuj…”, or simply speak naturally without repeating the wake phrase.
- After every answer, resume listening while the session remains active. End only on “Jarvis, spi”, “ukonči rozhovor”, or the visible stop control. The mic must visibly show standby/listening/thinking/speaking states.
- Do not silently record. Do not claim reliable wake listening while the app is backgrounded or the screen is locked unless device-tested. Pause recognition during speech output and resume safely afterward.
- The app must support Slovak speech recognition and male Slovak speech output where available. If recognition or TTS is unavailable, show a clear error and retain touch controls.

## 3. Film and video companion mode
- Commands: “Jarvis, stopni film”, “Jarvis, pokračuj”, “Jarvis, čo si myslíš o tejto postave?”, “vysvetli tú scénu”.
- Control playback through supported media-session controls or the active player’s supported integration. Do not claim success unless the player confirms or the action is visibly performed.
- To comment on a character or scene, use only an explicitly enabled and visibly indicated screen-sharing/frame-analysis feature, or user-provided title/scene description. Never secretly capture the screen, record audio/video, or continuously watch.
- Respect copyright: discuss and summarize; do not reproduce long copyrighted dialogue, scripts or subtitles.
- If the player does not expose controls or image access, say so plainly and offer a supported alternative such as the user naming the film/character.

## 4. Gmail and communication
- Use official Google OAuth and Gmail API scopes. Never request or store the user's Google password.
- After the user explicitly connects Gmail, support unread count, inbox listing, search, selected-message reading and concise summaries.
- Draft messages from dictation. Before sending, show recipient, subject and complete body and require explicit final confirmation. Never send merely because the user dictated a draft.
- Include disconnect/revoke access and explain granted permissions.
- For notifications and other private messages, summarize before reading when requested; never send a reply without explicit confirmation.
- Instagram is not connected by default. Do not imply access until an official supported connection exists.

## 5. Music
- Support presets such as WORK, TRAINING, CALM and FAVORITES.
- Start playback through a connected supported music service or installed app. User must choose/connect the service and grant required access.
- Learn exact favorite tracks only when the user names or selects them. Never invent “my favorite song”.
- “Pusť hudbu na prácu” should choose the WORK preset, using saved preferences and available service controls. Report honestly if no service is connected.

## 6. Tablet actions and web
- Support web search with source links, opening websites, app launching, time/date, battery and available network status, notes, reminders, file picker, gallery/camera only on explicit request, and Android settings such as Wi-Fi, Bluetooth, sound and display where Android permits.
- Use official Android intents and APIs. Optional Accessibility Service must be opt-in, clearly explained, visibly enabled and limited to user-commanded tasks. Never bypass logins, CAPTCHA, payment confirmation, device security or app restrictions.
- Consequential actions (sending messages, purchases, deleting data, sharing personal information, account changes) require confirmation.
- Do not claim that every finger/touch action can be reproduced by voice; describe unsupported actions honestly.

## 7. Maps and 3D project workspace
- Futuristic custom map view with MapLibre/OpenFreeMap vector tiles, 3D extruded buildings, tilt/rotation, search, route overlays and location only after permission. Do not describe it as photorealistic.
- Project mode should provide an editable 3D workspace with objects, dimensions, materials, layers, undo/redo, save/export and voice-directed edits. A simple wireframe preview is not a finished CAD system.
- UI: dark navy/black, cyan-blue holographic HUD, animated central reactor/core, restrained scan lines, live system telemetry, side panels, clear status states, tablet landscape layout. In project mode panels may slide aside to give the workspace room.
- Dashboard can show school marks by subject and personal progress/check-ins only from data the user has actually entered. Do not infer mental-health diagnoses from mood check-ins.

## 8. AI brain and connectivity
- Slovak conversational assistant with online provider support and optional local offline model. Clearly distinguish online and offline capability.
- The offline Qwen 0.6B model is a modest local model, not an all-knowing assistant. Do not claim it can match a large cloud model or know current information offline.
- Web search results must include source links. Validate tool arguments and permissions. Report the actual result of every action, not merely the intention.
- API keys must be stored securely using Android Keystore-backed encrypted storage where practical; never commit keys to Git or expose them in logs. Provide remove/replace controls and clear error messages.

## 9. Privacy and security
- App lock/PIN or Android biometric unlock for private areas; do not invent a PIN or silently enable a weak default.
- Local-first storage for personal profile, notes and progress. Provide review/export/delete controls.
- Request Android permissions only at the moment a feature needs them and explain why. Camera, microphone, location, Gmail and Accessibility permissions must be separately controllable.
- No smart-home control and no emergency-calling feature.
- No hidden screen capture, continuous camera recording, secret uploads or background access to private accounts.
- Protect API credentials and OAuth tokens; provide account disconnect and data deletion.

## 10. Delivery and acceptance tests
- The deliverable requested is a complete installable Android APK, not only source snippets or a mock-up.
- Build against the actual Android project, run Gradle/CI, fix compilation errors, and install/test on a real Android tablet before claiming it works.
- Test: first-launch profile; Slovak wake/session/stop flow; TTS; mic state; Gmail OAuth and confirmation; music integration; playback controls; screen analysis consent; map rendering; 3D workspace; local storage; app lock; permissions; offline model download and inference; rotation/landscape; app lifecycle.
- Mark each capability as implemented, requires account/permission, prototype, or not implemented. Never call the project complete or provide a fake APK link without a verified build artifact.
