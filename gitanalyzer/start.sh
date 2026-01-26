#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ==========================================
# USER CONFIGURATION
# ==========================================
# If your java is not in PATH, set it here.
# For MacOS, it is often: /Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
# For Linux: /usr/lib/jvm/java-17-openjdk-amd64
# export JAVA_HOME="/path/to/your/jdk17"

# ==========================================

if [ -n "$JAVA_HOME" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "[INFO] Using JAVA_HOME: $JAVA_HOME"
fi

# Check Port 6060 (MacOS/Linux)
PORT=6060
PID=$(lsof -ti :$PORT)
if [ -n "$PID" ]; then
    echo "[WARN] Port $PORT is use by PID $PID. Killing..."
    kill -9 $PID
fi

APP_HOME="$SCRIPT_DIR"
echo "[INFO] Starting GitAnalyzer..."
echo "[INFO] APP_HOME: $APP_HOME"

JAR_FILE=""
if [ -f "target/gitanalyzer-1.0.0.jar" ]; then
    JAR_FILE="target/gitanalyzer-1.0.0.jar"
else
    echo -e "${RED}[ERROR] JAR not found. Please run build.sh first.${NC}"
    exit 1
fi

# Run
java -Dapp.home="$APP_HOME" -jar "$JAR_FILE" "$@"
