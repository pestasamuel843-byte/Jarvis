# Stanford OpenJarvis adaptation for the native Android JARVIS

Reference: https://github.com/open-jarvis/OpenJarvis  
Documentation: https://open-jarvis.github.io/OpenJarvis/  
License: Apache-2.0. This Android implementation uses the architectural ideas; it does not copy or bundle Stanford's Python/Rust runtime.

## What has now been implemented in this branch

The first implementation slice moves the existing Android app/search command handling behind a small, explicit Kotlin skill architecture:

- `JarvisSkill` defines a typed capability contract and structured outcomes: success, failure, or confirmation required.
- `JarvisSkillDescriptor` declares each capability's identifier, purpose, Android permissions, network requirement, and confirmation policy.
- `JarvisSkillRegistry` is an allow-list of compiled-in capabilities. It does not download or execute arbitrary third-party code.
- `AppSearchSkill` handles app launching and web/YouTube search as one narrow deterministic capability.
- `JarvisOrchestrator` selects and executes the registered skill and returns its structured result to the existing `MainActivity` entry point through `JarvisAgent`.
- `JarvisTraceStore` stores only local execution metadata (skill ID, status, timestamp, duration), capped at 100 events. It does not store recognized phrases, message contents, notification text, prompts, or microphone audio.

This is a source-code change on the `openjarvis-adaptation` branch. It is not yet a verified APK or a tablet-tested release.

## OpenJarvis concepts adapted

Stanford's OpenJarvis v1.0 describes five composable primitives: Intelligence, Engine, Agents, Tools & Memory, and Learning. It also includes local-first routing, multiple agent execution modes, scheduled/continuous agents, local traces, and evaluation gates.

Android adaptation:

1. **Intelligence and engine routing:** keep the existing optional LiteRT-LM local model in `OfflineBrain.kt`; deterministic device actions should run in Kotlin before invoking a language model. A cloud model remains optional and must never silently receive local memory or notification content.
2. **Agents and tools:** use the new typed Kotlin skill contract, allow-list registry, and orchestrator. Future message-sending or account-changing tools must return `NeedsConfirmation` until the user explicitly approves.
3. **Memory:** keep user memory in app-private local storage and only provide relevant excerpts to the model. Do not upload it by default.
4. **Learning and traces:** keep content-free local execution traces and add a repeatable Slovak command test suite before changing routing behavior. Do not train on private conversations automatically.
5. **Scheduling:** use Android WorkManager for future scheduled summaries and reminders. Scheduled work must not secretly activate the microphone.

## Important Android constraints

- Keep the app native Kotlin/Jetpack Compose.
- Do not require Termux, root, ADB, an Ollama server, or a personal API key.
- Stanford's Python SDK, CLI, Docker/Podman sandbox, desktop app, and Linux/GPU engines are not directly portable to an Android tablet. Reuse the architectural patterns, not those runtimes.
- LiteRT-LM is already present in this Android project as an optional on-device model runtime; actual speed and memory use must be checked on the target tablet.
- Web search and live remote information need internet access. Local conversation can work offline only when a compatible model is installed.
- Android accepting a launch intent is not proof that the target app fully loaded or completed a task.
- A foreground microphone service is not guaranteed always-on wake-word detection on every Android device.

## Safe future self-upgrade design

JARVIS should not let an AI model rewrite and install arbitrary executable code on the tablet. A future upgrade flow should:

1. Detect a new version from a trusted release source.
2. Verify release authenticity and APK signing identity.
3. Run compatibility and regression checks before offering the update.
4. Explain the changes and request user approval before installation.
5. Keep a rollback path where Android's installation/update model permits it.

The local learning loop may recommend code/configuration changes from test results, but changes should be reviewed and shipped as a signed app release—not silently self-modified at runtime.

## Next implementation order

1. Add unit tests for Slovak intent parsing and structured skill outcomes.
2. Extend the registry with narrowly scoped tools (battery/status, reminders, safe read-only information).
3. Add explicit confirmation gates for any outbound message or sensitive action.
4. Add local-first model routing between deterministic Kotlin actions and the existing LiteRT-LM model.
5. Add opt-in WorkManager routines and privacy-preserving regression evaluation.
6. Only after these foundations, build a signed, user-approved update channel.

## Primary Stanford sources

- Repository and release history: https://github.com/open-jarvis/OpenJarvis
- Architecture overview: https://open-jarvis.github.io/OpenJarvis/architecture/overview/
- Design principles: https://open-jarvis.github.io/OpenJarvis/architecture/design-principles/
- Changelog: https://github.com/open-jarvis/OpenJarvis/blob/main/CHANGELOG.md
- Research paper: https://arxiv.org/abs/2605.17172
