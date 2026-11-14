# JARVIS — standalone Android assistant

Native Android prototype written in Kotlin and Jetpack Compose. It does not require Termux, ADB, root, shell scripts, or Termux:API on the tablet.

## Implemented so far

- JARVIS launcher identity and dark sci-fi/cyan home screen, configured for tablet landscape orientation.
- Slovak Android speech recognition and text-to-speech.
- User-enabled Slovak voice service runs as a visible microphone foreground service. It keeps checking for “Hej Jarvis” / “Hey Jarvis” while the app UI is backgrounded; background commands are routed to a headless receiver so they are not discarded merely because the Activity is paused. Android speech-provider, battery-management and device-vendor restrictions can still stop recognition, so locked-screen reliability must be verified on the target tablet.
- Voice commands can be paused/resumed from the conversation, and recent chat history is stored locally on the device.
- Local commands: time, date, battery percentage, open YouTube, Android Settings, camera, Google search, create/list/delete notes, remember/forget preferences, clear chat history, arithmetic, set an alarm in the Clock app, create a calendar reminder, open an installed app by its visible name, dial a number (user confirms the call), timer via the Clock app, Wi-Fi settings/status, maps/navigation, browser, calculator (if installed), and open ChatGPT in the browser.
- Offline AI conversation using the bundled Qwen3 1.7B INT4 model through LiteRT-LM; optional online AI through a configurable OpenAI-compatible endpoint.
- Notification previews for Gmail, Messenger, Instagram, Facebook, WhatsApp, and EduPage through Android NotificationListener access. WhatsApp quick replies are sent only when Android exposes a reply action and only after explicit confirmation.
- Voice commands to read the latest notification previews by app, including EduPage grade alerts.
- Project workspace: AI-generated project brief, rotatable wireframe 3D primitives (cube, pyramid, house, sphere), finger-drawn canvas, and locally saved sketch.
- Interactive holographic map workspace using MapLibre and OpenFreeMap vector tiles: starts on a 3D globe, smoothly zooms into searched cities and streets, supports voice requests for the globe/street view and place search, and displays extruded 3D buildings where data is available. Also includes zoom/rotation/tilt, optional current location, and optional route drawing. Map tiles, search, location, and routes require internet and relevant permissions.
- API endpoint, model, and key settings; the key is not committed to the repository.
- GitHub Actions builds a debug APK and uploads it as a workflow artifact.

## Build and install

1. Open [GitHub Actions](https://github.com/pestasamuel843-byte/Jarvis/actions).
2. Open the newest successful **Android APK** run.
3. Download the `JARVIS-debug-APK` artifact and extract the ZIP.
4. Install `app-debug.apk` on the Android tablet. Android may ask to allow installation from the browser or file manager.

The APK is available only after the workflow succeeds. A green run is required before installation. The latest successful build is linked from the repository's Actions page; download the `JARVIS-debug-APK` artifact and extract the ZIP.

## Notification integrations

Open Android Settings from the notification-access prompt and explicitly enable JARVIS under notification access. Allow notifications for the source apps as well. Android and each source app control which message/grade preview text is visible. WhatsApp quick reply works only when its notification exposes a compatible reply action. For privacy, notification text is stored locally in JARVIS preferences.

## Offline AI brain

- On tablets reporting at least 8 GB of physical RAM, JARVIS selects Gemma 4 E2B, the stronger mobile-oriented model, and downloads it automatically on first launch (about 2.59 GB). The model is provided by LiteRT Community under Apache 2.0.
- The app checks available storage before downloading Gemma 4. The download requires internet once; after it is saved in app-private storage, text conversations can run offline without an API key.
- On devices below the 8 GB RAM threshold, or if the Gemma download fails, JARVIS falls back to the Qwen3 1.7B INT4 model already bundled in the APK (about 932 MB). The fallback is smaller and less capable.
- The APK still contains the Qwen fallback, so it remains large. Gemma 4 is downloaded separately rather than embedded in the APK, avoiding an additional 2.59 GB increase to the installer.
- LiteRT-LM 0.19.1 is used for the Gemma 4 runtime. The app attempts GPU inference and falls back to CPU if GPU initialization fails. Actual speed and stability depend on the tablet's chipset, memory and drivers.
- This is a local language model, not a ChatGPT-equivalent model. It can improve general conversation and reasoning, but it can still make mistakes and does not automatically gain control of apps or access to live information.
- The current chat integration sends text prompts to the model. Although the Gemma 4 model family has multimodal capabilities, image/audio input is not yet wired into JARVIS's chat UI.
- A successful APK build does not prove the model runs correctly on the target tablet. Device testing is still required.
## AI setup

Online AI is optional. The bundled offline model works without an API key. If you want to use online Gemini instead, open Settings inside JARVIS and paste a valid **Google Gemini API key** into the API-key field, then save. The default endpoint is Google's OpenAI-compatible Gemini endpoint and the default model is `gemini-2.5-flash`. Create/manage a key through Google AI Studio: https://aistudio.google.com/apikey. Availability, quotas, and pricing are controlled by Google and may change. Test the connection in JARVIS settings before relying on it. Never commit tokens to GitHub or send them in chat. Advanced settings are available if you deliberately use a different OpenAI-compatible provider.

## Psychological support mode

The AI system prompt now includes a dedicated, context-sensitive support approach for conversations about stress, sadness, relationships, family conflict, and emotional difficulties. It prioritizes listening, validation, careful questions, practical coping steps, and a calm tone. It explicitly prohibits pretending to be a licensed psychologist or making clinical diagnoses. For self-harm, suicide, violence, or immediate danger, it directs the assistant to encourage immediate help from a trusted adult or qualified professional and emergency services (112/155 in Slovakia). This is an AI conversation guideline, not a substitute for professional mental-health care.

## Current limitations — important

This is still an early prototype, not the full autonomous assistant described in the original brief. The latest source includes additional local voice commands, but each feature still needs testing on the target tablet.

- The app uses a user-enabled foreground microphone service for background/locked-screen wake-word listening and a separate headless command receiver. This is an implemented code path, not a guarantee that every Android speech provider or tablet firmware will keep it alive; real-device testing is still required. Continuous listening can consume battery.
- The offline 3D design view provides several rotatable wireframe primitives, not arbitrary AI-generated 3D models or a full CAD editor. The drawing canvas is a freehand sketch pad and saves locally.
- The 3D map extrudes mapped building footprints; it is not a complete photorealistic 3D city, AR overlay, or live traffic system. Map tiles and public geocoding/routing services require internet and have coverage/usage limits.
- AI project responses are displayed as text; the app does not yet compile generated source code or turn arbitrary prompts into complete interactive diagrams or 3D scenes. The 3D preview remains a small set of predefined wireframe primitives.
- The app cannot freely control all other apps, send messages without confirmation, or bypass Android permissions/background restrictions. Message reading is limited to notification previews exposed by each app; it is not full inbox access.
- EduPage integration currently reads available notification previews only. It does not log into the school account or retrieve the complete gradebook, averages, or historical grades. No EduPage password should be entered into JARVIS.
- The API key and recent chat history are stored in app preferences on the device. The key is not included in source control, but a production release should use encrypted storage or a secure backend.
- Build success does not substitute for installing and testing the app on the target tablet.

## Personal profiles and four-level protection modes

New source code adds:
- Locally stored, user-entered notes about family members and friends. Voice commands: `Zapamätaj si o Kim: dôverujem jej a vie ma vypočuť` and `Čo vieš o Kim?`.
- Explicitly entered health notes: `Zapamätaj si moje zdravie: ...`. These are personal notes, not diagnoses or a live Da Fit/Health Connect integration.
- Delete a person profile with `Zabudni profil Kim`; delete health notes with `Vymaž zdravotné poznámky`.
- Personal profiles and health notes stay out of online AI by default. To share them with the configured online provider, explicitly say `Povoľ zdieľanie osobného profilu online`; to revoke it, say `Zakáž zdieľanie osobného profilu online`.
- Four support levels: GUARDIAN, BLACKOUT, OMEGA and SOVEREIGN. Say `Aktivuj Guardian/Blackout/Omega/Sovereign`; phrases such as `Ja už to nedám` or `Prevezmi to celé` request SOVEREIGN. `Situácia vyriešená` returns to normal mode.
- Protection level is saved locally and influences the online/offline AI context and the app's main background/accent.
- Notification previews are checked for a short list of explicit threat phrases. A possible match raises BLACKOUT and asks the user to review the message; it does not establish that a threat is real and does not contact anyone.

Important limits:
- The app cannot know everything about the user automatically. People profiles must be entered by the user or imported from a file the user chooses. Recommendations about whom to contact are only as reliable as those notes.
- Health notes are manual. Da Fit watch data, Health Connect, sleep, heart rate and SpO₂ are not connected by this change.
- Threat phrase detection is deliberately narrow and can miss context or produce false positives. It is an alert for review, not a security guarantee.
- These changes do not yet silence Android system notifications, run a full autonomous agent, contact family/emergency services, or execute actions without confirmation.
- The visual protection state is a first implementation, not a complete redesign of every screen. No build or on-device test is claimed until the GitHub Actions build succeeds and the APK is installed on the target tablet.

## Technical details

- Kotlin, Jetpack Compose, Java 17, Android SDK 35.
- Minimum Android version: Android 8.0 (API 26).
- Application ID: `com.jarvis.assistant`.
