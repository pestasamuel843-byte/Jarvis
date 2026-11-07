# JARVIS — standalone Android assistant

Native Android prototype written in Kotlin and Jetpack Compose. It does not require Termux, ADB, root, shell scripts, or Termux:API on the tablet.

## Implemented so far

- JARVIS launcher identity and dark sci-fi/cyan home screen, configured for tablet landscape orientation.
- Slovak Android speech recognition and text-to-speech.
- While the main screen is open, cycles Android speech recognition looking for “Hej Jarvis” / “Hey Jarvis”; say the command after the wake phrase or in the next utterance. This is not guaranteed to work with the screen locked or in the background.
- Voice commands can be paused/resumed from the conversation, and recent chat history is stored locally on the device.
- Local commands: time, date, battery percentage, open YouTube, Android Settings, camera, Google search, create/list/delete notes, remember/forget preferences, clear chat history, arithmetic, set an alarm in the Clock app, create a calendar reminder, open an installed app by its visible name, dial a number (user confirms the call), timer via the Clock app, Wi-Fi settings/status, maps/navigation, browser, calculator (if installed), and open ChatGPT in the browser.
- AI conversation through a configurable OpenAI-compatible Chat Completions endpoint, with recent conversation context.
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

## AI setup

Open Settings inside JARVIS and paste a valid **Google Gemini API key** into the API-key field, then save. The default endpoint is Google's OpenAI-compatible Gemini endpoint and the default model is `gemini-2.5-flash`, so the intended basic setup requires only the Gemini API key. Create/manage a key through Google AI Studio: https://aistudio.google.com/apikey. Availability, quotas, and pricing are controlled by Google and may change. Test the connection in JARVIS settings before relying on it. Never commit tokens to GitHub or send them in chat. Advanced settings are available if you deliberately use a different OpenAI-compatible provider.

## Psychological support mode

The AI system prompt now includes a dedicated, context-sensitive support approach for conversations about stress, sadness, relationships, family conflict, and emotional difficulties. It prioritizes listening, validation, careful questions, practical coping steps, and a calm tone. It explicitly prohibits pretending to be a licensed psychologist or making clinical diagnoses. For self-harm, suicide, violence, or immediate danger, it directs the assistant to encourage immediate help from a trusted adult or qualified professional and emergency services (112/155 in Slovakia). This is an AI conversation guideline, not a substitute for professional mental-health care.

## Current limitations — important

This is still an early prototype, not the full autonomous assistant described in the original brief. The latest source includes additional local voice commands, but each feature still needs testing on the target tablet.

- The app cycles Android SpeechRecognizer while its main screen is open. This is not a reliable background/locked-screen wake-word service; recognition availability and offline behavior depend on the Android speech provider. It may consume battery.
- The offline 3D design view provides several rotatable wireframe primitives, not arbitrary AI-generated 3D models or a full CAD editor. The drawing canvas is a freehand sketch pad and saves locally.
- The 3D map extrudes mapped building footprints; it is not a complete photorealistic 3D city, AR overlay, or live traffic system. Map tiles and public geocoding/routing services require internet and have coverage/usage limits.
- AI project responses are displayed as text; the app does not yet compile generated source code or turn arbitrary prompts into complete interactive diagrams or 3D scenes. The 3D preview remains a small set of predefined wireframe primitives.
- The app cannot freely control all other apps, send messages without confirmation, or bypass Android permissions/background restrictions. Message reading is limited to notification previews exposed by each app; it is not full inbox access.
- EduPage integration currently reads available notification previews only. It does not log into the school account or retrieve the complete gradebook, averages, or historical grades. No EduPage password should be entered into JARVIS.
- The API key and recent chat history are stored in app preferences on the device. The key is not included in source control, but a production release should use encrypted storage or a secure backend.
- Build success does not substitute for installing and testing the app on the target tablet.

## Technical details

- Kotlin, Jetpack Compose, Java 17, Android SDK 35.
- Minimum Android version: Android 8.0 (API 26).
- Application ID: `com.jarvis.assistant`.
