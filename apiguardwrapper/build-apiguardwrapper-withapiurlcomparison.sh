#!/bin/bash
# ===================================================================
# ApiGuard Wrapper - Build Script with ApiUrlComparison Only
# ===================================================================
# This script:
# 1. Builds and Installs ApiUrlComparison (Dependency)
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
echo "  ApiGuard Wrapper - Build with ApiUrlComparison Only"
echo "============================================================"
echo ""

# Step 1: Build & Install ApiUrlComparison
echo "[1/2] Building & Installing ApiUrlComparison..."
echo "============================================================"
if [ -d "$SCRIPT_DIR/../apiurlcomparison" ]; then
    cd "$SCRIPT_DIR/../apiurlcomparison"
    mvn clean install -DskipTests
    
    if [ $? -ne 0 ]; then
        echo ""
        echo "[ERROR] ApiUrlComparison build failed!"
        exit 1
    fi
    
    # Copy apiurlcomparison JAR to apiguardwrapper/lib
    echo ""
    echo "[INFO] Copying apiurlcomparison JAR to lib folder..."
    if [ -f "$SCRIPT_DIR/../apiurlcomparison/target/apiurlcomparison-1.0.0-jar-with-raks.jar" ]; then
        cp "$SCRIPT_DIR/../apiurlcomparison/target/apiurlcomparison-1.0.0-jar-with-raks.jar" "$SCRIPT_DIR/lib/apiurlcomparison-1.0.0.jar"
        echo "[INFO] apiurlcomparison JAR copied successfully"
    else
        echo "[WARN] apiurlcomparison JAR not found in target"
    fi
else
    echo "[ERROR] ApiUrlComparison project not found at ../apiurlcomparison"
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
