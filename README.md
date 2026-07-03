# whisper-dictate

Voice dictation that types for you. Press a hotkey, speak in French, English
or both, and the transcript lands in your clipboard a second after you stop
talking — ready to paste into any app, terminal, or AI-agent prompt.

Built because dictating prompts into a browser tab is a workflow, not a tool.

## How it works

```
hotkey ── record mic (16 kHz FLAC) ── auto-stop on silence
                                            │
clipboard + notification ◄── Whisper API ◄──┘
```

- **No daemon, no UI.** One short-lived process per dictation, signaled
  through a pidfile. Sound cues and a self-updating notification tell you
  what's happening; a menu-bar mic icon shows while recording.
- **Backend-agnostic.** Talks to any OpenAI-compatible
  `/v1/audio/transcriptions` endpoint. Default is Groq
  (`whisper-large-v3-turbo`, free tier, ~1 s round-trip); switching to a
  self-hosted server is one config line — see
  [docs/VPS-MIGRATION.md](docs/VPS-MIGRATION.md).
- **A dictation is never lost.** Failed API call? The audio is kept —
  `whisper-dictate retry`. Clipboard overwritten or delivery failed? The last
  5 transcripts are stored locally — `whisper-dictate copy [N]`.

## Repository layout

| Folder | Contents |
|--------|----------|
| `desktop/` | Linux client (Python) — current, working |
| `mobile/` | Android voice-input IME (native Kotlin) — planned, same API |
| `docs/` | Decisions, status journal, VPS migration guide |

## Desktop client

### Requirements

Linux with Wayland (built and tested on Ubuntu GNOME), Python ≥ 3.11,
a microphone, and:

```bash
sudo apt install wl-clipboard libportaudio2
```

### Install

```bash
cd desktop
./install.sh '<Super>z'    # any GNOME binding you like
```

The script is idempotent: creates the venv, links `whisper-dictate` into
`~/.local/bin`, installs config templates, and registers the GNOME shortcut.

Add your API key (free at [console.groq.com](https://console.groq.com)) to
`~/.config/whisper-dictate/env`:

```
GROQ_API_KEY=gsk_...
```

### Use

1. Press the hotkey — ping sound, "Recording…" notification.
2. Dictate. Pauses shorter than `silence_seconds` (default 7 s) never cut
   you off.
3. Stop by staying silent, or press the hotkey again.
4. Chime — the transcript is in your clipboard. Paste with Ctrl+V.

### Commands

| Command | Does |
|---------|------|
| `whisper-dictate toggle` | Start recording / stop and transcribe (the hotkey) |
| `whisper-dictate retry` | Re-send the last recording after an API/network failure |
| `whisper-dictate copy [N]` | Re-copy a stored transcript (1 = latest … 5) without an API call |
| `whisper-dictate status` | Recording state, config, transcript history |

### Configuration

`~/.config/whisper-dictate/config.toml` (see
[desktop/config.example.toml](desktop/config.example.toml)):

| Key | Default | Meaning |
|-----|---------|---------|
| `api_base_url` | Groq | Any OpenAI-compatible endpoint |
| `model` | `whisper-large-v3-turbo` | Model id as exposed by the backend |
| `silence_seconds` | `7.0` | Silence needed before auto-stop (3–30) |
| `max_duration_seconds` | `300` | Hard cap per dictation (30–1800) |

### Design notes

Wayland/GNOME imposes most of the architecture: apps cannot grab global
hotkeys (hence the gsettings shortcut), cannot type into other windows
without uinput (hence clipboard-first delivery), and cannot show always-on-top
overlays (hence sounds + notifications). Details and settled decisions:
[docs/DECISIONS.md](docs/DECISIONS.md). Project journal:
[docs/STATUS.md](docs/STATUS.md).

## Mobile (planned)

Android voice-input keyboard (IME) in native Kotlin hitting the same
transcription API — dictate into any app's text field. Not started; lives in
`mobile/` when it does.

## Development

```bash
cd desktop
python3 -m venv .venv && .venv/bin/pip install -e ".[dev]"
.venv/bin/ruff check .        # lint
```

No test suite yet; verification is a fake-server flow plus live dictation —
see [docs/STATUS.md](docs/STATUS.md).
