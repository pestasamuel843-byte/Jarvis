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
- First-run personalization must be substantive: known preferences, active projects, voice/language preferences and user-approved personal context should be loaded into a private local profile before the first normal conversation. Do not display a blank “tell me about yourself” chatbot.
- First-run profile should be reviewable and editable by voice. If a detail is uncertain or only a past idea, label it as such instead of treating it as fact.

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
- Offline model support requested at high capability, not just the prototype Qwen 0.6B model. Select the strongest model that actually fits the tablet after measuring hardware; see the dedicated offline-brain requirements below.
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



# 8A. Voice-controlled app locking

- User can say “Jarvis, zamkni apky” to activate the app-lock feature for the applications the user has selected.
- The user can configure which apps are protected and choose a PIN or supported biometric authentication. JARVIS must never invent or silently choose the PIN.
- Where Android supports it, use an approved app-lock mechanism or a transparent, user-enabled accessibility/overlay approach that does not bypass Android security. Explain device/version limitations.
- When a protected app is opened, require the configured authentication before allowing access, to the extent technically supported. Do not claim system-wide app locking if Android does not permit it.
- Voice commands may lock the configured apps, but must not expose or speak the PIN. Unlocking sensitive apps should require the user's configured secure authentication, not merely a voice match.
- Include “Jarvis, odomkni apky” only as a request to begin the normal secure unlock flow; it must not bypass authentication.
- Provide an immediate manual fallback, recovery path and a way to disable the feature in settings.

# 5A. User-directed learning and knowledge updates

- If JARVIS does not know something, the user can say “Jarvis, douč sa to” or “zisti si to a zapamätaj”.
- JARVIS should clarify what topic or skill the user means if the reference is ambiguous, then use permitted sources (web, user-provided documents or examples, and approved local files) to learn the information.
- It must distinguish between learning a fact, learning a procedure, and acquiring a genuinely new technical capability. Reading about a capability does not automatically implement that capability.
- After researching, JARVIS should summarize what it learned, cite or retain source references where possible, identify uncertainty and ask whether the user wants the useful result saved to its local knowledge/memory.
- Saved knowledge must be reviewable, editable and deletable. Respect source licences, privacy, and access permissions.
- JARVIS must not silently rewrite its own application code, change security settings, install software, grant permissions, or modify its underlying model. Any code change, installation, or consequential configuration change requires explicit user approval and validation.
- Use retrieval from approved local knowledge and sources to improve later answers. Do not claim that the underlying AI model has permanently retrained itself when it has only stored notes or indexed documents.
- If the user teaches JARVIS a specific preference or procedure, it may save that instruction after confirming the intended meaning.

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
- Dashboard should show useful personal information without cluttering every item into boxes.
- School timetable setup/editing was also discussed in the prototype.
- No invented marks, mood scores or school schedule.

# 13. Maps and navigation

- User explicitly wants a custom JARVIS map, not simply Google Maps embedded as the entire experience.
- Futuristic 3D map with buildings, streets and local/project-style visuals.
- Tilt, rotate, zoom, search, location when permission is granted, routes and useful overlays.
- Buildings should be represented as 3D extrusions derived from real footprints and available height data. Do not call this photorealistic. Never invent missing streets/buildings or present approximate geometry as verified.
- Map should integrate into the full dashboard and project workspace.\n- Real data sources, attribution, freshness and uncertainty must be visible where relevant.
- User wants map visuals detailed enough to feel like a real interactive map, not a static image or random drawing.

# 13A. Real-world map accuracy — non-negotiable requirement

- The map must represent the real world, not an invented or fictional city. This is a foundational correctness requirement.
- Use real geographic coordinates and identifiable, real-world map datasets for roads, street names, addresses, places and building footprints wherever available.
- Use a real routing engine/network graph for routes. Never draw a plausible-looking route that has not been calculated from actual road/path data.
- Use GPS/device location only after explicit permission; show location accuracy and timestamp where available. Never imply exact positioning when only an approximate location is available.
- 3D building geometry must be derived from real building footprints and available height/elevation data. If height or geometry is unavailable, show a clearly labelled approximate/unknown representation or omit the extrusion; never fabricate it as verified.
- Real satellite/aerial imagery may be shown only from a legitimate imagery provider and must be labelled with its source/date where available. Do not present generated imagery as a real satellite view.
- Every map layer must distinguish verified provider data, approximate data and unavailable data. Missing data must remain missing or be clearly marked; do not fill gaps with invented streets, buildings, labels, POIs or live traffic.
- Display data source/attribution and respect provider licensing, usage limits, caching and offline-use rules.
- Map search results, addresses, route status, traffic and opening information must come from actual providers and include freshness/availability limitations.
- If the network or provider fails, show the last valid data with its age or an explicit unavailable state. Do not silently replace real data with a fictional map.
- Acceptance tests must compare selected streets, building footprints, addresses and routes against trusted real-world references in the target area.

# 13B. Expanded real-world sensing and spatial understanding

- Where compatible sensors and permissions exist, JARVIS may combine camera, microphone, GPS, motion sensors, depth sensors and other supported inputs to understand the user's explicitly selected context.
- Explain which sensors are active, what they are used for, and whether processing is local or sent to a service.
- Detect and describe visible objects or environmental changes only within the confidence of the actual sensor data. Distinguish detection from identification and inference.
- Support spatial reconstruction of a room or object into a measurable 3D representation when supported by camera/depth hardware and a suitable reconstruction pipeline. Show measurement uncertainty; do not invent hidden geometry.
- Analyse objects and materials visually as estimates, not certainty. State when a material cannot reliably be identified from an image.
- Support satellite, aerial, elevation and geospatial datasets only through legitimate available providers; these are not live omniscient views and may be delayed or incomplete.
- No covert sensing, recording or background collection. Sensor use must be explicit, visible and stoppable.

# 13C. Technical operations, diagnostics and robotics

- Provide a technical diagnostics module for compatible devices and systems: collect available telemetry, detect abnormal values, explain likely causes and recommend safe checks. Never claim diagnostics for hardware that exposes no data.
- Add condition monitoring and early-warning analysis where a supported device provides reliable telemetry and sufficient history. Label predictions as estimates with confidence and evidence; do not present guesses as certain failures.
- Support monitoring of user-authorized technical infrastructure and connected devices through documented APIs/protocols. Display connection state, data freshness and failures honestly.
- Add a simulation/test mode for supported project types. Run calculations or simulations using a real, named engine/model and expose assumptions, inputs and limits. A visual animation alone is not an engineering validation.
- Add coordination of compatible robots or robotic devices only through documented, permissioned interfaces, with a clear manual stop and safety limits. Do not imply that the tablet can control arbitrary robots.
- Keep these capabilities modular and optional; they must not be represented as working until an actual compatible device/service and end-to-end test are available.

# 13D. Augmented reality and information in the user's field of view

- User explicitly wants information displayed directly in front of the user's eyes, inspired by Tony Stark's heads-up/augmented-reality displays.
- Plan a future AR/heads-up-display mode for compatible smart glasses or other supported wearable displays. The tablet alone cannot project graphics into a person's vision.
- Display context-relevant, readable overlays such as navigation directions, object labels, selected measurements, notifications, system status and assistant responses, subject to the wearable's capabilities.
- Provide voice-first interaction; do not require constant touch or obscure the user's real-world view.
- Include privacy controls, clear recording/camera indicators where applicable, accessibility/readability settings and a fast way to dismiss the overlay.
- If no compatible glasses/display are connected, provide a clearly labelled AR preview on the tablet only; never claim that it is appearing in the user's real field of view.
- Do not include Iron Man suit monitoring, suit diagnostics or suit control; these are explicitly excluded from the user's scope.

# 14. Project workspace, drawing and 3D creation

- A real project mode / workspace is required.
- User wants to describe what to build by voice and see the result in a 2D or 3D workspace.
- Support cars, buildings, rooms, devices, maps, streets, objects and other project concepts.
- Voice editing examples: change car, add lights, add spoiler; modify dimensions/materials; undo/redo.
- Actual editable objects, dimensions, materials, layers, selection, transform, save/load, undo/redo and export are desired.
- 2D drawing and 3D preview should be available; code and preview/workspace should work together.
- User described a split interface with chat on the left and workspace on the right, separated by a divider. In project mode, side panels should slide away or workspace should take over more of the screen.
- Use a real 3D engine/scene where practical (Three.js/WebView or native equivalent were discussed), with a clear distinction between concept preview and full CAD functionality.
- A wireframe primitive demo is not a finished 3D editor.
- **NEW — voice-only project creation and editing is a core requirement:** when the user enters project mode, the assistant must accept spoken descriptions and carry out the project work itself. The user does not want to type prompts, code, dimensions or instructions, and does not want to click tools, menus, object handles or drawing controls to create or modify a project.
- The expected workflow is: user says what to create/change → JARVIS interprets it → workspace updates visibly → JARVIS describes what it changed. Examples: “Navrhni športové auto”, “Daj mu spojler”, “Zmeň farbu na tmavomodrú”, “Pridaj LED svetlá”, “otoč ho”, “ukáž mi ho spredu”, “vráť poslednú zmenu”, “ulož projekt”.
- JARVIS must handle follow-up edits in context without requiring the user to repeat the whole project description.
- Voice commands must cover creating, selecting, moving, rotating, resizing, recoloring, adding/removing components, changing view, undo/redo, naming, saving, reopening and exporting projects wherever the underlying editor supports them.
- No typing or clicking should be necessary for normal project creation and editing. Touch may remain as an optional fallback, but it must not be the expected workflow.
- If a requested change is ambiguous, JARVIS should ask one short spoken clarification. For destructive or irreversible actions, it should ask for confirmation.
- The workspace should show the result while the user speaks and keep a visible, uncluttered, full-screen project view. The assistant should not make the user operate a conventional desktop-style CAD interface.
- The system must not pretend voice-only editing works until actual end-to-end commands have been implemented and tested.

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
- **NEW — strict visual direction:** the app must look like a serious, premium, futuristic JARVIS command system, not a children's app, toy, cartoon, generic chatbot, colourful school app or ordinary Android dashboard.
- Do not add decorative features, gamification, emoji-heavy panels, random cards, childish mascots, oversized rounded buttons or unrelated widgets. Show only the functions and information that serve the requirements in this specification.
- Avoid a generic chat screen as the main experience. The central reactor/HUD and operational dashboard are the visual identity; conversation is integrated into the interface.
- Use restrained dark surfaces, precise alignment, fine technical lines, subtle cyan/blue illumination, readable typography and deliberate motion. Keep effects polished rather than noisy.
- In project mode, collapse or move aside irrelevant dashboard panels and give the editable 2D/3D workspace the available screen area. Keep a small voice/status indicator and conversational feedback visible.
- Visual quality is an acceptance criterion: a technically working app with a childish or generic interface does not meet the user's request.

# 15A. Offline AI brain — high-priority requirement

- User explicitly wants a substantial offline AI model, regardless of download size, as long as it can converse in Slovak, understand context, write and create useful content, help build projects, and work without a paid API key.
- Do not limit the design to the current tiny 0.6B experimental model. Evaluate the largest practical model(s) that can run on the actual tablet, including quantized models and memory/storage constraints. Do not promise that a huge model will run smoothly until device RAM, storage, CPU/GPU/NPU and runtime compatibility are measured.
- Prefer a model manager that can download a selected model with clear size, storage estimate, license, progress, pause/resume, integrity check, and delete/replace controls. The user should not need to manually handle model files.
- Offline brain must support Slovak conversation, drafting/writing, summarizing locally available documents, reasoning within its actual capability, and generating project plans/code or structured scene edits for the workspace.
- Keep a clear distinction between language-model reasoning and actual app tools. The model can propose an action; only the validated Android integration performs it.
- Offline AI can summarize email already synchronized to the device, but receiving new Gmail messages, searching the live inbox and sending mail require internet and an authorized Gmail connection. Do not imply that an offline model alone can access Gmail.
- If a larger model exceeds device capacity, offer the strongest compatible model and explain the measured limitation; do not silently fall back to a much weaker model without telling the user.
- Keep private profile and personal memory local by default. Do not send camera frames, mail contents or private memories to cloud AI unless the user explicitly enables it.

# 16. Camera-based person/activity alerts

- User wants to be able to deliberately connect or select a camera, for example one pointed at a staircase or veranda, and receive spoken alerts while using another app or watching a film.
- Example alerts: “Pane, niekto ide po schodoch”; “Pane, prišla osoba na verandu.” Alerts should be concise and not repeatedly trigger for the same continuous event.
- The system may detect a person, movement, direction/zone crossing and approximate approach based on the camera's field of view and configured zones. It must not claim an exact arrival countdown unless the camera placement and measured movement genuinely support a defensible estimate. Prefer “osoba sa približuje” over invented precise minutes.
- Camera operation must be explicitly configured and enabled by the user, visibly indicated, and easy to pause or stop. No hidden camera activation, covert recording, or secret uploads.
- Process video locally where technically feasible. Do not save footage or snapshots by default; make retention settings explicit if recording is ever added.
- Do not identify a family member by face by default. A “sister arrived” identification feature would require informed, explicit consent from the person being enrolled and any other people subject to recognition, secure local storage of biometric templates, a deletion mechanism, and compliance with applicable law. If consent or reliable recognition is absent, say “známa osoba” only if a consented profile is available, otherwise simply “osoba”.
- Never infer a person's identity from appearance, guess a relationship, or announce “your sister” without a user-confirmed and consented identity profile.
- Handle poor lighting, occlusion, pets, shadows, camera disconnection and false detections with confidence thresholds and cautious wording. Provide a quiet-hours / alert sensitivity setting.
- The feature must be tested with the actual camera and real environment before being represented as reliable. A camera stream or simple motion detector alone is not a dependable person-recognition or arrival-time system.



# 16A. Camera-based room and object scanning — explicit user workflow

- User can pick up the tablet, open JARVIS scan mode, point its camera at a room or object, and move the tablet around it to capture the visible space from multiple angles.
- JARVIS should guide the user with spoken prompts such as “posuň kameru doľava”, “ukáž ešte túto stenu” or “chýba mi pohľad na roh”.
- Use an actual supported spatial-reconstruction pipeline (for example, ARCore Depth/scene APIs where the device supports them, or another validated photogrammetry/SLAM pipeline). Do not fake a 3D scan by placing generic boxes.
- Reconstruct only geometry supported by captured images/depth data. Show confidence/measurement uncertainty; label estimated dimensions as estimates.
- Detect and label visible surfaces and objects only where the model is sufficiently confident. Do not claim to see behind closed doors, inside objects, or around unseen corners.
- Provide a navigable 3D result that can be rotated, measured where supported, saved, reopened and exported. Let the user correct/remove inaccurate objects by voice.
- If the tablet camera or sensors cannot provide depth/scale, explain that exact measurements are unavailable and offer a photo-based approximate model clearly labelled as approximate.
- Camera must be visibly active during scanning, processing should be local where feasible, and the user can pause/stop and delete the scan. Do not retain raw video by default.

# 16B. Conversation Coach mode (user phrase may be configurable)

- Add an optional mode for helping the user participate in important conversations. The user may choose a custom spoken activation phrase in settings; “režim špión” can be configured as a personal shortcut name, but it must not enable covert surveillance or hidden recording.
- When enabled, JARVIS can provide short, private-to-the-user suggestions through a connected earbud/headset, such as relevant follow-up questions, ways to clarify a point, polite responses, reminders of details the user has chosen to remember, or a prompt when the user appears unsure what to say.
- The mode must be based on a conversation that participants know is being assisted/analysed and have agreed to. Microphone/camera use must be clearly indicated; no hidden listening, secret recording, covert app-wide monitoring or undisclosed streaming.
- Prefer on-device speech processing and transient context. Do not store audio or video by default. Do not send conversation content to cloud AI unless the user and participants have been appropriately informed and the processing is permitted.
- JARVIS must not infer a person's private thoughts, honesty, personality, intentions, health, or trustworthiness from facial expressions, voice, or gestures. It may describe observable, non-sensitive facts cautiously (for example, “hovorí rýchlejšie” only when genuinely measurable) but must not present them as psychological conclusions.
- The assistant should speak discreetly through the user's selected audio output at user-configurable intervals, with a quick “stíš”, “pauza” and “ukonči režim” command. It must not interrupt the conversation with long unsolicited commentary.
- If the user asks for help because they do not know what to say, offer context-sensitive neutral prompts; never manipulate, deceive, pressure or impersonate the user.
- If the user says they may be in immediate danger, prioritize simple safety guidance: help them move toward a safer public place or trusted person, offer to call a trusted contact/emergency services only with clear user confirmation where appropriate, and stop unnecessary analysis. Do not promise that camera-based danger prediction is reliable.
- A locked-tablet activation phrase may work only if Android permits a user-visible, foreground microphone service and the required permissions are granted. If the OS blocks background microphone or wake-word operation, JARVIS must explain this and offer a safe fallback; it must not bypass the lock screen or Android privacy indicators.
- Do not automatically unlock the tablet, inspect every app, bypass app security, or gain access to private app content. App integrations require explicit authorization and supported APIs.

# 17. Camera and visual understanding

- Camera access only when the user explicitly configures and enables a camera feature; staircase/veranda person-activity alerts are an explicit requested use case.
- Provide camera guidance/visual analysis when explicitly requested.
- No always-on camera, no secret recording and no hidden image storage.
- Screen sharing/film visual understanding is separate from camera permission and must also be explicit and visible.

# 18. Privacy, security and reliability

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

# 19. Future / exploratory ideas

These were mentioned as future ambitions, not necessarily launch blockers:
- Support for smart glasses / wearable interface in a later version.
- More advanced 3D map and project visualization.
- More capable offline AI model as hardware compatibility permits.
- Deeper user-approved app automation and context-aware film companion.
- JARVIS should become more capable over time as real integrations are added and tested.

# 20. Explicit exclusions

- No smart-home control was requested in the earlier definitive scope.
- No emergency-call feature.
- No Termux, ADB, root, shell scripts or Termux:API requirement.
- No wrapper that merely launches ChatGPT.
- No secret camera/screen capture or covert recording. Person/activity alerts must be transparent, locally processed where feasible, and consent-aware.
- No sending messages, purchases, deleting data or sharing private information without confirmation.
- Do not label a simple prototype, wireframe, static graphic or incomplete source tree as a finished JARVIS.
- Do not substitute typing and clicking for the requested voice-first project workflow.
- Do not substitute a childish or generic UI for the requested cinematic, professional JARVIS dashboard.

# 21. Known project state — not equivalent to completion

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

# 22. Acceptance criteria before calling it finished

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
13. Project workspace supports real editable objects, voice edits, undo/redo and save/export; normal project workflow must not require typing or clicking. Otherwise clearly label prototype.
14. Personal memory review/edit/export/delete and local-first behavior tested.
15. App lock, secret storage, permission controls and data deletion tested.
16. UI passes visual review for premium futuristic JARVIS styling; no childish, generic or unrelated UI elements.
17. Offline model size/capability is benchmarked on the target tablet; Slovak conversation and local creation tasks are tested without an API key.\n18. Camera alerts are tested with consent and visible activation; false alarms and limitations are documented.\n19. No unsupported feature is represented as implemented. Publish a feature matrix: working, requires account/permission, prototype, not implemented.

---

**Core product principle:** JARVIS should be a practical, Slovak-speaking, voice-first personal assistant and futuristic tablet command center that can genuinely perform supported actions, remember user-approved context, and clearly admit what it cannot yet do. The goal is the complete installable, tested app — not a document pretending to be the app.
