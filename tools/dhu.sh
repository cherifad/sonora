#!/usr/bin/env bash
# Starts the Android Auto Desktop Head Unit (DHU) against the connected device (emulator
# or phone) so the car UI can be driven from the development machine.
#
#   tools/dhu.sh            # touchscreen head unit
#   tools/dhu.sh -i rotary  # rotary controller
#
# On the device: Android Auto installed, developer mode on ("Start head unit server" in its
# menu) and the screen unlocked. On an emulator the Android Auto app is not installed by the
# google_apis image, so a phone (or a Play Store image) is the usual target.
set -euo pipefail

SDK=${ANDROID_HOME:-$HOME/android-sdk}
LIBS=${EMU_LIBS:-$HOME/.local/share/jellyfin-audio/emu-libs}
DHU=$SDK/extras/google/auto/desktop-head-unit
ADB=$SDK/platform-tools/adb

[ -x "$DHU" ] || { echo "no DHU at $DHU (sdkmanager --install \"extras;google;auto\")" >&2; exit 1; }

# The head unit server listens on 5277 on the device; the DHU talks to it over adb.
"$ADB" forward tcp:5277 tcp:5277 >/dev/null

[ -d "$LIBS" ] && export LD_LIBRARY_PATH="$LIBS:${LD_LIBRARY_PATH:-}"
exec "$DHU" "$@"
