#!/bin/bash
# ===================================================================
# ApiGuard Wrapper - Build Script with GitAnalyzer
# ===================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ -n "$JAVA_HOME" ]; then
    echo "[INFO] Using existing JAVA_HOME: $JAVA_HOME"
else
    if [ -d "/usr/lib/jvm/java-17-openjdk-amd64" ]; then
        export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
fi

echo ""
echo "============================================================"
echo "  ApiGuard Wrapper - Build with GitAnalyzer"
echo "============================================================"
echo ""

# Step 1: Build & Install GitAnalyzer
echo "[1/2] Building & Installing GitAnalyzer..."
echo "============================================================"
if [ -d "$SCRIPT_DIR/../gitanalyzer" ]; then
    cd "$SCRIPT_DIR/../gitanalyzer"
    mvn clean install -DskipTests
    
    if [ $? -ne 0 ]; then
        echo ""
        echo "[ERROR] GitAnalyzer build failed!"
        exit 1
    fi
    
    # Copy JAR to apiguardwrapper/lib
    echo ""
    echo "[INFO] Copying gitanalyzer JAR to lib folder..."
    cp "$SCRIPT_DIR/../gitanalyzer/target/gitanalyzer-1.0.0.jar" "$SCRIPT_DIR/lib/gitanalyzer-1.0.0.jar"
    if [ $? -eq 0 ]; then
        echo "[INFO] gitanalyzer-1.0.0.jar copied successfully"
    else
        echo "[WARN] Failed to copy gitanalyzer JAR"
    fi
else
    echo "[ERROR] GitAnalyzer project not found at ../gitanalyzer"
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
