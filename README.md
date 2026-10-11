# JARVIS — standalone Android assistant

Native Android prototype written in Kotlin and Jetpack Compose. It does not require Termux, ADB, root, shell scripts, or Termux:API on the tablet.

## Implemented so far

- JARVIS launcher identity and dark sci-fi/cyan home screen.
- Slovak Android speech recognition and text-to-speech.
- While the main screen is open, cycles Android speech recognition looking for “Hej Jarvis” / “Hey Jarvis”; say the command after the wake phrase or in the next utterance. This is not guaranteed to work with the screen locked or in the background.
- Voice commands can be paused/resumed from the conversation, and recent chat history is stored locally on the device.
- Local commands: time, date, battery percentage, open YouTube, Android Settings, camera, Google search, create/list/delete notes, remember/forget preferences, clear chat history, arithmetic, set an alarm in the Clock app, create a calendar reminder, open an installed app by its visible name, dial a number (user confirms the call), timer via the Clock app, Wi-Fi settings/status, maps/navigation, browser, calculator (if installed), and open ChatGPT in the browser.
- AI conversation through a configurable OpenAI-compatible Chat Completions endpoint, with recent conversation context.
- Project workspace: AI-generated project brief, rotatable wireframe 3D primitives (cube, pyramid, house, sphere), finger-drawn canvas, and locally saved sketch.
- Interactive map workspace using OpenStreetMap/CARTO tiles, place search, zoom/pan, optional current location, and optional route drawing with distance/time estimates. Map requests can open the map workspace directly.
- API endpoint, model, and key settings; the key is not committed to the repository.
- GitHub Actions builds a debug APK and uploads it as a workflow artifact.

## Build and install

1. Open [GitHub Actions](https://github.com/pestasamuel843-byte/Jarvis/actions).
2. Open the newest successful **Android APK** run.
3. Download the `JARVIS-debug-APK` artifact and extract the ZIP.
4. Install `app-debug.apk` on the Android tablet. Android may ask to allow installation from the browser or file manager.

The APK is available only after the workflow succeeds. A green run is required before installation. The latest successful build is linked from the repository's Actions page; download the `JARVIS-debug-APK` artifact and extract the ZIP.

## AI setup

Open Settings inside JARVIS and provide an OpenAI-compatible Chat Completions endpoint, a model supported by that endpoint, and an API key. API usage may cost money. Do not commit API keys or secrets to GitHub.

## Current limitations — important

This is still an early prototype, not the full autonomous assistant described in the original brief. The latest source includes additional local voice commands, but each feature still needs testing on the target tablet.

- The app cycles Android SpeechRecognizer while its main screen is open. This is not a reliable background/locked-screen wake-word service; recognition availability and offline behavior depend on the Android speech provider. It may consume battery.
- The 3D view provides several rotatable wireframe primitives, not arbitrary AI-generated 3D models or a full CAD editor. The drawing canvas is a freehand sketch pad.
- The map is a browsable 2D map, not an AR/holographic 3D city model. Route estimates depend on public geocoding/routing services and do not include live traffic.
- AI project responses are displayed as text; the app does not yet compile generated source code or turn arbitrary prompts into complete interactive diagrams or 3D scenes. The 3D preview remains a small set of predefined wireframe primitives.
- The app cannot freely control all other apps, send messages without confirmation, or bypass Android permissions/background restrictions.
- The API key and recent chat history are stored in app preferences on the device. The key is not included in source control, but a production release should use encrypted storage or a secure backend.
- Build success does not substitute for installing and testing the app on the target tablet.

## Technical details

- Kotlin, Jetpack Compose, Java 17, Android SDK 35.
- Minimum Android version: Android 8.0 (API 26).
- Application ID: `com.jarvis.assistant`.
