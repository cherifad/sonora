#!/usr/bin/env bash
# The Android emulator (and the Android Auto Desktop Head Unit) link against host
# libraries the WSL2 base image does not ship (libpulse, libsndfile, libmp3lame,
# libc++...). Without root, this downloads the Debian packages, unpacks them beside
# the SDK and leaves them in a directory the tools add to LD_LIBRARY_PATH.
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
  libc++1-14 libc++abi1-14 libunwind8   # the Desktop Head Unit (and some emulator parts)
)

# apt-get download needs no privileges (the packages land in the current directory).
apt-get download "${packages[@]}"

mkdir -p "$DEST"
for deb in ./*.deb; do dpkg-deb -x "$deb" "$DEST"; done
# Flat layout: the tools look for the .so files (and pulseaudio/) side by side.
cp -r "$DEST/usr/lib/x86_64-linux-gnu/." "$DEST/" 2>/dev/null || true
# LLVM ships libc++ as symlinks into ../llvm-14/lib: keep the real files, flat.
if [ -d "$DEST/usr/lib/llvm-14/lib" ]; then
  find "$DEST/usr/lib/llvm-14/lib" -maxdepth 1 -type f -name '*.so.*' -exec cp -f {} "$DEST/" \;
fi
ln -sf libc++.so.1.0 "$DEST/libc++.so.1"
ln -sf libc++abi.so.1.0 "$DEST/libc++abi.so.1"
# libc++abi asks for libunwind.so.1; Debian only ships the 8 ABI under that soname.
[ -e "$DEST/libunwind.so.8" ] && ln -sf libunwind.so.8 "$DEST/libunwind.so.1"

echo "Emulator libraries ready in $DEST"
echo "Start an AVD with: tools/emulator.sh"
echo "Start the Android Auto head unit with: tools/dhu.sh"
