# OpenJarvis ideas adapted for the Android JARVIS

This document records an Android-specific adaptation of ideas from Stanford's OpenJarvis. It is a design plan, not a claim that these features have already been implemented or tested.

Reference project: https://github.com/open-jarvis/OpenJarvis  
License: Apache-2.0 (review the license and preserve required notices before copying any source code).

## What to borrow conceptually

### 1. Local-first model routing
OpenJarvis is designed to run locally by default and use cloud models only when useful. JARVIS already has an optional LiteRT-LM on-device Qwen model in `OfflineBrain.kt` and an OpenAI-compatible cloud endpoint.

Adaptation:
- Route deterministic device commands to Kotlin handlers first; do not spend model tokens to open an app, read the battery, or set a timer.
- Use the on-device model for simple conversation, local-memory recall, and offline explanations.
- Use the configured cloud model only when internet is available and the user has configured it; never silently upload local memory or notification text.
- Tell the user when a task is limited by the small offline model instead of pretending it has cloud-level reasoning.
- Keep the current standalone Android app; do not import Stanford's Python/Rust desktop runtime or require Ollama/Termux.

### 2. Orchestrator with explicit tool selection
OpenJarvis separates an agent/orchestrator from the tools it can invoke.

Adaptation:
- Introduce a small Kotlin task planner that converts a request into typed steps such as `ReadNotifications`, `OpenApp`, `SearchWeb`, `CreateReminder`, or `DraftMessage`.
- Execute only registered Android tools. Each tool returns a structured result: success, failure, or needs-confirmation.
- Verify the actual Android result before JARVIS says an action succeeded.
- Ask for confirmation before sending messages, placing calls, deleting data, or changing account/security settings.
- Add loop limits and timeouts so a misunderstood voice command cannot repeat actions indefinitely.

### 3. Skills registry with permission boundaries
OpenJarvis uses discoverable skills to extend what an agent can do.

Adaptation:
- Store JARVIS skills as local, versioned capability definitions (for example: school notifications, maps, reminders, media control, and 3D workspace).
- Each skill declares required Android permissions, supported commands, whether internet is required, and which actions need confirmation.
- Keep skill definitions separate from the main Compose UI so new capabilities do not turn `MainActivity.kt` into a larger monolith.
- Do not download and execute arbitrary community code on the tablet. Imported skills must be declarative and reviewed.

### 4. Local trace and evaluation loop
OpenJarvis treats evaluations and execution traces as core engineering features.

Adaptation:
- Record compact local events: recognized intent, selected tool, result, duration, and whether the user corrected the result.
- Make trace collection optional and local-only by default; never record raw microphone audio.
- Build repeatable tests for Slovak phrases, wake-word false activations, app launching, notification reading, offline replies, and confirmation gates.
- Measure task success, response time, memory use, and battery impact on the actual tablet before calling a feature reliable.
- Use failed traces to improve phrase matching and tests; do not automatically train on private conversations.

### 5. Scheduled and proactive work
OpenJarvis includes scheduled digests and continuous monitoring agents.

Adaptation:
- Use Android WorkManager for reminders and scheduled summaries that must survive app restarts.
- Start with a spoken morning briefing from already-authorized sources such as notification previews and local reminders.
- Use the foreground voice service only for the explicitly enabled microphone session; scheduled work must not secretly activate the microphone.
- Respect Android background limits and clearly show when a task is pending, running, or unavailable.

## Recommended implementation order

1. **Reliability foundation:** structured tool results, explicit confirmation gates, timeouts, and regression tests for existing voice commands.
2. **Local-first router:** deterministic commands first, then LiteRT-LM offline chat, then optional cloud fallback.
3. **Skill registry:** move capabilities behind small Kotlin interfaces with permission and network metadata.
4. **Local traces and evaluation:** opt-in privacy-preserving diagnostics and a repeatable Slovak test suite.
5. **WorkManager routines:** persistent reminders and an optional morning briefing.
6. **Only then expand:** research agent, richer EduPage integration where officially supported, and more advanced 3D workflows.

## Important constraints

- Target remains a native Kotlin/Jetpack Compose Android tablet app.
- No Termux, root, ADB, Ollama server, or mandatory personal API key.
- Offline capability must be described honestly: web search, live maps, and remote services need internet; small local models have limited reasoning and device-dependent speed.
- A foreground microphone service is not the same as guaranteed always-on wake-word detection on every Android tablet. Test lock-screen behavior, microphone privacy indicators, battery use, and manufacturer power restrictions on the target device.
- Do not claim a new APK is ready until a GitHub Actions build succeeds and the APK is installed and tested on the tablet.

## Source

Stanford OpenJarvis repository and README: https://github.com/open-jarvis/OpenJarvis  
The project describes local-first agents, an orchestrator, skills, evaluation/benchmarking, local traces, and scheduled/continuous agents. This plan adapts those patterns to the existing Android codebase rather than copying its desktop runtime.
