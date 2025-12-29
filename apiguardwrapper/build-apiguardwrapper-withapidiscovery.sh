#!/bin/bash
# ===================================================================
# ApiGuard Wrapper - Build with API Discovery
# ===================================================================
# This script:
# 1. Builds and Packages API Discovery (Dependency)
# 2. Builds the ApiGuardWrapper Mule application
# ===================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# --- Java Version Check ---
# --- Java Version Check ---
if [ -z "$JAVA_HOME" ]; then
    PREFERRED_JAVA_HOME="C:/Program Files/Java/jdk-17" 
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

echo ""
echo "============================================================"
echo "  ApiGuard Wrapper - Build with API Discovery"
echo "============================================================"
echo ""

# Step 1: Build & Package API Discovery
echo "[1/2] Building & Packaging API Discovery..."
echo "============================================================"
if [ -d "$SCRIPT_DIR/../apidiscovery" ]; then
    cd "$SCRIPT_DIR/../apidiscovery"
    mvn clean package -DskipTests
    
    if [ $? -ne 0 ]; then
        echo ""
        echo "[ERROR] API Discovery build failed!"
        exit 1
    fi
    
    # Copy apidiscovery JAR to apiguardwrapper/lib
    echo ""
    echo "[INFO] Copying apidiscovery JAR to lib folder..."
    if [ -f "$SCRIPT_DIR/../apidiscovery/target/apidiscovery-1.0.0-with-raks.jar" ]; then
        cp "$SCRIPT_DIR/../apidiscovery/target/apidiscovery-1.0.0-with-raks.jar" "$SCRIPT_DIR/lib/apidiscovery-1.0.0.jar" && \echo "[INFO] apidiscovery-1.0.0.jar copied successfully" || echo "[WARN] Failed to copy apidiscovery JAR"
    else
        echo "[WARN] apidiscovery JAR not found in target"
    fi
else
    echo "[ERROR] API Discovery project not found at ../apidiscovery"
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
