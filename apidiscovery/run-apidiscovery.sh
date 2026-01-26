#!/bin/bash
# ============================================
# API Discovery Tool - Unix/Linux/Mac Launcher
# ============================================

echo ""
echo "=========================================="
echo "   API Discovery Tool - Launcher"
echo "=========================================="
echo ""
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


# Kill any existing process on port 8085
echo "[INFO] Checking for existing process on port 8085..."
PID=$(lsof -ti:8085)
if [ ! -z "$PID" ]; then
    echo "[INFO] Found process $PID on port 8085, terminating..."
    kill -9 $PID 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "[INFO] Process $PID terminated successfully"
    else
        echo "[WARN] Could not kill process $PID"
    fi
    # Wait a moment for port to be released
    sleep 2
else
    echo "[INFO] No existing process found on port 8085"
fi

# Check if JAR exists
if [ ! -f "target/apidiscovery-1.0.0.jar" ]; then
    echo "[ERROR] JAR file not found. Please run 'mvn clean package' first."
    exit 1
fi

# Start the application
echo ""
echo "[INFO] Starting API Discovery Tool..."
echo ""
java -jar "target/apidiscovery-1.0.0.jar"

# Check exit code
if [ $? -ne 0 ]; then
    echo ""
    echo "[ERROR] Application exited with error code $?"
    exit 1
fi
