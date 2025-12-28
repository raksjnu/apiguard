#!/bin/bash

# ===================================================================
# ApiGuard Wrapper - Deployment Script (Linux/Mac)
# ===================================================================

# Resolve script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# --- Java Version Check ---
PREFERRED_JAVA_HOME="C:/Program Files/Java/jdk-17" 
if [ -d "$PREFERRED_JAVA_HOME" ]; then
    export JAVA_HOME="$PREFERRED_JAVA_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
fi
# --------------------------

# Configure the JAR file path
JAR_PATH="$SCRIPT_DIR/target/apiguardwrapper-1.0.0-mule-application.jar"
APP_NAME="apiguardwrapper.jar"

# Configure Mule Runtime location (Relative to script location)
# If you need a different location, set MULE_RUNTIME_HOME environment variable
if [ -n "$MULE_RUNTIME_HOME" ]; then
    MULE_HOME="$MULE_RUNTIME_HOME"
else
    MULE_HOME="$SCRIPT_DIR/../mule-enterprise-standalone-4.10.1"
fi

MULE_APPS="$MULE_HOME/apps"

echo "[INFO] ApiGuard Wrapper Deployment"
echo "[INFO] ========================================"
echo "[INFO] Source JAR: $JAR_PATH"
echo "[INFO] Target: $MULE_APPS/$APP_NAME"
echo ""

# Check if source JAR exists
if [ ! -f "$JAR_PATH" ]; then
    echo "[ERROR] Source JAR not found: $JAR_PATH"
    echo "[ERROR] Please run ./build-apiguardwrapper.sh first."
    exit 1
fi

# Check if Mule apps directory exists
if [ ! -d "$MULE_APPS" ]; then
    echo "[ERROR] Mule apps directory not found: $MULE_APPS"
    echo "[ERROR] Please verify MULE_HOME is correct."
    exit 1
fi

echo "[INFO] Deploying to Standalone Runtime..."
cp "$JAR_PATH" "$MULE_APPS/$APP_NAME"

if [ $? -ne 0 ]; then
    echo "[ERROR] Deployment Copy Failed!"
    exit 1
fi

echo ""
echo "[INFO] ========================================"
echo "[INFO] Deployment Successful!"
echo "[INFO] ========================================"
echo "[INFO] Monitor the Mule Runtime console for startup logs."
echo "[INFO] The application will be available at: http://localhost:8081/apiguard"
echo ""
