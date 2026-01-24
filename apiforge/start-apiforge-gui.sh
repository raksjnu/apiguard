#!/bin/bash
# Start the API Response Comparison Tool GUI
cd "$(dirname "$0")"

echo "Starting API Response Comparison Tool - GUI..."
echo "The GUI will open automatically in your default browser."
echo ""

# Check if JAR exists, build if not
if [ ! -f "target/apiforge-1.0.0-jar-with-raks.jar" ]; then
    echo "[INFO] JAR not found. Building..."
    mvn clean package -DskipTests
fi

echo "[INFO] Launching JAR in GUI mode..."
java -jar target/apiforge-1.0.0-jar-with-raks.jar --gui

# Keep the terminal open if there's an error
if [ $? -ne 0 ]; then
    echo ""
    echo "Press any key to exit..."
    read -n 1
fi
