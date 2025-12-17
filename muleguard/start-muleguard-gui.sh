#!/bin/bash
# MuleGuard GUI Launcher for Mac/Linux
# You can change the port by setting the MULEGUARD_PORT environment variable
# Example: export MULEGUARD_PORT=9090 before running this script

echo "╔════════════════════════════════════════════════════════════╗"
echo "║          MuleGuard GUI Launcher                            ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Check if JAR exists
if [ ! -f "target/muleguard-1.0.0-jar-with-raks.jar" ]; then
    echo "ERROR: JAR file not found!"
    echo "Please run: mvn clean package"
    echo ""
    read -p "Press Enter to exit..."
    exit 1
fi

# Set default port if not specified
if [ -z "$MULEGUARD_PORT" ]; then
    MULEGUARD_PORT=8080
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

echo "Starting MuleGuard GUI on port $MULEGUARD_PORT..."
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

"$JAVA_CMD" -cp target/muleguard-1.0.0-jar-with-raks.jar com.raks.muleguard.gui.MuleGuardGUI $MULEGUARD_PORT

# Check exit status
if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: Failed to start MuleGuard GUI"
    echo ""
    read -p "Press Enter to exit..."
fi
