#!/usr/bin/env sh
set -eu

GRADLE_VERSION="8.11.1"
BASE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/vadana-bootstrap"
DIST_DIR="$BASE_DIR/gradle-$GRADLE_VERSION"
ZIP_FILE="$BASE_DIR/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$DIST_DIR/bin/gradle" ]; then
  mkdir -p "$BASE_DIR"
  if [ ! -f "$ZIP_FILE" ]; then
    URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    if command -v curl >/dev/null 2>&1; then
      curl -fL "$URL" -o "$ZIP_FILE"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ZIP_FILE" "$URL"
    else
      echo "curl or wget is required to bootstrap Gradle." >&2
      exit 1
    fi
  fi
  rm -rf "$DIST_DIR"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$ZIP_FILE" -d "$BASE_DIR"
  else
    echo "unzip is required to bootstrap Gradle." >&2
    exit 1
  fi
fi

exec "$DIST_DIR/bin/gradle" "$@"



