#!/usr/bin/env bash
# Builds the debug APK inside WSL using the Linux Android SDK.
set -euo pipefail

export JAVA_HOME=/home/adlen/tools/jdk-17.0.20.1+1
export ANDROID_HOME=/home/adlen/android-sdk
export ANDROID_SDK_ROOT=/home/adlen/android-sdk

cd /home/adlen/jellyfin/jellyfin-audio
./gradlew :app:assembleDebug --stacktrace "$@"
