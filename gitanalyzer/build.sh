#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# --- Java Version Check ---
if [ -n "$JAVA_HOME" ]; then
    echo "[INFO] Using existing JAVA_HOME: $JAVA_HOME"
else
    if [ -d "/usr/lib/jvm/java-17-openjdk-amd64" ]; then
        export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
fi

echo "[INFO] Building GitAnalyzer..."
mvn clean package -DskipTests
