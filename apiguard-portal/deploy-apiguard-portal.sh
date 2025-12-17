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

echo "[INFO] Building Apiguard Portal..."

mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "[ERROR] Build Failed!"
    exit 1
fi

MULE_HOME="$SCRIPT_DIR/../mule/mule-enterprise-standalone-4.10.1"
if [ ! -d "$MULE_HOME" ]; then
    MULE_HOME="/c/raks/mule-enterprise-standalone-4.10.1"
fi

echo "[INFO] Deploying..."
cp "target/apiguard-portal-1.0.0-SNAPSHOT-mule-application.jar" "$MULE_HOME/apps/apiguard-portal.jar"

if [ $? -eq 0 ]; then
    echo "[INFO] Deployment Successful!"
else
    echo "[ERROR] Deployment Failed!"
    exit 1
fi
