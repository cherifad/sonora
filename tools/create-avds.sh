#!/usr/bin/env bash
# Creates (or recreates) the AVDs used for the test matrix: a phone and a tablet.
# Android Studio is not needed; this uses cmdline-tools only.
#
#   tools/create-avds.sh
#
# The images are ~1.5 GB each; they are only downloaded once.
set -euo pipefail

SDK=${ANDROID_HOME:-$HOME/android-sdk}
BIN=$SDK/cmdline-tools/latest/bin
SDKMANAGER=$BIN/sdkmanager
AVDMANAGER=$BIN/avdmanager
IMAGE=${IMAGE:-system-images;android-36-ext19;google_apis;x86_64}

[ -x "$SDKMANAGER" ] || { echo "no sdkmanager at $SDKMANAGER (set ANDROID_HOME)" >&2; exit 1; }

yes | "$SDKMANAGER" --install "$IMAGE"

# --force replaces an AVD of the same name (its data is lost).
"$AVDMANAGER" create avd -n jellyfin        -k "$IMAGE" -d pixel_7       --force
"$AVDMANAGER" create avd -n jellyfin_tablet -k "$IMAGE" -d medium_tablet --force

echo "AVDs ready: jellyfin (phone), jellyfin_tablet (tablet)"
echo "Start one with: tools/emulator.sh jellyfin_tablet"
