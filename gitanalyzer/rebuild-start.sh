#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "[INFO] Rebuilding and Starting GitAnalyzer..."

./build.sh
if [ $? -ne 0 ]; then
    echo "[ERROR] Build failed. Aborting start."
    exit 1
fi

./start.sh "$@"
