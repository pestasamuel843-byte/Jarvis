# JARVIS — Android assistant

Standalone Android application prototype built with Kotlin and Jetpack Compose. No Termux, ADB, root, or shell scripts are required on the tablet.

## Current features
- Dark sci-fi interface with cyan accents.
- Slovak text-to-speech output and speech recognition through Android's installed speech service.
- Time and date commands.
- Google web search commands: `hľadaj <výraz>` or `vyhľadaj <výraz>`.
- Local notes saved in app preferences: `poznámka <text>` or `zapíš <text>`.
- AI chat through a configurable OpenAI-compatible Chat Completions endpoint. You must provide your own API endpoint, model, and API key in Settings; usage may cost money.
- Project workspace with a finger-drawing canvas, opened with the PROJEKT button or by saying/typing a request containing “projekt” or “nakresli”.
- GitHub Actions workflow to build a debug APK and upload it as an artifact.

## Build
GitHub Actions runs on pushes to `main`. Open the [Actions page](https://github.com/pestasamuel843-byte/Jarvis/actions), select a successful **Android APK** run, and download the `JARVIS-debug-APK` artifact. Extract the ZIP and install the APK on the Android tablet. Android may ask you to allow installation from that browser/file manager.

## Important limitations
- This is an early prototype, not the complete autonomous assistant described in the original project brief.
- The wake phrase “Hej Jarvis” is **not** implemented as an always-on background listener. Press the microphone button to start speech recognition.
- The drawing canvas is temporary; it is not yet saved or converted into 3D models or production-ready project files.
- AI access requires a compatible endpoint and a valid API key. Do not commit API keys to this repository.
- The app does not have unrestricted control over other apps, messaging, camera, device settings, or background tasks. Android permissions and platform limits apply.
- A successful GitHub Actions build is still required before an APK can be installed.

## Technical details
- Kotlin, Android SDK 35, minimum Android 8.0 (API 26), Java 17, Jetpack Compose.
- Package: `com.jarvis.assistant`.
