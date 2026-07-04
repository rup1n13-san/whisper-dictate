#!/usr/bin/env bash
# One-shot, idempotent setup: system deps + venv + launcher + config + hotkey.
# Usage: ./install.sh ["<Super>z"]   (hotkey arg optional; omit to skip binding)
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN_DIR="$HOME/.local/bin"
CONF_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/whisper-dictate"
BINDING="${1:-}"

apt_install() {
    sudo apt-get update -qq
    sudo apt-get install -y "$@"
}

echo "== python =="
command -v python3 >/dev/null || { echo "   python3 not found — install Python 3.11+ first"; exit 1; }
python3 -c 'import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)' \
    || { echo "   $(python3 --version) found, but 3.11+ is required"; exit 1; }
echo "   $(python3 --version): OK"

echo "== system packages =="
MISSING=()
command -v wl-copy      >/dev/null || MISSING+=(wl-clipboard)
command -v notify-send  >/dev/null || MISSING+=(libnotify-bin)
command -v paplay       >/dev/null || MISSING+=(pulseaudio-utils)
python3 -c 'import venv, ensurepip' 2>/dev/null || MISSING+=(python3-venv)
if [ "${#MISSING[@]}" -gt 0 ]; then
    if command -v apt-get >/dev/null; then
        echo "   missing: ${MISSING[*]} — installing (sudo will ask for your password)"
        apt_install "${MISSING[@]}"
    else
        echo "   missing: ${MISSING[*]}"
        echo "   install them with your package manager, then re-run this script"
        exit 1
    fi
else
    echo "   all present"
fi

echo "== venv =="
[ -d "$REPO/.venv" ] || python3 -m venv "$REPO/.venv"
"$REPO/.venv/bin/pip" install --quiet -e "$REPO"
# the sounddevice wheel bundles PortAudio; fall back to the system lib if not
if ! "$REPO/.venv/bin/python" -c 'import sounddevice' 2>/dev/null; then
    echo "   PortAudio missing — installing libportaudio2 (sudo will ask for your password)"
    apt_install libportaudio2
    "$REPO/.venv/bin/python" -c 'import sounddevice'
fi

echo "== launcher =="
mkdir -p "$BIN_DIR"
ln -sf "$REPO/.venv/bin/whisper-dictate" "$BIN_DIR/whisper-dictate"
case ":$PATH:" in
    *":$BIN_DIR:"*) ;;
    *) echo "   NOTE: $BIN_DIR is not on your PATH — log out and back in (Ubuntu adds it automatically), or add it to ~/.profile" ;;
esac

echo "== config =="
mkdir -p "$CONF_DIR"
[ -f "$CONF_DIR/config.toml" ] || cp "$REPO/config.example.toml" "$CONF_DIR/config.toml"
if [ ! -f "$CONF_DIR/env" ]; then
    printf '# Put your key here:\nGROQ_API_KEY=\n' > "$CONF_DIR/env"
    chmod 600 "$CONF_DIR/env"
fi

if [ -n "$BINDING" ]; then
    if command -v gsettings >/dev/null && gsettings list-schemas 2>/dev/null | grep -q '^org.gnome.settings-daemon.plugins.media-keys$'; then
        echo "== GNOME hotkey ($BINDING) =="
        SCHEMA="org.gnome.settings-daemon.plugins.media-keys"
        KEYPATH="/org/gnome/settings-daemon/plugins/media-keys/custom-keybindings/whisper-dictate/"
        LIST="$(gsettings get $SCHEMA custom-keybindings)"
        if [[ "$LIST" != *"$KEYPATH"* ]]; then
            if [[ "$LIST" == "@as []" || "$LIST" == "[]" ]]; then
                NEW="['$KEYPATH']"
            else
                NEW="${LIST%]*}, '$KEYPATH']"
            fi
            gsettings set $SCHEMA custom-keybindings "$NEW"
        fi
        gsettings set "$SCHEMA.custom-keybinding:$KEYPATH" name "Whisper Dictate"
        gsettings set "$SCHEMA.custom-keybinding:$KEYPATH" command "$BIN_DIR/whisper-dictate toggle"
        gsettings set "$SCHEMA.custom-keybinding:$KEYPATH" binding "$BINDING"
        echo "   bound: $BINDING -> whisper-dictate toggle"
    else
        echo "== hotkey: not GNOME — bind '$BIN_DIR/whisper-dictate toggle' to a key in your desktop's shortcut settings =="
    fi
else
    echo "== hotkey: skipped (pass a binding, e.g. ./install.sh '<Super>z') =="
fi

echo
echo "Done. Two steps left:"
if ! grep -q '^GROQ_API_KEY=..*' "$CONF_DIR/env" 2>/dev/null; then
    echo "  1. Add your API key (free at https://console.groq.com) to $CONF_DIR/env"
else
    echo "  1. API key: already set"
fi
echo "  2. Test: whisper-dictate toggle  (speak, pause ~7 s, transcript lands in your clipboard)"
