#!/bin/bash

# ===================================================================
# ApiGuard Wrapper - Complete Dependency Build Script (Linux/Mac)
# ===================================================================
# This script:
# 1. Builds and Installs RaksAnalyzer (Dependency)
# 2. Builds and Installs ApiDiscovery (Dependency)
# 3. Builds and Installs API Forge (Dependency)
# 4. Builds and Installs GitAnalyzer (Dependency)
# 5. Builds and Installs Aegis (Dependency)
# 6. Builds the ApiGuardWrapper Mule application
# ===================================================================

# Resolve script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# --- Java Version Check ---
if [ -z "$JAVA_HOME" ]; then
    PREFERRED_JAVA_HOME="C:/Program Files/Java/jdk-17" 
    if [ -d "$PREFERRED_JAVA_HOME" ]; then
        export JAVA_HOME="$PREFERRED_JAVA_HOME"
    fi
fi

if [ -n "$JAVA_HOME" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "[INFO] Using JAVA_HOME: $JAVA_HOME"
else
    echo "[INFO] JAVA_HOME not set. Using java from system PATH.
fi

echo ""
echo "============================================================"
echo "  ApiGuard Wrapper - Full Dependency Build"
echo "============================================================"
echo ""

# Step 1: Build & Install RaksAnalyzer
echo "[1/6] Building & Installing RaksAnalyzer..."
echo "============================================================"
if [ -d "$SCRIPT_DIR/../raksanalyzer" ]; then
    cd "$SCRIPT_DIR/../raksanalyzer"
    mvn clean install -DskipTests
    
    if [ $? -ne 0 ]; then
        echo ""
        echo "[ERROR] RaksAnalyzer build failed!"
        exit 1
    fi
    
    # Copy raksanalyzer JAR to apiguardwrapper/lib
    echo ""
    echo "[INFO] Copying raksanalyzer JAR to lib folder..."
    if [ -f "$SCRIPT_DIR/../raksanalyzer/target/raksanalyzer-1.0.0.jar" ]; then
        cp "$SCRIPT_DIR/../raksanalyzer/target/raksanalyzer-1.0.0.jar" "$SCRIPT_DIR/lib/"
        echo "[INFO] raksanalyzer JAR copied successfully"
    else
        echo "[WARN] raksanalyzer JAR not found"
    fi
else
    echo "[ERROR] RaksAnalyzer project not found at ../raksanalyzer"
    exit 1
fi

# Step 2: Build & Install ApiDiscovery
echo ""
echo "[2/6] Building & Installing ApiDiscovery..."
echo "============================================================"
if [ -d "$SCRIPT_DIR/../apidiscovery" ]; then
    cd "$SCRIPT_DIR/../apidiscovery"
    mvn clean install -DskipTests
    
    if [ $? -ne 0 ]; then
        echo ""
        echo "[ERROR] ApiDiscovery build failed!"
        exit 1
    fi
    
    # Copy JAR to apiguardwrapper/lib
    echo ""
    echo "[INFO] Copying apidiscovery JAR to lib folder..."
    if [ -f "$SCRIPT_DIR/../apidiscovery/target/apidiscovery-1.0.0.jar" ]; then
        cp "$SCRIPT_DIR/../apidiscovery/target/apidiscovery-1.0.0.jar" "$SCRIPT_DIR/lib/"
        echo "[INFO] apidiscovery JAR copied successfully"
    else
        echo "[WARN] apidiscovery JAR not found"
    fi
else
    echo "[ERROR] ApiDiscovery project not found at ../apidiscovery"
    exit 1
fi

# Step 3: Build & Install API Forge
echo ""
echo "[3/6] Building & Installing API Forge..."
echo "============================================================"
if [ -d "$SCRIPT_DIR/../apiforge" ]; then
    cd "$SCRIPT_DIR/../apiforge"
    mvn clean install -DskipTests
    
    if [ $? -ne 0 ]; then
        echo ""
        echo "[ERROR] API Forge build failed!"
        exit 1
    fi
    
    # Copy JAR to apiguardwrapper/lib
    echo ""
    echo "[INFO] Copying apiforge JAR to lib folder..."
    if [ -f "$SCRIPT_DIR/../apiforge/target/apiforge-1.0.0.jar" ]; then
        cp "$SCRIPT_DIR/../apiforge/target/apiforge-1.0.0.jar" "$SCRIPT_DIR/lib/"
        echo "[INFO] apiforge JAR copied successfully"
    else
        echo "[WARN] apiforge JAR not found"
    fi

    # Sync Web Resources
    echo "[INFO] Synchronizing API Forge Resources..."
    mkdir -p "$SCRIPT_DIR/src/main/resources/web/apiforge"
    if [ -d "$SCRIPT_DIR/../apiforge/src/main/resources/public" ]; then
        cp -r "$SCRIPT_DIR/../apiforge/src/main/resources/public/"* "$SCRIPT_DIR/src/main/resources/web/apiforge/"
    fi
    cp "$SCRIPT_DIR/../apiforge/src/main/resources/config.yaml" "$SCRIPT_DIR/src/main/resources/web/apiforge/"
    mkdir -p "$SCRIPT_DIR/src/main/resources/web/apiforge/testData"
    cp -r "$SCRIPT_DIR/../apiforge/src/main/resources/testData/"* "$SCRIPT_DIR/src/main/resources/web/apiforge/testData/"
    echo "[INFO] API Forge resources synchronized successfully."
else
    echo "[ERROR] API Forge project not found"
    exit 1
fi

# Step 4: Build & Install GitAnalyzer
echo ""
echo "[4/6] Building & Installing GitAnalyzer..."
echo "============================================================"
if [ -d "$SCRIPT_DIR/../gitanalyzer" ]; then
    cd "$SCRIPT_DIR/../gitanalyzer"
    mvn clean install -DskipTests
    if [ $? -eq 0 ]; then
        cp "$SCRIPT_DIR/../gitanalyzer/target/gitanalyzer-1.0.0.jar" "$SCRIPT_DIR/lib/gitanalyzer-1.0.0.jar"
    fi
fi

# Step 5: Build & Install Aegis
echo ""
echo "[5/6] Building & Installing Aegis..."
echo "============================================================"
if [ -d "$SCRIPT_DIR/../aegis" ]; then
    cd "$SCRIPT_DIR/../aegis"
    mvn clean install -DskipTests
    if [ $? -eq 0 ]; then
        cp "$SCRIPT_DIR/../aegis/target/aegis-1.0.0.jar" "$SCRIPT_DIR/lib/"
        echo "[INFO] Synchronizing Aegis Web Resources..."
        mkdir -p "$SCRIPT_DIR/src/main/resources/web/aegis"
        cp -r "$SCRIPT_DIR/../aegis/src/main/resources/web/aegis/"* "$SCRIPT_DIR/src/main/resources/web/aegis/"
    fi
fi

# Step 6: Build ApiGuardWrapper
echo ""
echo "[6/6] Building ApiGuardWrapper..."
echo "============================================================"
cd "$SCRIPT_DIR"
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo ""
    echo "[ERROR] ApiGuardWrapper build failed!"
    exit 1
fi

echo ""
echo "============================================================"
echo "  BUILD SUCCESSFUL!"
echo "============================================================"
echo ""
echo "Wrapper JAR: $SCRIPT_DIR/target/apiguardwrapper-1.0.0-mule-application.jar"
echo ""
