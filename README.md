# JARVIS — standalone Android assistant

Native Android prototype written in Kotlin and Jetpack Compose. It does not require Termux, ADB, root, shell scripts, or Termux:API on the tablet.

## Implemented so far

- JARVIS launcher identity and dark sci-fi/cyan home screen.
- Slovak Android speech recognition and text-to-speech.
- Voice input can run a recognized command after you press the microphone.
- Removes the prefix “Hej Jarvis” from recognized text; this is **not** an always-listening wake-word service.
- Local commands: time, date, battery percentage, open YouTube, open Android Settings, open camera, Google search, notes, and Clock app timer.
- AI conversation through a configurable OpenAI-compatible Chat Completions endpoint, with recent conversation context.
- Project workspace: AI-generated project brief, draggable 3D wireframe cube, finger-drawn canvas, and locally saved sketch.
- API endpoint, model, and key settings; the key is not committed to the repository.
- GitHub Actions builds a debug APK and uploads it as a workflow artifact.

## Build and install

1. Open [GitHub Actions](https://github.com/pestasamuel843-byte/Jarvis/actions).
2. Open the newest successful **Android APK** run.
3. Download the `JARVIS-debug-APK` artifact and extract the ZIP.
4. Install `app-debug.apk` on the Android tablet. Android may ask to allow installation from the browser or file manager.

The APK is available only after the workflow succeeds. A green run is required before installation.

## AI setup

Open Settings inside JARVIS and provide an OpenAI-compatible Chat Completions endpoint, a model supported by that endpoint, and an API key. API usage may cost money. Do not commit API keys or secrets to GitHub.

## Current limitations — important

This is still an early prototype, not the full autonomous assistant described in the original brief.

- The phrase “Hej Jarvis” is not monitored continuously in the background. Press the microphone button to speak. Always-on wake-word detection needs a carefully permissioned foreground microphone service and device-specific testing.
- The 3D view is a draggable wireframe cube, not an AI-generated arbitrary 3D model. The drawing canvas is a freehand sketch pad.
- AI project responses are displayed as text; the app does not yet compile generated source code or turn arbitrary prompts into complete interactive diagrams or 3D scenes.
- The app cannot freely control all other apps, send messages without confirmation, or bypass Android permissions/background restrictions.
- The API key is stored in app preferences on the device. It is not included in source control, but a production release should use encrypted storage or a secure backend.
- Build success does not substitute for installing and testing the app on the target tablet.

## Technical details

- Kotlin, Jetpack Compose, Java 17, Android SDK 35.
- Minimum Android version: Android 8.0 (API 26).
- Application ID: `com.jarvis.assistant`.
