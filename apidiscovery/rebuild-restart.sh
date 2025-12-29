#!/bin/bash
# ==========================================
#   API Discovery Tool - Rebuild & Restart
# ==========================================

# Set JAVA_HOME (User can edit this specific path if needed)
CUSTOM_JAVA_HOME="/c/Program Files/Java/jdk-17"

# Logic to find Java
if [ -d "$CUSTOM_JAVA_HOME" ]; then
    export JAVA_HOME="$CUSTOM_JAVA_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
elif [ -x "/usr/libexec/java_home" ]; then
    # MacOS way
    export JAVA_HOME=$(/usr/libexec/java_home)
    export PATH="$JAVA_HOME/bin:$PATH"
else
    # Fallback to system java or assume it's in PATH
    echo "[INFO] Using system Default Java"
fi

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
