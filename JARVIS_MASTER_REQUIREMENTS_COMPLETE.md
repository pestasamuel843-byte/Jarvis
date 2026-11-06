# JARVIS ULTIMATE — MASTER REQUIREMENTS
## Consolidated record of the user's accumulated vision

**Purpose:** This is the consolidated product specification reconstructed from available conversation history and project context. It is not a claim that all features are implemented. Where a requirement has changed, the newest instruction takes precedence. Exploratory/future ideas are labelled as such.

---

# 1. Product identity and delivery

- Product: JARVIS / JARVIS Ultimate, a standalone Android tablet personal assistant inspired by the capable JARVIS interface from Iron Man.
- It must be a real installable Android application, delivered as a directly installable **APK**, not merely a ZIP, source snippet, mock-up, web wrapper or instructions to build it.
- User expects the complete app to be built, tested and functional. Do not present a prototype as finished.
- Platform constraints explicitly stated: Android tablet, Android 12 target in earlier brief; current device context later reported as Android 13 Tab 50 WiFi. Earlier device mentioned as NPad Pro. Verify actual device compatibility during testing.
- Must be a standalone Android app, not Termux. Explicit exclusions: no Termux, ADB, root, shell scripts or Termux:API as required setup.
- App should launch from its normal home-screen icon.
- No requirement for a PC to be used by the user to operate the finished app.
- User wants as little setup and clicking as possible. Earlier versions accepted an API key setting; later the user repeatedly asked for a prebuilt assistant that works immediately without keys or manual integrations. Treat this as a strong product goal: provide a usable built-in/offline baseline and clearly explain which advanced cloud features need an account, key or permission. Never pretend online AI can be provided free or keyless if it cannot.
- Existing repository: https://github.com/pestasamuel843-byte/Jarvis
- Package known in project context: com.jarvis.assistant.

# 2. First launch and onboarding

- On first launch, show “Vitajte Samuel” before the JARVIS logo.
- Animate the welcome/logo into the full tablet command dashboard; the central logo/core should settle smaller into the main interface.
- First launch should already contain the known, user-approved profile and preferences; it should not feel like an empty generic chatbot.
- Provide a clear first-run status for microphone, speech recognition, speech synthesis, AI engine, internet, offline brain, map, and integrations.
- Ask only for permissions when the corresponding feature is first used. Explain each permission.
- Do not demand a long setup wizard. Make the core usable immediately where Android permits.
- Settings should expose voice, AI provider, local model, privacy, permissions, accounts, memory, security, map and workspace controls.
- User expects the assistant to know the user's preferences and project context. However, do not claim automatic access to every prior ChatGPT conversation. Only preload known, explicitly available facts or import data with the user's authorization. Provide review/edit/export/delete controls.

# 3. Personality and conversation

- Slovak-first. Understand informal typed and spoken Slovak, imperfect phrasing, colloquial words and short commands.
- Calm, professional, serious, helpful, proactive, intelligent, concise; not childish, not verbose.
- Address the user as “pane” in spoken assistant style.
- Startup greeting requested in earlier project context: “Dobrý deň, pane. JARVIS je pripravený. Systémy sú online.”
- The first-launch screen text “Vitajte Samuel” precedes the logo; this is separate from the spoken greeting.
- Should understand context and follow-up references naturally, not require rigid command syntax.
- Should answer questions, explain, reason, plan, research, program, analyze, suggest useful next steps and admit uncertainty.
- Must not invent personal facts, memories, actions, search results or successful integrations.

# 4. Voice and microphone — critical requirement

- Wake phrase: “Hej Jarvis” / “Hey Jarvis”.
- Wake phrase is used **only once at the beginning** to wake the assistant.
- After wake-up, user speaks naturally without repeating the wake phrase. Examples: “Jarvis, otvor Gmail”, “Počuj, vyhľadaj…”, “Pusť hudbu na prácu”, “Čo si myslíš o tejto postave?”
- Keep the conversational session active across multiple user turns and JARVIS replies.
- End the active session only when the user says “Jarvis, spi”, “ukonči rozhovor”, or uses an obvious stop control. Then return to wake-word standby.
- This latest continuous-session design supersedes an older one-command-then-microphone-off design.
- The microphone must be reliable. Earlier user described desired behavior as starting when the user begins speaking, stopping when speech ends, then JARVIS responds. Newer explicit wake/session design defines how activation and session continuity should work.
- The app must clearly show mic state: standby, listening, processing, speaking, paused/error. The user must be able to stop microphone access immediately.
- Avoid hearing its own TTS as a user command; pause recognition during JARVIS speech and resume after output.
- Slovak speech recognition; do not randomly stop listening or leave a stuck microphone session.
- Preferred male Slovak voice: “Lukáš” / sk-SK-LukasNeural when available. The Edge neural voice is not guaranteed through Android TTS; select the closest installed Slovak male voice and disclose fallback. Earlier desired speech rate around 0.95 and pitch 0.85; use only if the engine supports it.
- Do not claim reliable background/locked-screen wake-word support until tested on the target tablet and Android version. Explain Android battery/microphone restrictions honestly.
- Voice should be the primary interaction; touch controls remain available as fallback and for permissions/security confirmations.

# 5. AI brain and learning

- A real AI brain is required, not an app that merely opens ChatGPT.
- Should support conversation, reasoning, learning from user-approved memory, coding, planning, research, explaining, and using app capabilities.
- Online AI provider support was requested, with model/provider settings and an API key field in earlier specifications. Gemini 2.5 Flash endpoint was used in the prototype.
- User later strongly preferred a ready-to-use assistant with no keys or manual connecting. Product should therefore have a keyless local baseline and a clear optional online upgrade. Never promise a large online model without credentials, connectivity or provider access.
- Offline model support requested. Current prototype uses a modest Qwen 0.6B LiteRT-LM model concept; this is not equivalent to a powerful cloud model and must not be marketed as all-knowing.
- Local/offline and online capability must be clearly distinguished. Offline knowledge may be stale and cannot search the live web.
- AI should use available tools to perform actions, not merely tell the user how. Every action must be implemented, permissioned, validated and report its real result.
- If no tool exists for a requested action, say so plainly.

# 6. Internet, search and knowledge

- Internet access and web search are core requested capabilities.
- Search current information, websites, sources, documentation, products and news.
- Present useful source links and distinguish verified facts from estimates or incomplete search results.
- Open websites and help navigate them.
- Answer questions about things the user is watching or doing when the required context is available.
- Programming capability: create, explain, debug and improve code; support project mode and real file changes only where implemented.
- Do not claim to browse or open a page if no successful result confirms it.

# 7. Film and screen companion

- User wants to watch a film and speak to JARVIS during playback.
- Example commands: “Jarvis, stopni ho/film”; “Jarvis, pokračuj”; “Jarvis, čo si myslíš o tejto postave?”; “vysvetli mi túto scénu”.
- Pause/resume playback using Android media session controls or supported player integrations where available.
- Discuss a character or scene using the film title, user description, subtitles/context legitimately available, or an explicitly enabled and visibly indicated screen/frame-sharing feature.
- Screen viewing must be permission-based and visible. No secret screenshots, hidden screen recording, continuous camera recording or covert uploads.
- Spoken stop instruction such as “nekukaj” must stop screen viewing/sharing immediately.
- If player controls or image access are unavailable, state the limitation rather than claiming control.
- Respect copyright: discuss and summarize films, do not reproduce long copyrighted dialogue or scripts.

# 8. Android and app control

- User wants JARVIS to perform actions that a person could perform by tapping the tablet, using voice as the main interface.
- Launch supported installed apps and open websites.
- Open system settings such as Wi-Fi, Bluetooth, sound, display, app permissions and other supported settings.
- Search the web, open files, open gallery and use camera only when explicitly requested.
- Support app navigation and user-directed workflows where Android APIs permit.
- Optional Accessibility Service may be used only if the user deliberately enables it, understands the permission, and can see when it is active. It must not bypass login, CAPTCHA, payment confirmation, app security, or other protections.
- Consequential actions such as sending messages, sharing personal data, deleting data, account changes or purchases require explicit confirmation.
- No claim that every app or every touch can be automated; list real support and limitations.

# 9. Gmail, messages and social apps

- Gmail integration requested: user can ask “Jarvis, aké mám Gmaili?” and JARVIS should report unread mail / available inbox summary after account connection.
- Use official Google OAuth and Gmail API. Never request the Google password.
- List unread messages, search mail, read or summarize selected messages, and draft messages from voice dictation.
- Before sending, show recipient, subject and full message and require a final explicit confirmation.
- Include disconnect/revoke permissions and token security.
- Message/notification reading should be user-requested; summarize first when useful.
- Before replying or sending any message, obtain explicit confirmation.
- Instagram opening was requested; actual inbox/feed access must not be claimed unless a supported official integration exists and user connects it.
- Do not secretly read private conversations or send replies.

# 10. Music and audio

- Play music through supported installed apps or connected music services.
- Understand natural requests such as “Pusť hudbu na prácu”, “na tréning”, “niečo pokojné”, “moju obľúbenú skladbu”.
- Create/use presets such as WORK, TRAINING, CALM, FAVORITES.
- Learn exact favorite songs/artists only when the user names or selects them. Never invent a favorite track.
- Known music taste to seed only as broad preference: Christian rap, guitar, dark UK drill around 140 BPM, 808s, drums, piano, guitar and trumpet. These are styles/instruments, not confirmed favorite tracks.
- Support YouTube/search/music launch where app/service controls permit.
- No claim playback started unless the app or media session confirms it.

# 11. Personal memory and user profile

- User wants JARVIS to know relevant information previously shared with ChatGPT: music preferences, school context, interests, projects, personal context and preferences.
- Provide a clear, privacy-preserving import path. Do not claim invisible access to ChatGPT account history.
- Store personal memory locally by default, with inspect/edit/export/delete controls.
- Only use personal details when relevant; avoid surfacing private relationship or personal details unnecessarily.
- Exact favorites, contacts, schedules and personal facts must be user-confirmed; do not infer or fabricate.
- User wants JARVIS to improve usefulness over time by remembering approved preferences and prior project decisions.
- Personal data must not be sent to an online model unless the user has clearly enabled that behavior.

# 12. School and personal dashboard

- Startup / daily check-in was requested: ask about school grades by subject and how the day went.
- Remember the subject, grade and date/time so progress can be tracked.
- Show grades in graphs by subject and show improvement/worsening over time.
- Include daily wellbeing/mood check-in and a trend graph based only on user-entered information; do not diagnose.
- When a graph or topic is requested, zoom/focus on the relevant graph automatically.
- Explain changes in plain Slovak.
- Dashboard should show useful personal information without cluttering every item into boxes.
- School timetable setup/editing was also discussed in the prototype.
- No invented marks, mood scores or school schedule.

# 13. Maps and navigation

- User explicitly wants a custom JARVIS map, not simply Google Maps embedded as the entire experience.
- Futuristic 3D map with buildings, streets and local/project-style visuals.
- Tilt, rotate, zoom, search, location when permission is granted, routes and useful overlays.
- Buildings should be represented as 3D extrusions where map data supports it. Do not call this photorealistic.
- Map should integrate into the full dashboard and project workspace.
- User wants map visuals detailed enough to feel like a real interactive map, not a static image or random drawing.

# 14. Project workspace, drawing and 3D creation

- A real project mode / workspace is required.
- User wants to describe what to build by voice and see the result in a 2D or 3D workspace.
- Support cars, buildings, rooms, devices, maps, streets, objects and other project concepts.
- Voice editing examples: change car, add lights, add spoiler; modify dimensions/materials; undo/redo.
- Actual editable objects, dimensions, materials, layers, selection, transform, save/load, undo/redo and export are desired.
- 3D models should be genuinely editable, not only an image or a static random SVG.
- 2D drawing and 3D preview should be available; code and preview/workspace should work together.
- User described a split interface with chat on the left and workspace on the right, separated by a divider. In project mode, side panels should slide away or workspace should take over more of the screen.
- Use a real 3D engine/scene where practical (Three.js/WebView or native equivalent were discussed), with a clear distinction between concept preview and full CAD functionality.
- A wireframe primitive demo is not a finished 3D editor.

# 15. Visual design and animation

- Overall look: advanced, futuristic, Iron Man / Tony Stark JARVIS command center; not childish.
- Dark navy/black background, cyan/blue holographic HUD, crisp technical typography, restrained glowing accents.
- Central animated JARVIS core/reactor, orbital rings, scanning lines, targeting geometry and telemetry.
- Core should animate according to state: standby, listening, thinking, searching, mapping, speaking, error.
- Full-tablet dashboard, preferably landscape orientation.
- Side panels for status and useful information; clean hierarchy, no excessive cards or clutter.
- Map, personal dashboard, system telemetry, chat and workspace should fit into the experience.
- Startup sequence: “Vitajte Samuel”, then JARVIS logo/core, then core shrinks/settles into dashboard.
- Interface should feel like a working system, not a static picture.

# 16. Camera and visual understanding

- Camera access only when the user explicitly asks to use it.
- Provide camera guidance/visual analysis when explicitly requested.
- No always-on camera, no secret recording and no hidden image storage.
- Screen sharing/film visual understanding is separate from camera permission and must also be explicit and visible.

# 17. Privacy, security and reliability

- App lock / PIN concept for private areas; biometric unlock may be supported if available.
- Never invent or silently set a weak PIN. User chooses it.
- Protect API keys and OAuth tokens; use Android Keystore-backed secure storage where practical. Never commit secrets to Git or log them.
- Local-first personal data and visible controls to review, export and delete.
- Permissions should be granular and requested only when needed: microphone, camera, location, screen capture, Gmail, Accessibility, notifications.
- Immediate stop for microphone and screen viewing.
- Visible indication whenever microphone, camera, location or screen sharing is active.
- Do not silently send private data to online AI.
- Do not store private messages or camera images without user authorization.
- Robust lifecycle behavior: handle app pause/resume, recognizer errors, permissions, network loss, TTS and mic conflicts.
- Clear error states and recovery; no fake “online” statuses.

# 18. Future / exploratory ideas

These were mentioned as future ambitions, not necessarily launch blockers:
- Support for smart glasses / wearable interface in a later version.
- More advanced 3D map and project visualization.
- More capable offline AI model as hardware compatibility permits.
- Deeper user-approved app automation and context-aware film companion.
- JARVIS should become more capable over time as real integrations are added and tested.

# 19. Explicit exclusions

- No smart-home control was requested in the earlier definitive scope.
- No emergency-call feature.
- No Termux, ADB, root, shell scripts or Termux:API requirement.
- No wrapper that merely launches ChatGPT.
- No secret camera/screen capture or covert recording.
- No sending messages, purchases, deleting data or sharing private information without confirmation.
- Do not label a simple prototype, wireframe, static graphic or incomplete source tree as a finished JARVIS.

# 20. Known project state — not equivalent to completion

The repository has had prototype work on:
- Kotlin + Jetpack Compose Android UI and animated HUD/core.
- Slovak Android speech recognition/TTS and an older one-command wake loop.
- Gemini-compatible AI settings/API key field and DuckDuckGo HTML search.
- Basic app/system settings intents and file/gallery/Instagram opening.
- Personal progress/check-in storage and dashboard.
- MapLibre/OpenFreeMap map prototype with 3D extruded buildings, search and route overlays.
- Basic wireframe project workspace.
- Experimental LiteRT-LM Qwen 0.6B offline model integration.

Known incomplete or unverified:
- Current wake loop previously armed only one command after wake phrase; this does not satisfy the latest continuous conversational session requirement.
- No verified Gmail OAuth/API integration.
- No verified music-service integration or exact favorites.
- No verified film/media-session/screen-analysis implementation.
- No verified full 3D editor/CAD-style voice editing.
- No verified full import of ChatGPT memories.
- Offline model API/build/download compatibility needs verification.
- No verified final APK or real-device acceptance test.

# 21. Acceptance criteria before calling it finished

1. Build a directly installable APK from the actual repository; verify artifact exists and installs.
2. Test on the target Android tablet and report the actual device/Android version.
3. First-run welcome and preloaded user-approved profile work.
4. Wake phrase starts session once; subsequent natural speech works without repeating it; stop phrase returns to standby.
5. Microphone state is visible, TTS does not trigger recognition, stop control works, lifecycle errors recover.
6. Slovak recognition and available male Slovak TTS voice tested and reported honestly.
7. Online AI and offline model tested separately; capability limitations stated.
8. Search opens useful sources and reports failures accurately.
9. Gmail OAuth, unread count, reading, drafting and confirmation-before-send tested.
10. Music presets and media playback tested with an actual supported service.
11. Film pause/resume and permitted scene analysis tested with an actual supported player; no hidden capture.
12. Map tiles, 3D buildings, search, route and location permission tested.
13. Project workspace supports real editable objects, voice edits, undo/redo and save/export; otherwise clearly label prototype.
14. Personal memory review/edit/export/delete and local-first behavior tested.
15. App lock, secret storage, permission controls and data deletion tested.
16. No unsupported feature is represented as implemented. Publish a feature matrix: working, requires account/permission, prototype, not implemented.

---

**Core product principle:** JARVIS should be a practical, Slovak-speaking, voice-first personal assistant and futuristic tablet command center that can genuinely perform supported actions, remember user-approved context, and clearly admit what it cannot yet do. The goal is the complete installable, tested app — not a document pretending to be the app.
