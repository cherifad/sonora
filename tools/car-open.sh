#!/usr/bin/env bash
# Opens the Android Automotive OS media app on Jellyfin Audio's media browser service.
# Run it with the car emulator (jellyfin_car) booted and the automotive APK installed:
#
#   tools/emulator.sh jellyfin_car
#   adb -s emulator-5554 install -r app/build/outputs/apk/automotive/debug/app-automotive-debug.apk
#   tools/car-open.sh
#
# The car's media host is told which service to show (android.car.intent.extra.MEDIA_COMPONENT);
# without it, it opens its own Local Media Player.
set -euo pipefail

SDK=${ANDROID_HOME:-$HOME/android-sdk}
ADB=$SDK/platform-tools/adb
SERIAL=${SERIAL:-emulator-5554}
PKG=${CAR_PKG:-com.adlen.jellyfinaudio.car.dev}
COMPONENT="$PKG/app.myspoti.host.android.PlaybackService"

# The app must not be in the "stopped" state (a package with no launcher starts that way, and
# the media host ignores stopped packages). Starting its playback service once is enough.
"$ADB" -s "$SERIAL" shell am start-foreground-service -n "$PKG/app.myspoti.host.android.PlaybackService" >/dev/null 2>&1 || true

"$ADB" -s "$SERIAL" shell am start \
  -n com.android.car.media/.MediaDispatcherActivity \
  -a android.car.intent.action.MEDIA_TEMPLATE \
  --es android.car.intent.extra.MEDIA_COMPONENT "$COMPONENT" >/dev/null
