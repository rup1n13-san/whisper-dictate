# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

`whisper-dictate` — a Linux dictation utility replacing the owner's habit of
dictating prompts into ChatGPT's web mic. Flow: GNOME hotkey → record mic →
auto-stop on silence → transcribe via a remote Whisper API → transcript lands
in the clipboard + desktop notification → user pastes manually. Android IME
(native Kotlin, same API) is a later phase, not in this repo yet.

All binding product decisions live in `docs/DECISIONS.md` — read it before
changing behavior. Do not re-litigate settled decisions (D1–D5); propose a new
entry there instead.

Repo layout: `desktop/` = Linux client (Python, working); `mobile/` = future
Android IME (native Kotlin, not started); `docs/` = decisions + status.

## Architecture (v1)

Python package, `desktop/src/whisper_dictate/`, **no daemon and almost no
UI** — a single-shot process per dictation:

- `whisper-dictate toggle` (bound to a GNOME custom shortcut via gsettings):
  starts recording, or — if a pidfile in `XDG_RUNTIME_DIR` shows an instance
  already recording — signals it (SIGUSR1) to stop and transcribe now.
- Capture: 16 kHz mono via `sounddevice`, written as FLAC (`soundfile`).
- Auto-stop: VAD with a **5–10 s silence threshold (default 7 s)** — the user
  dictates long-form with pauses; never cut early. Hard cap 5 min.
- Transcription: POST to an **OpenAI-compatible** `/v1/audio/transcriptions`
  endpoint. v1 backend is Groq (`whisper-large-v3-turbo`), but the backend is
  swappable purely by config URL — keep the client provider-agnostic. No
  language pin: dictation is mixed French/English.
- Delivery: `wl-copy` + `notify-send` + start/stop sound cues. Clipboard-only
  by design (D3); auto-paste via ydotool is a documented opt-in, not default.
- Failure contract: a dictation is never lost — on any API/network failure the
  FLAC is kept at `~/.local/share/whisper-dictate/last.flac` and
  `whisper-dictate retry` re-sends it.

Config: `~/.config/whisper-dictate/config.toml`; `GROQ_API_KEY` comes from
`~/.config/whisper-dictate/env` (never in the repo).

## Environment constraints (non-obvious, load-bearing)

Target machine is Ubuntu **GNOME on Wayland**, i5-1035G1, 7.5 GB RAM, no
NVIDIA GPU. Consequences:

- `wtype` does **not** work (Mutter lacks the virtual-keyboard protocol);
  text injection, if ever enabled, must go through `ydotool` (uinput).
- Apps cannot grab global hotkeys on Wayland — the hotkey is a GNOME custom
  shortcut invoking the CLI, registered by `install.sh` via gsettings.
- No client-side always-on-top overlays on GNOME Wayland; recording state is
  signaled by sound + notification instead.
- Local Whisper inference was ruled out (RAM); Heroku was ruled out as a host
  (512 MB dynos, sleep, 30 s router timeout). Self-host migration path:
  `docs/VPS-MIGRATION.md`.

## Commands

All from `desktop/`:

- Setup: `python3 -m venv .venv && .venv/bin/pip install -e ".[dev]"`
  (or `./install.sh` which also installs config templates + GNOME hotkey)
- Lint: `.venv/bin/ruff check .`
- Run: `whisper-dictate toggle|retry|copy [N]|status` (symlinked to
  `~/.local/bin`)
- No test suite; verification is the offline fake-server flow (see
  docs/STATUS.md) plus live dictation. Testing tip: point the client at a
  local fake server by overriding `XDG_CONFIG_HOME`/`XDG_DATA_HOME` — never
  test against the user's real config dir.

The VAD tuning constants (RMS gate, streak, min voiced) at the top of
`desktop/src/whisper_dictate/audio.py` were calibrated against this laptop's
ambient noise — don't change them without re-running the ambient-rejection
test in docs/STATUS.md.
