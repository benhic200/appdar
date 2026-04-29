#!/bin/bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_GRADLE="$PROJECT_DIR/app/build.gradle.kts"

# Default: release build. Pass DEBUG=1 for debug build.
BUILD_TYPE="${DEBUG:-release}"
if [[ "$BUILD_TYPE" != "release" && "$BUILD_TYPE" != "debug" ]]; then
    echo "❌ Invalid BUILD_TYPE '$BUILD_TYPE'. Use 'release' or 'debug'."
    exit 1
fi

TASK_APK="assemble${BUILD_TYPE^}"
TASK_AAB="bundle${BUILD_TYPE^}"

# Output paths
if [[ "$BUILD_TYPE" == "release" ]]; then
    APK_PATH="$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"
    AAB_PATH="$PROJECT_DIR/app/build/outputs/bundle/release/app-release.aab"
else
    APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
    AAB_PATH="$PROJECT_DIR/app/build/outputs/bundle/debug/app-debug.aab"
fi

echo "🔧 Building next ${BUILD_TYPE} version of Appdar..."

# Extract current version code and version name
CURRENT_VERSION_CODE=$(grep -E "versionCode\s*=" "$BUILD_GRADLE" | grep -oE '[0-9]+')
CURRENT_VERSION_NAME=$(grep -E "versionName\s*=" "$BUILD_GRADLE" | grep -oE '"[^"]+"' | tr -d '"')
echo "Current: versionCode=$CURRENT_VERSION_CODE, versionName=$CURRENT_VERSION_NAME"

# Increment version code
NEXT_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))
NEXT_VERSION_NAME="1.$NEXT_VERSION_CODE"
echo "Next: versionCode=$NEXT_VERSION_CODE, versionName=$NEXT_VERSION_NAME"

# Update build.gradle.kts
sed -i.bak -E "s/(versionCode\s*=).*/\1 $NEXT_VERSION_CODE/" "$BUILD_GRADLE"
sed -i.bak -E "s/(versionName\s*=).*/\1 \"$NEXT_VERSION_NAME\"/" "$BUILD_GRADLE"
rm -f "$BUILD_GRADLE.bak"

echo "✅ Updated version in $BUILD_GRADLE"

# Build
echo "🏗️  Building ${BUILD_TYPE} APK + AAB..."
cd "$PROJECT_DIR"
./gradlew "$TASK_APK" "$TASK_AAB"

# Check APK exists
if [[ ! -f "$APK_PATH" ]]; then
    echo "❌ APK not found at $APK_PATH"
    ls -la "$(dirname "$APK_PATH")" 2>/dev/null || true
    exit 1
fi

APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
echo "✅ ${BUILD_TYPE^} APK built: $APK_PATH ($APK_SIZE)"

# Check AAB exists
if [[ -f "$AAB_PATH" ]]; then
    AAB_SIZE=$(du -h "$AAB_PATH" | cut -f1)
    echo "✅ ${BUILD_TYPE^} AAB built: $AAB_PATH ($AAB_SIZE)"
fi

# Check ADB device
echo "📱 Checking ADB devices..."
DEVICES=$(adb devices | grep -v "List of devices" | grep -v "^$" | wc -l)
if [[ $DEVICES -eq 0 ]]; then
    echo "❌ No ADB devices found. Connect your phone via USB and enable USB debugging."
    exit 1
fi

# For release builds, uninstall any existing debug version first
echo "📲 Installing APK..."
adb install -r "$APK_PATH"

echo "🎉 Successfully installed version $NEXT_VERSION_NAME ($NEXT_VERSION_CODE) to device."
echo "📦 APK: $APK_PATH"
echo "📦 AAB: $AAB_PATH"

# Create symlink to latest APK
LINK_PATH="$PROJECT_DIR/Appdar-latest.apk"
ln -sf "$APK_PATH" "$LINK_PATH"
echo "🔗 Latest APK symlink updated: $LINK_PATH"
