#!/bin/bash
# Battery Historian profiling script for Nearby Apps Widget
# Usage: ./scripts/battery_profile.sh [duration_seconds]
# Requires: adb, docker, and an Android device/emulator connected

set -e

DURATION=${1:-1800}  # default 30 minutes
BUGREPORT="bugreport-$(date +%Y%m%d-%H%M%S).zip"
HISTORIAN_PORT=9999

echo "=== Battery Historian Profiling ==="
echo "Duration: $DURATION seconds"
echo "Bugreport file: $BUGREPORT"

# Check prerequisites
command -v adb >/dev/null 2>&1 || { echo "adb not found"; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "docker not found"; exit 1; }

# Ensure device is connected
DEVICE=$(adb devices | grep -E 'device$' | head -1 | cut -f1)
if [ -z "$DEVICE" ]; then
    echo "No Android device/emulator connected. Please connect one."
    exit 1
fi
echo "Target device: $DEVICE"

# Install app if not already installed
APP_ID="com.example.nearbyappswidget"
if ! adb shell pm list packages | grep -q "$APP_ID"; then
    echo "Installing app..."
    APK_PATH="./app/build/outputs/apk/debug/app-debug.apk"
    if [ ! -f "$APK_PATH" ]; then
        echo "APK not found at $APK_PATH. Build the app first."
        exit 1
    fi
    adb install -r "$APK_PATH"
fi

# Grant necessary permissions
echo "Granting permissions..."
adb shell pm grant "$APP_ID" android.permission.ACCESS_FINE_LOCATION
adb shell pm grant "$APP_ID" android.permission.ACCESS_COARSE_LOCATION
adb shell appops set "$APP_ID" android:mock_location allow

# Enable mock location (requires developer options)
adb shell settings put secure mock_location 1

# Start the app's main activity to ensure services are running
echo "Starting app..."
adb shell am start -n "$APP_ID/.MainActivity" >/dev/null 2>&1 || true

# Simulate location changes to trigger geofencing
echo "Simulating location changes (London -> Manchester)..."
LONDON_LAT=51.5074
LONDON_LNG=-0.1278
MANCHESTER_LAT=53.4808
MANCHESTER_LNG=-2.2426

# Move gradually between locations to simulate travel
STEPS=10
for i in $(seq 0 $STEPS); do
    LAT=$(echo "$LONDON_LAT + ($MANCHESTER_LAT - $LONDON_LAT) * $i / $STEPS" | bc -l)
    LNG=$(echo "$LONDON_LNG + ($MANCHESTER_LNG - $LONDON_LNG) * $i / $STEPS" | bc -l)
    echo "Setting location: $LAT, $LNG"
    adb shell "am broadcast -a com.example.nearbyappswidget.SET_MOCK_LOCATION --ef lat $LAT --ef lng $LNG" >/dev/null 2>&1 || true
    sleep 5
done

# Let geofencing run for the specified duration
echo "Monitoring geofencing for $DURATION seconds..."
sleep "$DURATION"

# Capture bug report
echo "Capturing bug report..."
adb bugreport "$BUGREPORT"
echo "Bug report saved: $BUGREPORT"

# Run Battery Historian
echo "Starting Battery Historian on port $HISTORIAN_PORT..."
docker run -d -p "$HISTORIAN_PORT":9999 batteryhistorian/batteryhistorian --bugreport "$BUGREPORT" >/dev/null 2>&1

echo "Battery Historian is running at http://localhost:$HISTORIAN_PORT"
echo "Analyze the report and look for:"
echo "  - gnss / network_location wake‑locks"
echo "  - Geofencing‑related JobScheduler jobs"
echo "  - LocationManagerService GEOFENCE entries"
echo "  - Overall battery drain attributable to geofencing"
echo ""
echo "When done, stop the container with: docker stop \$(docker ps -q --filter ancestor=batteryhistorian/batteryhistorian)"