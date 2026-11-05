#!/usr/bin/env bash
# Starts the Android emulator used for device tests.
#
#   tools/emulator.sh                 # the phone AVD (jellyfin)
#   tools/emulator.sh jellyfin_tablet # another AVD
#
# WSL2's base image lacks a few libraries the emulator links against; tools/fetch-emulator-libs.sh
# puts them in ~/.local/share/jellyfin-audio/emu-libs and they are added to the loader path here.
# The window opens on the Windows desktop through WSLg (DISPLAY=:0).
set -euo pipefail

SDK=${ANDROID_HOME:-$HOME/android-sdk}
AVD=${1:-${AVD_NAME:-jellyfin}}
[ $# -gt 0 ] && shift || true
LIBS=${EMU_LIBS:-$HOME/.local/share/jellyfin-audio/emu-libs}

EMULATOR=$SDK/emulator/emulator
ADB=$SDK/platform-tools/adb
[ -x "$EMULATOR" ] || { echo "no emulator at $EMULATOR (set ANDROID_HOME)" >&2; exit 1; }

if [ -d "$LIBS" ]; then
  export LD_LIBRARY_PATH="$LIBS:$LIBS/pulseaudio:${LD_LIBRARY_PATH:-}"
fi

"$ADB" start-server >/dev/null 2>&1 || true

exec "$EMULATOR" -avd "$AVD" \
  -gpu swiftshader_indirect \
  -no-audio \
  -no-boot-anim \
  -no-snapshot-save \
  "$@"
