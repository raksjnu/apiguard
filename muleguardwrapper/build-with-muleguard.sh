#!/bin/bash
###################################################################
# MuleGuard Wrapper - Complete Build Script
###################################################################
# This script:
# 1. Builds the muleguard JAR
# 2. Copies it to muleguardwrapper/lib/
# 3. Builds the muleguardwrapper Mule application
###################################################################

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# --- Java Version Check ---
PREFERRED_JAVA_HOME="/usr/lib/jvm/java-17-openjdk"

if [ -d "$PREFERRED_JAVA_HOME" ]; then
    export JAVA_HOME="$PREFERRED_JAVA_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "[INFO] Using Preferred JDK 17 at: $JAVA_HOME"
else
    echo "[WARN] Preferred JDK 17 not found at: $PREFERRED_JAVA_HOME"
    echo "[INFO] Falling back to system JAVA_HOME: $JAVA_HOME"
fi
# --------------------------

echo ""
echo "============================================================"
echo "  MuleGuard Wrapper - Complete Build"
echo "============================================================"
echo ""

# Step 1: Build muleguard JAR
echo "[1/3] Building muleguard JAR..."
echo "============================================================"
cd "$SCRIPT_DIR/../muleguard"
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo ""
    echo "[ERROR] MuleGuard build failed!"
    exit 1
fi

echo ""
echo "[INFO] MuleGuard JAR built successfully!"
echo ""

# Step 2: Copy JAR to wrapper lib
echo "[2/3] Copying JAR to muleguardwrapper/lib..."
echo "============================================================"
cp "target/muleguard-1.0.0-jar-with-raks.jar" "$SCRIPT_DIR/lib/muleguard-1.0.0-jar-with-raks.jar"

if [ $? -ne 0 ]; then
    echo ""
    echo "[ERROR] JAR copy failed!"
    exit 1
fi

echo ""
echo "[INFO] JAR copied successfully!"
echo ""

# Step 3: Build wrapper
echo "[3/3] Building muleguardwrapper..."
echo "============================================================"
cd "$SCRIPT_DIR"
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo ""
    echo "[ERROR] MuleGuardWrapper build failed!"
    exit 1
fi

echo ""
echo "============================================================"
echo "  BUILD SUCCESSFUL!"
echo "============================================================"
echo ""
echo "MuleGuard JAR: $SCRIPT_DIR/lib/muleguard-1.0.0-jar-with-raks.jar"
echo "Wrapper JAR:   $SCRIPT_DIR/target/muleguardwrapper-1.0.0-mule-application.jar"
echo ""
echo "Next steps:"
echo "  - To deploy to Mule: run ./deploy-muleguardwrapper.sh"
echo "  - To clean redeploy:  run ./clean-redeploy-muleguardwrapper.sh"
echo ""
