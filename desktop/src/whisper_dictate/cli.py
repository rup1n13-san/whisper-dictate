"""whisper-dictate CLI: toggle | retry | status. No daemon — one process per dictation."""

import argparse
import os
import signal
import sys
import threading
from pathlib import Path

from . import audio, config, deliver, history, transcribe

RUNTIME_DIR = Path(os.environ.get("XDG_RUNTIME_DIR", "/tmp"))
PIDFILE = RUNTIME_DIR / "whisper-dictate.pid"
LAST_AUDIO = config.DATA_DIR / "last.flac"


def _running_pid() -> int | None:
    try:
        pid = int(PIDFILE.read_text())
        os.kill(pid, 0)
        return pid
    except (FileNotFoundError, ValueError, ProcessLookupError, PermissionError):
        return None


def _acquire_pidfile() -> bool:
    if _running_pid() is None:
        PIDFILE.unlink(missing_ok=True)  # stale
    try:
        fd = os.open(PIDFILE, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o644)
    except FileExistsError:
        return False
    with os.fdopen(fd, "w") as fh:
        fh.write(str(os.getpid()))
    return True


def cmd_toggle() -> int:
    pid = _running_pid()
    if pid is not None:
        os.kill(pid, signal.SIGUSR1)
        return 0
    if not _acquire_pidfile():
        return 0  # lost the race to a concurrent toggle: treat as its stop signal
    try:
        return _record_and_send()
    finally:
        PIDFILE.unlink(missing_ok=True)


def _record_and_send() -> int:
    try:
        cfg = config.load()
    except config.ConfigError as exc:
        deliver.notify_error(str(exc))
        return 1
    stop = threading.Event()
    signal.signal(signal.SIGUSR1, lambda *_: stop.set())
    deliver.play_sound("start")
    deliver.notify("Recording… speak now. Hotkey again to stop.")
    try:
        audio.record(LAST_AUDIO, cfg.silence_seconds, cfg.max_duration_seconds, stop)
    except audio.NoSpeechError:
        deliver.play_sound("stop")
        deliver.notify("No speech detected — nothing sent.")
        return 1
    except audio.MicError as exc:
        deliver.notify_error(str(exc))
        return 1
    deliver.play_sound("stop")
    deliver.notify("Transcribing…")
    return _send(cfg)


def _send(cfg: config.Config) -> int:
    try:
        text = transcribe.transcribe(cfg, LAST_AUDIO)
    except transcribe.TranscriptionError as exc:
        deliver.notify_error(f"{exc}\nAudio kept — run: whisper-dictate retry")
        return 1
    text = text.strip()
    if not text:
        deliver.notify("Empty transcript — nothing copied.")
        return 1
    history.save(text)
    return _copy_out(text)


def _copy_out(text: str) -> int:
    try:
        deliver.to_clipboard(text)
    except deliver.ClipboardError as exc:
        print(text)
        deliver.notify_error(f"{exc}\nTranscript saved — run: whisper-dictate copy")
        return 1
    deliver.notify_result(text)
    return 0


def cmd_retry() -> int:
    try:
        cfg = config.load()
    except config.ConfigError as exc:
        deliver.notify_error(str(exc))
        return 1
    if not LAST_AUDIO.exists():
        deliver.notify("Nothing to retry — no saved recording.")
        return 1
    deliver.notify("Retrying transcription…")
    return _send(cfg)


def cmd_copy(n: int) -> int:
    text = history.get(n)
    if text is None:
        deliver.notify(f"No transcript #{n} in history.")
        print(f"no transcript #{n} in history", file=sys.stderr)
        return 1
    return _copy_out(text)


def cmd_qr() -> int:
    """Provisioning QR for the Android app (Settings > Scan QR)."""
    import json

    import qrcode

    try:
        cfg = config.load()
    except config.ConfigError as exc:
        print(f"config error: {exc}", file=sys.stderr)
        return 1
    payload = json.dumps({"api_base_url": cfg.api_base_url, "api_key": cfg.api_key})
    qr = qrcode.QRCode(border=1)
    qr.add_data(payload)
    qr.print_ascii(invert=True)
    print("Scan with the Whisper Dictate Android app (Settings > Scan QR).")
    print("The QR contains your API key — don't screenshot or share it.")
    return 0


def cmd_status() -> int:
    pid = _running_pid()
    print(f"recording: {'yes (pid ' + str(pid) + ')' if pid else 'no'}")
    print(f"last audio: {LAST_AUDIO if LAST_AUDIO.exists() else 'none'}")
    for i, (stamp, text) in enumerate(history.entries(), start=1):
        preview = text.replace("\n", " ")[:60]
        print(f"history {i} ({stamp}): {preview}")
    try:
        cfg = config.load()
        print(f"endpoint: {cfg.api_base_url} (model {cfg.model})")
        print(f"silence_seconds: {cfg.silence_seconds}, api key: set")
    except config.ConfigError as exc:
        print(f"config: ERROR — {exc}")
        return 1
    return 0


def main() -> None:
    parser = argparse.ArgumentParser(prog="whisper-dictate")
    parser.add_argument("command", choices=["toggle", "retry", "status", "copy", "qr"])
    parser.add_argument(
        "n", nargs="?", type=int, default=1,
        help="for copy: 1 = latest transcript, 2 = previous, … (max 5)",
    )
    args = parser.parse_args()
    if args.command == "copy":
        sys.exit(cmd_copy(args.n))
    commands = {"toggle": cmd_toggle, "retry": cmd_retry, "status": cmd_status, "qr": cmd_qr}
    sys.exit(commands[args.command]())
