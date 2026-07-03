#!/usr/bin/env bash
# Idempotent install: venv + symlink + config templates + GNOME hotkey.
# Usage: ./install.sh ["<Super>z"]   (hotkey arg optional; omit to skip binding)
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN_DIR="$HOME/.local/bin"
CONF_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/whisper-dictate"
BINDING="${1:-}"

echo "== venv =="
[ -d "$REPO/.venv" ] || python3 -m venv "$REPO/.venv"
"$REPO/.venv/bin/pip" install --quiet -e "$REPO"

echo "== launcher =="
mkdir -p "$BIN_DIR"
ln -sf "$REPO/.venv/bin/whisper-dictate" "$BIN_DIR/whisper-dictate"

echo "== config =="
mkdir -p "$CONF_DIR"
[ -f "$CONF_DIR/config.toml" ] || cp "$REPO/config.example.toml" "$CONF_DIR/config.toml"
if [ ! -f "$CONF_DIR/env" ]; then
    printf '# Put your key here:\nGROQ_API_KEY=\n' > "$CONF_DIR/env"
    chmod 600 "$CONF_DIR/env"
    echo "   -> add your key to $CONF_DIR/env"
fi

echo "== dependencies =="
for tool in wl-copy notify-send paplay; do
    command -v "$tool" >/dev/null && echo "   $tool: OK" || \
        echo "   $tool: MISSING (wl-copy -> sudo apt install wl-clipboard)"
done

if [ -n "$BINDING" ]; then
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
    echo "== GNOME hotkey: skipped (pass a binding, e.g. ./install.sh '<Super>z') =="
fi

echo "Done. Check: whisper-dictate status"
