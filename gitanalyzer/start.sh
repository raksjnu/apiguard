#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# --- Java Version Check ---
if [ -n "$JAVA_HOME" ]; then
    echo "[INFO] Using existing JAVA_HOME: $JAVA_HOME"
fi

APP_HOME="$SCRIPT_DIR"
echo "[INFO] Starting GitAnalyzer..."
echo "[INFO] APP_HOME: $APP_HOME"

if [ ! -f "target/gitanalyzer-1.0.0.jar" ]; then
    echo "[ERROR] JAR not found. Please run build.sh first. (Check target directory)"
    exit 1
fi

java -Dapp.home="$APP_HOME" -jar target/gitanalyzer-1.0.0.jar "$@"
