## Summary
- Keep the native Kotlin/Jetpack Compose Android app and the OpenJarvis-inspired skill/orchestrator architecture.
- Replace the continuous SpeechRecognizer wake loop with a streaming AudioRecord microphone pipeline and sherpa-onnx KeywordSpotter.
- Start Android's on-device Slovak SpeechRecognizer only after the local wake detector recognizes the wake phrase.
- Download the sherpa-onnx 2.0.1 Android AAR during Gradle pre-build and verify its published SHA-256 before packaging.
- Download the compact English keyword-spotting model once into app-private storage; no API key or cloud speech endpoint is used.
- Preserve the local Qwen3 conversation model, notification previews, daily briefing, and local-only speech/TTS policies.

## Voice limitations that still need validation
- The pretrained GigaSpeech wake model is English-only. The currently configured wake phrase is “Hey Jarvis”; reliable Slovak “Nazdar Jarvis” needs a Slovak-trained model or a custom-trained wake model.
- The spoken command is still transcribed by Android's on-device Slovak recognizer, so the device must provide the offline Slovak language pack.
- First use needs internet to fetch the AAR at build time and the compact wake model at runtime. No personal API key is needed.
- Screen-off listening requires the user to start the microphone foreground service while the app is visible and Android to keep it alive. It cannot listen while the tablet is powered off or after the service is force-stopped.
- The wake engine change is source code only until CI passes and the app is tested on the target tablet. Do not treat it as a verified APK yet.

## Test gate
1. CI must resolve the AAR, compile Kotlin, and assemble debug/release APKs.
2. Test wake detection in a quiet room and normal household noise.
3. Verify screen-off wake, pause/resume during TTS, no self-triggering, and battery consumption on the actual tablet.
4. Verify Slovak command transcription with the offline Slovak language pack.
5. Only then publish a signed APK.