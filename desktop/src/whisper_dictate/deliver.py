"""User-facing output: clipboard, notifications, sound cues. All fail-soft except clipboard."""

import os
import shutil
import subprocess

SOUNDS = {
    "start": "/usr/share/sounds/freedesktop/stereo/message-new-instant.oga",
    "stop": "/usr/share/sounds/freedesktop/stereo/message.oga",
    "success": "/usr/share/sounds/freedesktop/stereo/complete.oga",
    "error": "/usr/share/sounds/freedesktop/stereo/dialog-warning.oga",
}
PREVIEW_CHARS = 200
WLCOPY_TIMEOUT = 10

# One bubble per process, updated in place (Recording -> Transcribing -> result)
_notif_id: int | None = None


class ClipboardError(Exception):
    pass


def play_sound(name: str) -> None:
    path = SOUNDS.get(name)
    if path and os.path.exists(path) and shutil.which("paplay"):
        subprocess.Popen(
            ["paplay", path], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        )


def notify(message: str, urgency: str = "normal") -> None:
    global _notif_id
    if not shutil.which("notify-send"):
        return
    cmd = ["notify-send", "--print-id", "--urgency", urgency, "--app-name", "whisper-dictate"]
    if _notif_id is not None:
        cmd += ["--replace-id", str(_notif_id)]
    cmd += ["Whisper Dictate", message]
    out = subprocess.run(cmd, check=False, capture_output=True, text=True).stdout.strip()
    if out.isdigit():
        _notif_id = int(out)


def notify_error(message: str) -> None:
    play_sound("error")
    notify(message, urgency="critical")


def notify_result(text: str) -> None:
    play_sound("success")
    preview = text if len(text) <= PREVIEW_CHARS else text[:PREVIEW_CHARS] + "…"
    notify(f"Copied to clipboard:\n{preview}")


def to_clipboard(text: str) -> None:
    if not shutil.which("wl-copy"):
        raise ClipboardError("wl-copy not found — install it: sudo apt install wl-clipboard")
    last: Exception | None = None
    for _attempt in range(2):  # wl-copy can stall in odd compositor focus states
        try:
            subprocess.run(
                ["wl-copy"],
                input=text.encode(),
                check=True,
                timeout=WLCOPY_TIMEOUT,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            return
        except subprocess.TimeoutExpired as exc:
            last = exc
        except subprocess.CalledProcessError as exc:
            raise ClipboardError(f"wl-copy failed: {exc}") from exc
    raise ClipboardError(f"wl-copy timed out twice ({WLCOPY_TIMEOUT}s each)") from last
