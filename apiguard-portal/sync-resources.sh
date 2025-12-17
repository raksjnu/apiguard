#!/bin/bash

# Function to detect real path
get_abs_filename() {
  # $1 : relative filename
  echo "$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
}

echo "[INFO] Syncing Raks Common Resources..."

# Try 1 level up
COMMON_DIR="$(cd "$(dirname "$0")/../apiguard-common" 2>/dev/null && pwd)"

# Try 2 levels up if not found
if [ ! -d "$COMMON_DIR" ]; then
    COMMON_DIR="$(cd "$(dirname "$0")/../../apiguard-common" 2>/dev/null && pwd)"
fi

if [ ! -d "$COMMON_DIR" ]; then
    echo "[ERROR] Common directory not found. Checked ../apiguard-common and ../../apiguard-common"
    exit 1
fi

TARGET_DIR="./src/main/resources/web/assets"
mkdir -p "$TARGET_DIR/images"
mkdir -p "$TARGET_DIR/css"

echo "[INFO] Found Common at: $COMMON_DIR"

echo "[INFO] Copying Images..."
cp -r "$COMMON_DIR/resources/images/"* "$TARGET_DIR/images/" 2>/dev/null || true

echo "[INFO] Copying CSS..."
cp -r "$COMMON_DIR/resources/css/"* "$TARGET_DIR/css/" 2>/dev/null || true

echo "[INFO] Resources Synced Successfully to $TARGET_DIR"
