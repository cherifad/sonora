#!/usr/bin/env bash
set -euo pipefail
export JAVA_HOME=/home/adlen/tools/jdk-17.0.20.1+1
SDK_ROOT="$HOME/android-sdk"
SDKM="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

yes | "$SDKM" --sdk_root="$SDK_ROOT" --licenses >/tmp/sdk-licenses.log 2>&1 || true
"$SDKM" --sdk_root="$SDK_ROOT" "platforms;android-37.0" "build-tools;36.0.0"

echo "Installed platforms:"
ls "$SDK_ROOT/platforms"
