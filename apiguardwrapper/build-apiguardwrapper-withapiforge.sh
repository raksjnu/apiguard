#!/bin/bash
# ===================================================================
# ApiGuard Wrapper - Build Script with apiforge Only
# ===================================================================
# This script:
# 1. Builds and Installs apiforge (Dependency)
# 2. Builds the ApiGuardWrapper Mule application
# ===================================================================

# --- Java Version Check ---
if [ -z "$JAVA_HOME" ]; then
    PREFERRED_JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64" # Common Linux path, adjust as needed or rely on param
    if [ -d "$PREFERRED_JAVA_HOME" ]; then
        export JAVA_HOME="$PREFERRED_JAVA_HOME"
    fi
fi

if [ -n "$JAVA_HOME" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "[INFO] Using JAVA_HOME: $JAVA_HOME"
else
    echo "[INFO] JAVA_HOME not set. Using java from system PATH."
fi
# --------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo ""
echo "============================================================"
echo "  ApiGuard Wrapper - Build with apiforge Only"
echo "============================================================"
echo ""

# Step 1: Build & Install apiforge
echo "[1/2] Building & Installing apiforge..."
echo "============================================================"
if [ -d "$SCRIPT_DIR/../apiforge" ]; then
    cd "$SCRIPT_DIR/../apiforge"
    mvn clean install -DskipTests
    
    if [ $? -ne 0 ]; then
        echo ""
        echo "[ERROR] apiforge build failed!"
        exit 1
    fi
    
    # Copy apiforge JAR to apiguardwrapper/lib
    echo ""
    echo "[INFO] Copying apiforge JAR to lib folder..."
    if [ -f "$SCRIPT_DIR/../apiforge/target/apiforge-1.0.0.jar" ]; then
        cp "$SCRIPT_DIR/../apiforge/target/apiforge-1.0.0.jar" "$SCRIPT_DIR/lib/apiforge-1.0.0.jar"
        echo "[INFO] apiforge JAR copied successfully"
    else
        echo "[WARN] apiforge JAR not found in target"
    fi
    
    # Sync Config and Test Data
    echo "[INFO] Synchronizing Configuration and Test Data..."
    cp "$SCRIPT_DIR/../apiforge/src/main/resources/config.yaml" "$SCRIPT_DIR/src/main/resources/web/apiforge/"
    mkdir -p "$SCRIPT_DIR/src/main/resources/web/apiforge/testData"
    cp -r "$SCRIPT_DIR/../apiforge/src/main/resources/testData/"* "$SCRIPT_DIR/src/main/resources/web/apiforge/testData/"
    echo "[INFO] Resources, Config, and Test Data synchronized successfully."

else
    echo "[ERROR] apiforge project not found at ../apiforge"
    exit 1
fi

# Step 2: Build ApiGuardWrapper
echo ""
echo "[2/2] Building ApiGuardWrapper..."
echo "============================================================"
cd "$SCRIPT_DIR"
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo ""
    echo "[ERROR] ApiGuardWrapper build failed!"
    exit 1
fi

echo ""
echo "============================================================"
echo "  BUILD SUCCESSFUL!"
echo "============================================================"
echo ""
echo "Wrapper JAR: $SCRIPT_DIR/target/apiguardwrapper-1.0.0-mule-application.jar"
echo ""
