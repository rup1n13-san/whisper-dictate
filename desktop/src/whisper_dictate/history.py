"""Rolling store of the last transcripts, newest first."""

import time

from .config import DATA_DIR

HISTORY_DIR = DATA_DIR / "history"
KEEP = 5


def save(text: str) -> None:
    HISTORY_DIR.mkdir(parents=True, exist_ok=True)
    (HISTORY_DIR / f"{time.strftime('%Y%m%d-%H%M%S')}.txt").write_text(text)
    for old in sorted(HISTORY_DIR.glob("*.txt"))[:-KEEP]:
        old.unlink()


def get(n: int = 1) -> str | None:
    """n=1 is the most recent transcript, n=2 the one before, etc."""
    files = sorted(HISTORY_DIR.glob("*.txt"), reverse=True)
    if n < 1 or n > len(files):
        return None
    return files[n - 1].read_text()


def entries() -> list[tuple[str, str]]:
    return [(f.stem, f.read_text()) for f in sorted(HISTORY_DIR.glob("*.txt"), reverse=True)]
