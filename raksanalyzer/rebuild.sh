#!/bin/bash
# ===================================================================
# RaksAnalyzer - Build Script
# ===================================================================

cd "$(dirname "$0")"

# Java Version Check
PREFERRED_JAVA_HOME="/usr/lib/jvm/java-17-openjdk"

if [ -d "$PREFERRED_JAVA_HOME" ]; then
    export JAVA_HOME="$PREFERRED_JAVA_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "[INFO] Using JDK 17 at: $JAVA_HOME"
else
    echo "[WARN] Preferred JDK 17 not found, using system JAVA_HOME"
fi

echo ""
echo "============================================================"
echo "  Building RaksAnalyzer"
echo "============================================================"
echo ""

mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo ""
    echo "  BUILD SUCCESSFUL!"
    echo ""
else
    echo ""
    echo "  BUILD FAILED!"
    echo ""
    exit 1
fi
