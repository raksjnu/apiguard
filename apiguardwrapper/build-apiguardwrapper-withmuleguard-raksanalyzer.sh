#!/bin/bash

# ===================================================================
# ApiGuard Wrapper - Complete Dependency Build Script (Linux/Mac)
# ===================================================================
# This script:
# 1. Builds and Installs RaksAnalyzer (Dependency)
# 2. Builds and Installs MuleGuard (Dependency)
# 3. Builds the ApiGuardWrapper Mule application
# ===================================================================

# Resolve script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# --- Java Version Check ---
PREFERRED_JAVA_HOME="C:/Program Files/Java/jdk-17" 
if [ -d "$PREFERRED_JAVA_HOME" ]; then
    export JAVA_HOME="$PREFERRED_JAVA_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "[INFO] Using Preferred JDK 17 at: $JAVA_HOME"
fi

echo ""
echo "============================================================"
echo "  ApiGuard Wrapper - Full Dependency Build"
echo "============================================================"
echo ""

# Step 1: Build & Install RaksAnalyzer
echo "[1/3] Building & Installing RaksAnalyzer..."
echo "============================================================"
if [ -d "$SCRIPT_DIR/../raksanalyzer" ]; then
    cd "$SCRIPT_DIR/../raksanalyzer"
    mvn clean install -DskipTests
    
    if [ $? -ne 0 ]; then
        echo ""
        echo "[ERROR] RaksAnalyzer build failed!"
        exit 1
    fi
else
    echo "[ERROR] RaksAnalyzer project not found at ../raksanalyzer"
    exit 1
fi

# Step 2: Build & Install MuleGuard
echo ""
echo "[2/3] Building & Installing MuleGuard..."
echo "============================================================"
if [ -d "$SCRIPT_DIR/../muleguard" ]; then
    cd "$SCRIPT_DIR/../muleguard"
    mvn clean install -DskipTests
    
    if [ $? -ne 0 ]; then
        echo ""
        echo "[ERROR] MuleGuard build failed!"
        exit 1
    fi
else
    echo "[ERROR] MuleGuard project not found at ../muleguard"
    exit 1
fi

# Step 3: Build ApiGuardWrapper
echo ""
echo "[3/3] Building ApiGuardWrapper..."
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
