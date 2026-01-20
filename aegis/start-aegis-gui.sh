#!/bin/bash
# Aegis GUI Launcher for Mac/Linux
# You can change the port by setting the AEGIS_PORT environment variable
# Example: export AEGIS_PORT=9090 before running this script

echo "╔════════════════════════════════════════════════════════════╗"
echo "║          Aegis GUI Launcher                                ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Resolve JAR file path - prioritize fat JAR in target
IF_TARGET_JAR="target/aegis-1.0.0-jar-with-raks.jar"
IF_ROOT_JAR="aegis.jar"

if [ -f "$IF_TARGET_JAR" ]; then
    JAR_FILE="$IF_TARGET_JAR"
    echo "[INFO] Using latest built JAR: $JAR_FILE"
elif [ -f "$IF_ROOT_JAR" ]; then
    JAR_FILE="$IF_ROOT_JAR"
    echo "[INFO] Using JAR: $JAR_FILE"
else
    echo "ERROR: JAR file not found!"
    echo "Please run: ./build-aegis.sh"
    echo ""
    read -p "Press Enter to exit..."
    exit 1
fi

# Set default port if not specified
if [ -z "$AEGIS_PORT" ]; then
    AEGIS_PORT=8080
fi

# Optional: Uncomment and set JAVA_HOME if the system JAVA_HOME is missing or incorrect
# export JAVA_HOME="/c/Program Files/Java/jdk-17"

# Find Java command
if [ -n "$JAVA_HOME" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
else
    JAVA_CMD="java"
fi

# Check Java Version
echo "Using Java: $JAVA_CMD"
"$JAVA_CMD" -version
echo ""

echo Starting Aegis GUI on port $AEGIS_PORT...
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

"$JAVA_CMD" -jar "$JAR_FILE" --gui --port $AEGIS_PORT

# Check exit status
if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: Failed to start Aegis GUI"
    echo ""
    read -p "Press Enter to exit..."
fi
