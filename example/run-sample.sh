#!/bin/bash
# Builds, installs and launches the example app so it can be tried by hand.
#
# usage: example/run-sample.sh [ios|android|all]   (default: all)
#
# iOS:     simulator $IOS_SIM_UDID, or the newest "iPhone 18 Pro" simulator (kept separate from
#          the one the UI tests use). Opens Simulator.app (DeviceHub.app on Xcode 27).
# Android: every connected device/emulator (or only $ANDROID_SERIAL), or starts the AVD
#          $ANDROID_AVD (default: first AVD) when none is connected.
set -u
TARGET=${1:-all}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

run_ios() {
  local udid=${IOS_SIM_UDID:-}
  if [ -z "$udid" ]; then
    udid=$(xcrun simctl list devices available -j | python3 -I -c '
import json, sys
devices = json.load(sys.stdin)["devices"]
ios = sorted((k for k in devices if "iOS" in k), key=lambda k: [int(x) for x in k.split("iOS-")[-1].split("-") if x.isdigit()])
for runtime in reversed(ios):
    for d in devices[runtime]:
        if d["name"] == "iPhone 18 Pro":
            print(d["udid"]); sys.exit()
')
  fi
  [ -z "$udid" ] && { echo "No iOS simulator found (set IOS_SIM_UDID)"; return 1; }
  echo "iOS: building for simulator $udid..."
  xcrun simctl boot "$udid" 2>/dev/null
  # Simulator.app (older Xcode) or DeviceHub.app (Xcode 27) shows the simulator screen.
  local xcode
  xcode=$(cd "$(xcode-select -p)/../.." && pwd)
  for viewer in "$xcode/Contents/Developer/Applications/Simulator.app" "$xcode/Contents/Applications/DeviceHub.app"; do
    if [ -d "$viewer" ]; then open "$viewer"; break; fi
  done
  local derived="$ROOT/build/sample/ios"
  xcodebuild -project example/iosApp/iosApp.xcodeproj -scheme example.iosApp -configuration Debug \
    -destination "platform=iOS Simulator,id=$udid" -derivedDataPath "$derived" build > "$ROOT/build/sample/ios-build.log" 2>&1 \
    || { echo "iOS build failed: build/sample/ios-build.log"; return 1; }
  xcrun simctl bootstatus "$udid" -b > /dev/null
  xcrun simctl install "$udid" "$derived/Build/Products/Debug-iphonesimulator/Example.app"
  xcrun simctl launch --terminate-running-process "$udid" zone.ien.navigation.example.Example > /dev/null
  echo "iOS: running on $(xcrun simctl list devices | grep "$udid" | sed 's/ (Booted)//;s/^ *//')"
}

run_android() {
  local sdk
  sdk=$(grep '^sdk.dir=' local.properties 2>/dev/null | cut -d= -f2)
  [ -z "$sdk" ] && sdk=${ANDROID_HOME:-$HOME/Library/Android/sdk}
  local adb="$sdk/platform-tools/adb"
  online() { "$adb" devices | sed 1d | awk '$2 == "device" { print $1 }'; }
  local serials=${ANDROID_SERIAL:-$(online)}
  if [ -z "$serials" ]; then
    local avd=${ANDROID_AVD:-$("$sdk/emulator/emulator" -list-avds | head -1)}
    [ -z "$avd" ] && { echo "No Android device or AVD"; return 1; }
    echo "Android: starting emulator $avd..."
    nohup "$sdk/emulator/emulator" -avd "$avd" > "$ROOT/build/sample/emulator.log" 2>&1 &
    "$adb" wait-for-device
    serials=$(online)
  fi
  # adb needs -s when several devices are connected (e.g. a phone and an emulator).
  local serial
  for serial in $serials; do
    until [ "$("$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
    # The package manager comes up a little after boot_completed.
    until "$adb" -s "$serial" shell pm path android > /dev/null 2>&1; do sleep 2; done
  done
  echo "Android: installing on $(echo $serials)..."
  # installDebug installs on every connected device, or only on $ANDROID_SERIAL when it is set.
  ./gradlew :example:androidApp:installDebug -q > "$ROOT/build/sample/android-build.log" 2>&1 \
    || { echo "Android build failed: build/sample/android-build.log"; return 1; }
  for serial in $serials; do
    "$adb" -s "$serial" shell am start -n zone.ien.navigation.example/.MainActivity > /dev/null
    echo "Android: running on $("$adb" -s "$serial" shell getprop ro.product.model | tr -d '\r') ($serial)"
  done
}

mkdir -p "$ROOT/build/sample"
case "$TARGET" in
  ios) run_ios ;;
  android) run_android ;;
  all) run_ios; run_android ;;
  *) echo "usage: $0 [ios|android|all]"; exit 2 ;;
esac
