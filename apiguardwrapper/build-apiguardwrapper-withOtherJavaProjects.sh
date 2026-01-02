#!/bin/bash

# ===================================================================
# ApiGuard Wrapper - Complete Dependency Build Script (Linux/Mac)
# ===================================================================
# This script:
# 1. Builds and Installs RaksAnalyzer (Dependency)
# 2. Builds and Installs MuleGuard (Dependency)
# 3. Builds and Installs ApiDiscovery (Dependency)
# 4. Builds the ApiGuardWrapper Mule application
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
    echo "[INFO] JAVA_HOME not set. Using java from system PATH."
fi

echo ""
echo "============================================================"
echo "  ApiGuard Wrapper - Full Dependency Build"
echo "============================================================"
echo ""

# Step 1: Build & Install RaksAnalyzer
echo "[1/4] Building & Installing RaksAnalyzer..."
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
    RAKS_JAR="$HOME/.m2/repository/com/raks/raksanalyzer/1.0.0/raksanalyzer-1.0.0.jar"
    if [ -f "$RAKS_JAR" ]; then
        cp "$RAKS_JAR" "$SCRIPT_DIR/lib/raksanalyzer-1.0.0.jar"
        if [ $? -eq 0 ]; then
            echo "[INFO] raksanalyzer-1.0.0.jar copied successfully"
        else
            echo "[WARN] Failed to copy raksanalyzer JAR"
        fi
    else
        echo "[WARN] raksanalyzer JAR not found in .m2 repository"
    fi
else
    echo "[ERROR] RaksAnalyzer project not found at ../raksanalyzer"
    exit 1
fi

# Step 2: Build & Install MuleGuard
echo ""
echo "[2/4] Building & Installing MuleGuard..."
echo "============================================================"
if [ -d "$SCRIPT_DIR/../muleguard" ]; then
    cd "$SCRIPT_DIR/../muleguard"
    mvn clean install -DskipTests
    
    if [ $? -ne 0 ]; then
        echo ""
        echo "[ERROR] MuleGuard build failed!"
        exit 1
    fi
    
    # Copy muleguard JAR to apiguardwrapper/lib
    echo ""
    echo "[INFO] Copying muleguard JAR to lib folder..."
    MULEGUARD_JAR="$HOME/.m2/repository/com/raks/muleguard/1.0.0/muleguard-1.0.0.jar"
    if [ -f "$MULEGUARD_JAR" ]; then
        cp "$MULEGUARD_JAR" "$SCRIPT_DIR/lib/muleguard-1.0.0.jar"
        if [ $? -eq 0 ]; then
            echo "[INFO] muleguard-1.0.0.jar copied successfully"
        else
            echo "[WARN] Failed to copy muleguard JAR"
        fi
    else
        echo "[WARN] muleguard JAR not found in .m2 repository"
    fi
else
    echo "[ERROR] MuleGuard project not found at ../muleguard"
    exit 1
fi

# Step 3: Build & Install ApiDiscovery
echo ""
echo "[3/4] Building & Installing ApiDiscovery..."
echo "============================================================"
if [ -d "$SCRIPT_DIR/../apidiscovery" ]; then
    cd "$SCRIPT_DIR/../apidiscovery"
    mvn clean install -DskipTests
    
    if [ $? -ne 0 ]; then
        echo ""
        echo "[ERROR] ApiDiscovery build failed!"
        exit 1
    fi
    
    # Copy apidiscovery JAR to apiguardwrapper/lib
    echo ""
    echo "[INFO] Copying apidiscovery JAR to lib folder..."
    DISCOVERY_JAR="$HOME/.m2/repository/com/raks/apidiscovery/1.0.0/apidiscovery-1.0.0.jar"
    if [ -f "$DISCOVERY_JAR" ]; then
        cp "$DISCOVERY_JAR" "$SCRIPT_DIR/lib/apidiscovery-1.0.0.jar"
        if [ $? -eq 0 ]; then
            echo "[INFO] apidiscovery-1.0.0.jar copied successfully"
        else
            echo "[WARN] Failed to copy apidiscovery JAR"
        fi
    else
        echo "[WARN] apidiscovery JAR not found in .m2 repository"
    fi
else
    echo "[ERROR] ApiDiscovery project not found at ../apidiscovery"
    exit 1
fi

# Step 4: Build ApiGuardWrapper
echo ""
echo "[4/4] Building ApiGuardWrapper..."
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
