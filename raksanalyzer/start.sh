#!/bin/bash
# ===================================================================
# RaksAnalyzer - Start Script
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
echo "  Starting RaksAnalyzer"
echo "============================================================"
echo ""

# Kill any process using port 8080
echo "[INFO] Checking for processes using port 8080..."
PID=$(lsof -t -i:8080)
if [ -n "$PID" ]; then
    echo "[INFO] Terminating process $PID on port 8080..."
    kill -9 $PID 2>/dev/null
fi
sleep 2

# Start the application
java -jar "target/raksanalyzer-1.0.0.jar"

echo ""
echo "[INFO] RaksAnalyzer has exited"
echo ""
