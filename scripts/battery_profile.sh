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
APP_ID="com.benhic.appdar"
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
# adb shell pm grant "$APP_ID" android.permission.ACCESS_FINE_LOCATION
# adb shell pm grant "$APP_ID" android.permission.ACCESS_COARSE_LOCATION
# adb shell appops set "$APP_ID" android:mock_location allow

# Enable mock location (requires developer options)
# adb shell settings put secure mock_location 1

# Start the app's main activity to ensure services are running
echo "Starting app..."
adb shell am start -n "$APP_ID/.MainActivity" >/dev/null 2>&1 || true

# Simulate location changes to trigger geofencing (skipped)
echo "Skipping location simulation (device real location used)..."

# Let geofencing run for the specified duration
echo "Monitoring geofencing for $DURATION seconds..."
sleep "$DURATION"

# Capture bug report
echo "Capturing bug report..."
adb bugreport "$BUGREPORT"
echo "Bug report saved: $BUGREPORT"

# Run Battery Historian
echo "Starting Battery Historian on port $HISTORIAN_PORT..."
if docker run -d -p "$HISTORIAN_PORT":9999 -v "$(pwd)":/bugreports gcr.io/android-battery-historian/battery-historian --bugreport "/bugreports/$BUGREPORT" >/dev/null 2>&1; then
    echo "Battery Historian is running at http://localhost:$HISTORIAN_PORT"
    echo "Analyze the report and look for:"
    echo "  - gnss / network_location wake‑locks"
    echo "  - Geofencing‑related JobScheduler jobs"
    echo "  - LocationManagerService GEOFENCE entries"
    echo "  - Overall battery drain attributable to geofencing"
    echo ""
    echo "When done, stop the container with: docker stop \$(docker ps -q --filter ancestor=gcr.io/android-battery-historian/battery-historian)"
else
    echo "⚠️  Docker pull failed (requires GCP authentication)."
    echo ""
    echo "Your bug report is ready: $BUGREPORT"
    echo ""
    echo "To analyze it, choose one of these options:"
    echo ""
    echo "Option 1: Web upload (easiest)"
    echo "   1. Open https://battery-historian.appspot.com/"
    echo "   2. Click 'Choose File' and select '$BUGREPORT'"
    echo "   3. Click 'Submit' and wait for analysis"
    echo ""
    echo "Option 2: Set up GCP authentication (for local Docker)"
    echo "   1. Install Google Cloud SDK:"
    echo "      curl https://sdk.cloud.google.com | bash"
    echo "      exec -l \$SHELL"
    echo "   2. Log in to your Google account:"
    echo "      gcloud auth login"
    echo "   3. Configure Docker to use gcloud as credential helper:"
    echo "      gcloud auth configure-docker gcr.io"
    echo "   4. Re-run this script"
    echo ""
    echo "Option 3: Use Python parser from battery-historian repo"
    echo "   1. Clone the repository:"
    echo "      git clone https://github.com/google/battery-historian"
    echo "   2. Run the Python parser:"
    echo "      cd battery-historian/tools"
    echo "      python3 parse_battery_history.py \"$BUGREPORT\""
    echo ""
    echo "To skip analysis, ignore this error. The bug report is saved."
fi