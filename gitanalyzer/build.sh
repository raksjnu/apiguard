#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ==========================================
# USER CONFIGURATION
# ==========================================
# If your java is not in PATH, set it here.
# For MacOS: /Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
# export JAVA_HOME="/path/to/your/jdk17"

# ==========================================

if [ -n "$JAVA_HOME" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "[INFO] Using JAVA_HOME: $JAVA_HOME"
fi

echo "[INFO] Building GitAnalyzer..."
mvn clean package -DskipTests
