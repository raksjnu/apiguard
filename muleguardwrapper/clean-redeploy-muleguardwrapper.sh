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

echo "[INFO] Clean Redeploy of MuleGuardWrapper..."

# 1. Clean
./mvn-clean.bat # Use bat or mvn clean directly if on linux? Assumes mvn is available.
mvn clean

# 2. Deploy (using the new script)
./deploy-muleguardwrapper.sh
