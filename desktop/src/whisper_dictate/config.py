"""Configuration: config.toml + API key from the user's config dir."""

import os
import tomllib
from dataclasses import dataclass
from pathlib import Path

CONFIG_DIR = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config")) / "whisper-dictate"
DATA_DIR = Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local/share")) / "whisper-dictate"

DEFAULTS = {
    "api_base_url": "https://api.groq.com/openai/v1",
    "model": "whisper-large-v3-turbo",
    "silence_seconds": 7.0,
    "max_duration_seconds": 300,
}
API_KEY_VARS = ("WHISPER_DICTATE_API_KEY", "GROQ_API_KEY")


class ConfigError(Exception):
    pass


@dataclass
class Config:
    api_base_url: str
    model: str
    silence_seconds: float
    max_duration_seconds: int
    api_key: str


def _read_api_key() -> str:
    for var in API_KEY_VARS:
        if os.environ.get(var):
            return os.environ[var]
    env_file = CONFIG_DIR / "env"
    if env_file.exists():
        for line in env_file.read_text().splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            if key.strip() in API_KEY_VARS and value.strip():
                return value.strip().strip('"')
    raise ConfigError(
        f"No API key. Put GROQ_API_KEY=<your key> in {env_file} and chmod 600 it."
    )


def load() -> Config:
    values = dict(DEFAULTS)
    path = CONFIG_DIR / "config.toml"
    if path.exists():
        try:
            with path.open("rb") as fh:
                values.update(tomllib.load(fh))
        except tomllib.TOMLDecodeError as exc:
            raise ConfigError(f"Invalid TOML in {path}: {exc}") from exc
    unknown = set(values) - set(DEFAULTS)
    if unknown:
        raise ConfigError(f"Unknown keys in {path}: {', '.join(sorted(unknown))}")
    silence = float(values["silence_seconds"])
    if not 3 <= silence <= 30:
        raise ConfigError("silence_seconds must be between 3 and 30")
    max_duration = int(values["max_duration_seconds"])
    if not 30 <= max_duration <= 1800:
        raise ConfigError("max_duration_seconds must be between 30 and 1800")
    return Config(
        api_base_url=str(values["api_base_url"]).rstrip("/"),
        model=str(values["model"]),
        silence_seconds=silence,
        max_duration_seconds=max_duration,
        api_key=_read_api_key(),
    )
