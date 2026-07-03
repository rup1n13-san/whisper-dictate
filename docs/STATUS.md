# Project status

## 2026-07-03 (later) — repo published

Restructured: Linux client moved to `desktop/`, `mobile/` placeholder for the
Android IME phase. Pushed to github.com/rup1n13-san/whisper-dictate (main =
bootstrap commit; future work branches off `dev` per GIT-CONVENTIONS).

## 2026-07-03 — v1 built, awaiting live test

Done (tasks 1–8, 10): scaffold, config/secrets, audio capture (16 kHz FLAC),
energy-gated VAD auto-stop, pidfile toggle, transcription client, clipboard +
notification delivery, CLI, install.sh, docs. Lint (ruff) passes. Offline
tests pass: fake-server round-trip, toggle/signal lifecycle, ambient-noise
rejection, stdout fallback when wl-copy missing.

Setup complete (2026-07-03): wl-clipboard installed, Groq key configured
(was pasted twice → deduplicated), hotkey `<Super>z` registered. Full E2E
verified with synthesized speech: Groq round-trip 1.2 s, transcript in
clipboard. Remaining: live test with the user's real voice (task 9).

## 2026-07-03 (later) — live test passed, hardening round

Live FR/EN dictation validated by the user (long-form Dostoevsky quotes, both
languages, good quality; user now dictates his own prompts with the tool).
One transient failure observed: wl-copy stalled >10 s (compositor focus state,
Activities overview open). Fixes shipped: wl-copy auto-retry, transcript
history (last 5, `copy [N]` command), failure notification points to `copy`,
single self-updating notification bubble + distinct success/error sounds.
Known Whisper artifact to watch: occasional sentence repetition after long
pauses.

## Lessons

- webrtcvad alone flags ambient fan/mic noise as speech ~30–44% of frames on
  this laptop; it must be gated by an adaptive RMS threshold (3× running
  noise floor) plus a 2-consecutive-frame rule and a ≥300 ms total-voiced
  minimum. Tuned constants live at the top of
  `desktop/src/whisper_dictate/audio.py`.
- GNOME Wayland: wtype is a dead end (Mutter), hotkeys only via gsettings
  custom keybindings, no app-side always-on-top overlays.
- Heroku rejected as backend host (512 MB dynos, sleep, 30 s router timeout).
- A 401 from Groq with a key of exactly double length = key pasted twice in
  the env file. Worth checking value length before blaming the provider.

## Next

- Live FR/EN dictation test → tune silence_seconds / VAD constants if needed.
- Later: opt-in auto-paste via ydotool (DECISIONS.md D3), VPS migration
  (docs/VPS-MIGRATION.md), Android native IME (phase 2).
