#!/bin/bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_GRADLE="$PROJECT_DIR/app/build.gradle.kts"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

echo "🔧 Building next version of Appdar..."

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

# Build debug APK
echo "🏗️  Building debug APK..."
cd "$PROJECT_DIR"
./gradlew assembleDebug

if [[ ! -f "$APK_PATH" ]]; then
    echo "❌ APK not found at $APK_PATH"
    exit 1
fi

APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
echo "✅ APK built: $APK_PATH ($APK_SIZE)"

# Check ADB device
echo "📱 Checking ADB devices..."
DEVICES=$(adb devices | grep -v "List of devices" | grep -v "^$" | wc -l)
if [[ $DEVICES -eq 0 ]]; then
    echo "❌ No ADB devices found. Connect your phone via USB and enable USB debugging."
    exit 1
fi

echo "📲 Installing APK..."
adb install -r "$APK_PATH"

echo "🎉 Successfully installed version $NEXT_VERSION_NAME ($NEXT_VERSION_CODE) to device."
echo "📦 APK saved as $APK_PATH"

# Optional: create symlink to latest APK
LINK_PATH="$PROJECT_DIR/Appdar-latest.apk"
ln -sf "$APK_PATH" "$LINK_PATH"
echo "🔗 Latest APK symlink updated: $LINK_PATH"