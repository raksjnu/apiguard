#!/bin/bash
# ===================================================================
# RaksAnalyzer - Build Script (Linux/Mac)
# ===================================================================
# This script builds the RaksAnalyzer Java project
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
echo "  RaksAnalyzer - Build"
echo "============================================================"
echo ""

mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo ""
    echo "[ERROR] RaksAnalyzer build failed!"
    exit 1
fi

echo ""
echo "============================================================"
echo "  BUILD SUCCESSFUL!"
echo "============================================================"
echo ""
echo "RaksAnalyzer JAR: $SCRIPT_DIR/target/raksanalyzer-1.0.0.jar"
echo ""
