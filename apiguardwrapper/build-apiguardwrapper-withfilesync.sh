#!/bin/bash
# ===================================================================
# ApiGuard Wrapper - Build with FileSync
# ===================================================================
# This script:
# 1. Builds and Installs FileSync (Dependency)
# 2. Builds the ApiGuardWrapper Mule application
# ===================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "============================================================"
echo "  ApiGuard Wrapper - Build with FileSync"
echo "============================================================"
echo ""

# Step 1: Build & Package FileSync
echo "[1/2] Building & Packaging FileSync..."
echo "============================================================"
if [ -d "$SCRIPT_DIR/../filesync" ]; then
    cd "$SCRIPT_DIR/../filesync"
    mvn clean install -DskipTests
    
    if [ $? -ne 0 ]; then
        echo ""
        echo "[ERROR] FileSync build failed!"
        exit 1
    fi
    
    # Copy filesync JAR to apiguardwrapper/lib
    echo ""
    echo "[INFO] Copying FileSync JAR to lib folder..."
    
    if [ -f "$SCRIPT_DIR/../filesync/target/filesync-1.0.0.jar" ]; then
        cp -f "$SCRIPT_DIR/../filesync/target/filesync-1.0.0.jar" "$SCRIPT_DIR/lib/filesync-1.0.0.jar"
        if [ $? -eq 0 ]; then
            echo "[INFO] filesync-1.0.0.jar copied successfully"
        else
            echo "[WARN] Failed to copy filesync JAR"
        fi
    else
        echo "[WARN] filesync-1.0.0.jar not found in target folder"
    fi
else
    echo "[ERROR] FileSync project not found at ../filesync"
    exit 1
fi

# Step 2: Build ApiGuardWrapper
echo ""
echo "============================================================"
echo "  Step 2: Building apiguardwrapper"
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
echo "Wrapper JAR: ${SCRIPT_DIR}/target/apiguardwrapper-1.0.0-mule-application.jar"
echo ""
