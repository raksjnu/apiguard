#!/bin/bash

echo "=== Aegis Quick Deploy ==="

# Set JAVA_HOME (Adjust path if not running in Git Bash on Windows)
# Optional: Uncomment and set JAVA_HOME if the system JAVA_HOME is missing or incorrect
# export JAVA_HOME="/c/Program Files/Java/jdk-17"

if [ -n "$JAVA_HOME" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi

java -version >/dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "ERROR: Java not found!"
    echo "Please ensure Java is installed or set JAVA_HOME in this script."
    exit 1
fi

echo "[1/3] Copying latest rules.yaml to wrapper resources..."
cp "src/main/resources/rules/rules.yaml" "apiguardwrapper/src/main/resources/web/sample-rules.yaml"

echo "Building Aegis Core and Wrapper..."
mvn clean package -DskipTests -q
if [ $? -ne 0 ]; then
    echo "ERROR: Aegis build failed!"
    exit 1
fi
echo "Aegis JAR built successfully"

echo "[2/3] Copying JAR to wrapper..."
cp -f "target/aegis-1.0.0-jar-with-raks.jar" "apiguardwrapper/lib/aegis-1.0.0-jar-with-raks.jar"
echo "JAR copied to wrapper/lib"

echo "[3/3] Rebuilding wrapper application..."
cd apiguardwrapper
mvn clean package -DskipTests -q
if [ $? -ne 0 ]; then
    echo "ERROR: wrapper build failed!"
    cd ..
    exit 1
fi
cd ..
echo "Wrapper built successfully"

echo "=== DEPLOYMENT COMPLETE ==="
echo "Next step: RESTART Anypoint Studio"
