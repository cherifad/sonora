#!/usr/bin/env bash
# Installs the Linux Android SDK packages needed to build the app in WSL.
set -euo pipefail

export JAVA_HOME=/home/adlen/tools/jdk-17.0.20.1+1
SDK_ROOT="$HOME/android-sdk"
SDKM="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$SDKM" ]; then
  echo "sdkmanager not found at $SDKM" >&2
  exit 1
fi

yes | "$SDKM" --sdk_root="$SDK_ROOT" --licenses >/tmp/sdk-licenses.log 2>&1 || true

if ! "$SDKM" --sdk_root="$SDK_ROOT" "platform-tools" "platforms;android-37.0" "build-tools;36.0.0" >/tmp/sdk-install.log 2>&1; then
  "$SDKM" --sdk_root="$SDK_ROOT" "platform-tools" "platforms;android-36" "build-tools;36.0.0" >>/tmp/sdk-install.log 2>&1
fi

echo "Android SDK packages installed:"
ls "$SDK_ROOT/platforms" "$SDK_ROOT/build-tools"
