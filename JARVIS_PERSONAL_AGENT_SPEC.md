# JARVIS — Personal Agent, Voice Session & Integrations

This file defines intended behavior. It is a target specification, not a claim that every integration is implemented.

## 1. Natural voice session
- Wake phrase only starts a session: Hej Jarvis / Hey Jarvis.
- After activation, speak naturally without repeating the wake phrase: Jarvis, open Gmail; Počuj, vyhľadaj; Pusť hudbu na prácu.
- Keep the session available across spoken replies. Pause recognition while speaking/processing, then resume automatically.
- End only on a clear command such as Jarvis, spi / ukonči rozhovor, or the visible stop control; then return to wake standby.
- Show WAKE STANDBY, SESSION ACTIVE, LISTENING, THINKING, SPEAKING and ERROR, plus a visible mic indicator and immediate stop.
- No secret recording or microphone activation at app launch without explicit enablement. Do not promise locked-screen wake reliability before device testing.
- Slovak recognition; preferred male Slovak TTS voice sk-SK-LukasNeural when available, with fallback.

## 2. Private personal memory
The user explicitly wants JARVIS to use relevant details shared in ChatGPT. Create a private local profile containing:
- Slovak-first, concise/direct communication; preferred address pane; futuristic JARVIS / Stark-style technology.
- Music interests: guitar, original music, Christian rap, dark UK drill around 140 BPM, 808s, drums, piano, guitar and trumpet.
- Athletics, school interests, and future interest in medicine/neurosurgery or emergency care.
- Personal context involving Zuzka, discreetly and only when relevant to the user's request.
- Previous JARVIS requirements and user-approved preferences.
- Exact favorite songs/artists only when explicitly identified. Do not invent a favorite track; learn it when the user says a track is a favorite.
- Grades, wellbeing notes, health-related information and other sensitive records only when explicitly entered/imported by the user.

Memory controls: app-private local storage, encryption where feasible, review/edit/export/delete, corrections, dated events separated from stable preferences. Never claim automatic access to every ChatGPT conversation. Import only data the user explicitly provides or authorizes. Do not send private memory to online providers unless clearly enabled by the user. Include a local-only mode.

## 3. Gmail and email
Use Google OAuth and the official Gmail API. Never ask for a Google password or scrape credentials. With explicit account connection and granted scopes:
- Count unread mail; list recent messages with sender, subject and date.
- Summarize selected mail and read aloud only on request.
- Search mail from natural Slovak queries.
- Dictate and prepare email drafts for review.
- Sending always requires a final confirmation showing recipient, subject and body. Never delete, archive, forward, label or send without clear instruction.
- Show the connected account and provide disconnect/revoke controls.

## 4. Websites and Android actions
- Open websites and search results in a browser; summarize accessible pages with source links.
- Prefer Android intents and documented APIs for supported app actions.
- Optional Accessibility Service for UI actions without an API: user must separately enable it in Android settings; show active status; act only on explicit commands; never bypass logins, CAPTCHAs, payment confirmations or security controls.
- Do not claim universal finger-level control: Android does not let an ordinary app safely or reliably perform every possible UI action.
- Confirm consequential actions such as sending, posting, purchases, deletion and account/security changes.

## 5. Music
- Learn favorite songs/artists only when explicitly named or selected from a connected music library.
- Support requests like Pusť moju obľúbenú skladbu, Pusť hudbu na prácu, daj UK drill, or niečo pokojné.
- Use a connected music service/account or installed-app deep links. Explain login/subscription requirements and never claim playback started unless opening/playback is confirmed.
- Support user-curated WORK, TRAINING, CALM and FAVORITES presets.

## 6. Tool execution architecture
The AI interprets intent and proposes structured actions. Android validates permissions, account connection and arguments, executes the action, then returns the actual result. Model text alone must never claim success.

## 7. Delivery gates
Gmail OAuth, music integration, Accessibility Service, continuous voice session and local memory require separate implementation and testing. Build/install and verify on the actual tablet. Until then, mark them planned or prototype—not ready.