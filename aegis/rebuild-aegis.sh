#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# --- Java Version Check ---
# Fallback to system or specific paths
if [ -n "$JAVA_HOME" ]; then
    echo "[INFO] Using existing JAVA_HOME: $JAVA_HOME"
else
    # Try common locations
    if [ -d "/usr/lib/jvm/jdk-17" ]; then
        export JAVA_HOME="/usr/lib/jvm/jdk-17"
    elif [ -d "/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home" ]; then
        export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"
    fi
    
    if [ -n "$JAVA_HOME" ]; then
        echo "[INFO] Found JDK 17 at: $JAVA_HOME"
        export PATH="$JAVA_HOME/bin:$PATH"
    else
        echo "[WARN] JDK 17 not explicit, using system default"
    fi
fi
# --------------------------

echo "Stopping java processes..."
pkill -f "java" || echo "No java processes found"

echo "Waiting 2 seconds..."
sleep 2

echo "Building Aegis..."
mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo ""
    echo "============================================================"
    echo "  BUILD SUCCESSFUL!"
    echo "============================================================"
    echo ""
    echo "You can now run: ./start-aegis-gui.sh"
else
    echo ""
    echo "============================================================"
    echo "  BUILD FAILED!"
    echo "============================================================"
fi
