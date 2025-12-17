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

JAR_PATH="$SCRIPT_DIR/target/muleguardwrapper-1.0.0-mule-application.jar"
APP_NAME="muleguardwrapper.jar"

# Relative path to Mule Home (up one level, then into mule)
MULE_HOME="$SCRIPT_DIR/../mule/mule-enterprise-standalone-4.10.1"

if [ ! -d "$MULE_HOME" ]; then
    echo "[INFO] Relative MULE_HOME not found, trying absolute fallback..."
    MULE_HOME="/c/raks/mule-enterprise-standalone-4.10.1"
fi

MULE_APPS="$MULE_HOME/apps"

echo "[INFO] Source JAR: $JAR_PATH"
echo "[INFO] Target: $MULE_APPS/$APP_NAME"

if [ ! -f "$JAR_PATH" ]; then
    echo "[ERROR] Source JAR not found: $JAR_PATH"
    exit 1
fi

if [ ! -d "$MULE_APPS" ]; then
    echo "[ERROR] Mule apps directory not found: $MULE_APPS"
    exit 1
fi

echo "[INFO] Deploying..."
cp "$JAR_PATH" "$MULE_APPS/$APP_NAME"

if [ $? -eq 0 ]; then
    echo "[INFO] Deployment Successful!"
else
    echo "[ERROR] Deployment Failed!"
    exit 1
fi
