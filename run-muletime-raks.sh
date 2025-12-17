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

MULE_HOME="$SCRIPT_DIR/mule/mule-enterprise-standalone-4.10.1"
if [ ! -d "$MULE_HOME" ]; then
    MULE_HOME="/c/raks/mule-enterprise-standalone-4.10.1"
fi
export MULE_HOME
export MULE_BASE="$MULE_HOME"

echo "[INFO] Starting Mule Runtime..."
echo "[INFO] MULE_HOME=$MULE_HOME"

if [ -f "$MULE_HOME/bin/mule" ]; then
    cd "$MULE_HOME/bin"
    ./mule
else
    echo "[ERROR] Mule executable not found at $MULE_HOME/bin/mule"
    exit 1
fi
