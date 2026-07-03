"""OpenAI-compatible /audio/transcriptions client. Provider-agnostic."""

from pathlib import Path

import requests

from .config import Config

TIMEOUT = (10, 120)  # connect, read


class TranscriptionError(Exception):
    pass


def transcribe(cfg: Config, path: Path) -> str:
    url = f"{cfg.api_base_url}/audio/transcriptions"
    last_exc: Exception | None = None
    for _attempt in range(2):
        try:
            with path.open("rb") as fh:
                resp = requests.post(
                    url,
                    headers={"Authorization": f"Bearer {cfg.api_key}"},
                    files={"file": (path.name, fh, "audio/flac")},
                    data={"model": cfg.model, "response_format": "json"},
                    timeout=TIMEOUT,
                )
        except requests.Timeout as exc:
            raise TranscriptionError("Transcription request timed out.") from exc
        except requests.ConnectionError as exc:
            last_exc = exc
            continue
        if resp.status_code == 401:
            raise TranscriptionError("API key rejected (401) — check ~/.config/whisper-dictate/env.")
        if resp.status_code == 429:
            raise TranscriptionError("Rate limited (429) — wait a moment.")
        if not resp.ok:
            raise TranscriptionError(f"API error {resp.status_code}: {resp.text[:200]}")
        try:
            return resp.json()["text"]
        except (ValueError, KeyError) as exc:
            raise TranscriptionError(f"Unexpected API response: {resp.text[:200]}") from exc
    raise TranscriptionError(f"Network unreachable: {last_exc}")
