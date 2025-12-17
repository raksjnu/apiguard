#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# --- Java Version Check ---
if [ -n "$JAVA_HOME" ]; then
    echo "[INFO] Using existing JAVA_HOME: $JAVA_HOME"
else
    if [ -d "/usr/lib/jvm/jdk-17" ]; then
        export JAVA_HOME="/usr/lib/jvm/jdk-17"
    elif [ -d "/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home" ]; then
        export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"
    fi
    if [ -n "$JAVA_HOME" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
fi
# --------------------------

echo "Building ApiGuard Portal..."
mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo ""
    echo "============================================================"
    echo "  BUILD SUCCESSFUL!"
    echo "============================================================"
    echo ""
else
    echo ""
    echo "============================================================"
    echo "  BUILD FAILED!"
    echo "============================================================"
    exit 1
fi
