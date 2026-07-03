# Decisions

## D1 — Languages (2026-07-03)
Dictation is mixed French/English (code-switching). Multilingual Whisper models
only — English-only variants are excluded.

## D2 — Interaction mode (2026-07-03)
Toggle mode: hotkey starts recording, recording auto-stops on silence.
Silence threshold before auto-stop: **5-10 seconds minimum** (user dictates
long-form prompts with pauses — do not cut early). Hotkey pressed again also
stops immediately.

## D3 — Text delivery (2026-07-03)
Default: transcript lands in the **clipboard + desktop notification**; user
pastes manually with Ctrl+V.

Second option (documented for later, not v1 default): auto-paste into the
focused field via ydotool (uinput). Requires one-time setup: ydotoold daemon +
/dev/uinput udev rule. Kept as an opt-in setting when implemented.

## D4 — Transcription backend (2026-07-03)
Client speaks the OpenAI-compatible `/v1/audio/transcriptions` API so the
backend is swappable by URL. v1 backend: **Groq hosted API**
(`whisper-large-v3-turbo`, free tier). Heroku rejected (512 MB dynos, sleep,
30 s router timeout, ephemeral FS). Migration path to a self-hosted VPS
(faster-whisper in Docker) is a config change only; to be documented in
docs/VPS-MIGRATION.md.

## D5 — Client stack (2026-07-03)
**Python** for the Linux client (background utility, ~no UI; smallest
footprint on an 8 GB laptop). Android phase 2 will be a native Kotlin IME
regardless (Flutter is not suited for building Android IMEs).

## D6 — Transcript history (2026-07-03)
The last 5 transcripts are kept as plain text in
`~/.local/share/whisper-dictate/history/`; `whisper-dictate copy [N]`
re-copies one without a new API call. Rationale: clipboard delivery can fail
(wl-copy stalls in odd compositor focus states) and users overwrite their
clipboard — a dictation must stay recoverable.
