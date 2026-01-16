#!/bin/bash
# ===================================================================
# Aegis - Build Script (Linux/Mac)
# ===================================================================
# This script builds the Aegis Java project
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
echo "  Aegis - Build"
echo "============================================================"
echo ""

mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo ""
    echo "[ERROR] Aegis build failed!"
    exit 1
fi

echo ""
echo "============================================================"
echo "  BUILD SUCCESSFUL!"
echo "============================================================"
echo ""
echo "Aegis JAR: $SCRIPT_DIR/target/aegis-1.0.0-jar-with-raks.jar"
cp target/aegis-1.0.0-jar-with-raks.jar aegis.jar
echo "Copied to aegis.jar"
echo ""
