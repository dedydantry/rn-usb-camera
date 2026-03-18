#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
DEFAULT_AUSBC_SOURCE_DIR=$(CDPATH= cd -- "$REPO_ROOT/../AndroidUSBCamera" 2>/dev/null && pwd || true)
AUSBC_SOURCE_DIR=${1:-${RN_USB_CAMERA_AUSBC_SOURCE_DIR:-$DEFAULT_AUSBC_SOURCE_DIR}}
ANDROID_LIBS_DIR="$REPO_ROOT/android/libs"

if [ -z "$AUSBC_SOURCE_DIR" ] || [ ! -d "$AUSBC_SOURCE_DIR" ]; then
  echo "AUSBC source repo not found. Pass the path as the first argument or set RN_USB_CAMERA_AUSBC_SOURCE_DIR." >&2
  exit 1
fi

if [ ! -x "$AUSBC_SOURCE_DIR/gradlew" ]; then
  echo "Gradle wrapper not found in $AUSBC_SOURCE_DIR" >&2
  exit 1
fi

echo "Publishing AUSBC artifacts from $AUSBC_SOURCE_DIR"
(cd "$AUSBC_SOURCE_DIR" && ./gradlew publishAllPublicationsToLocalBuildRepoRepository)

mkdir -p "$ANDROID_LIBS_DIR"
rm -f \
  "$ANDROID_LIBS_DIR/libausbc.jar" \
  "$ANDROID_LIBS_DIR/libuvc.jar" \
  "$ANDROID_LIBS_DIR/libnative.jar"

cp "$AUSBC_SOURCE_DIR/libausbc/build/outputs/aar/libausbc-release.aar" "$ANDROID_LIBS_DIR/"
cp "$AUSBC_SOURCE_DIR/libuvc/build/outputs/aar/libuvc-release.aar" "$ANDROID_LIBS_DIR/"
cp "$AUSBC_SOURCE_DIR/libnative/build/outputs/aar/libnative-release.aar" "$ANDROID_LIBS_DIR/"

echo "AUSBC Maven repo published to $AUSBC_SOURCE_DIR/build/repo"
echo "AUSBC release AARs refreshed in $ANDROID_LIBS_DIR"