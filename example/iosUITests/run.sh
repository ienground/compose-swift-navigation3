#!/bin/bash
# Runs the example app's UI tests with real touches on one simulator.
#
# usage: example/iosUITests/run.sh <simulator udid> [xcodebuild -only-testing:... args]
#   e.g. example/iosUITests/run.sh 5807183C-... -only-testing:UITests/NavigationUITests
#
# Builds the example app, installs it, runs the tests, and writes to build/ui-tests/<udid>/:
#   xcodebuild.log, app.log (system log of the app), shots/<millis>.png (screenshots ~1/s)
# Then: python3 -I example/iosUITests/timeline.py build/ui-tests/<udid>
set -u
UDID=$1; shift
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT="$ROOT/build/ui-tests/$UDID"
DERIVED="$ROOT/build/ui-tests/derived"
APP="$DERIVED/Build/Products/Debug-iphonesimulator/NaviteExample.app"
rm -rf "$OUT"; mkdir -p "$OUT/shots"

xcrun simctl boot "$UDID" 2>/dev/null; xcrun simctl bootstatus "$UDID" -b >/dev/null

echo "Building example app..."
xcodebuild -project "$ROOT/example/iosApp/iosApp.xcodeproj" -scheme example.iosApp -configuration Debug \
  -destination "platform=iOS Simulator,id=$UDID" -derivedDataPath "$DERIVED" build > "$OUT/app-build.log" 2>&1 \
  || { echo "example app build failed: $OUT/app-build.log"; exit 1; }
xcrun simctl terminate "$UDID" zone.ien.navite.example 2>/dev/null
xcrun simctl install "$UDID" "$APP"

xcrun simctl spawn "$UDID" log stream --level debug --style compact \
  --predicate 'process == "NaviteExample"' > "$OUT/app.log" 2>&1 &
LOG_PID=$!
( while true; do
    T=$(python3 -c 'import time; print(int(time.time() * 1000))')
    xcrun simctl io "$UDID" screenshot --type=png "$OUT/shots/$T.png" >/dev/null 2>&1
  done ) &
SHOT_PID=$!

echo "Running UI tests..."
xcodebuild test -project "$ROOT/example/iosUITests/iosUITests.xcodeproj" -scheme UITests \
  -destination "platform=iOS Simulator,id=$UDID" -derivedDataPath "$DERIVED" "$@" > "$OUT/xcodebuild.log" 2>&1
STATUS=$?
kill $SHOT_PID 2>/dev/null; sleep 1; kill $LOG_PID 2>/dev/null

grep -aE "Test Case .*(passed|failed)|error:" "$OUT/xcodebuild.log"
echo "xcodebuild exit $STATUS (output: $OUT)"
exit $STATUS
