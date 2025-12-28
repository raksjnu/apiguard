#!/bin/bash
# ==========================================
#   API Discovery Tool - Rebuild & Restart
# ==========================================

# Set JAVA_HOME to JDK 17
export JAVA_HOME="/c/Program Files/Java/jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"

echo ""
echo "=========================================="
echo "   Rebuilding API Discovery Tool"
echo "=========================================="
echo ""

# Stop existing server
echo "[INFO] Stopping existing server on port 8085..."
PID=$(lsof -ti:8085 2>/dev/null)
if [ ! -z "$PID" ]; then
    kill -9 $PID 2>/dev/null
fi

# Build
echo "[INFO] Building with Maven..."
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "[ERROR] Build failed!"
    exit 1
fi

echo ""
echo "[SUCCESS] Build completed!"
echo ""

# Start server
echo "[INFO] Starting API Discovery Tool..."
./run-apidiscovery.sh &

echo ""
echo "[SUCCESS] Server started!"
echo "[INFO] Access at: http://localhost:8085"
echo ""
