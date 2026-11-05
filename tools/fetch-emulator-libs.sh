#!/usr/bin/env bash
# The Android emulator links against host libraries the WSL2 base image does not ship
# (libpulse, libsndfile, libmp3lame, ...). Without root, this downloads the Debian
# packages, unpacks them beside the SDK and leaves them in a directory that
# tools/emulator.sh adds to LD_LIBRARY_PATH.
#
#   tools/fetch-emulator-libs.sh
#
# Re-run it after a distribution upgrade if the emulator complains about a missing .so.
set -euo pipefail

DEST=${1:-$HOME/.local/share/jellyfin-audio/emu-libs}
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
cd "$TMP"

packages=(
  libpulse0        # libpulse.so.0 + pulseaudio/libpulsecommon
  libsndfile1      # libsndfile.so.1
  libmp3lame0      # libmp3lame.so.0
  libflac12 libmpg123-0 libopus0 libvorbis0a libvorbisenc2 libogg0 libasyncns0 liblz4-1
)

# apt-get download needs no privileges (the packages land in the current directory).
apt-get download "${packages[@]}"

mkdir -p "$DEST"
for deb in ./*.deb; do dpkg-deb -x "$deb" "$DEST"; done
# Flat layout: the emulator looks for the .so files and the pulseaudio/ folder side by side.
cp -r "$DEST/usr/lib/x86_64-linux-gnu/." "$DEST/"

echo "Emulator libraries ready in $DEST"
echo "Start an AVD with: tools/emulator.sh"
