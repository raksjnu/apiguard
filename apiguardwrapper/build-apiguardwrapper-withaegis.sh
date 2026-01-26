#!/bin/bash
# ===================================================================
# ApiGuard Wrapper - Build Script with Aegis Only
# ===================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# --- Java Version Check ---
if [ -z "$JAVA_HOME" ]; then
    PREFERRED_JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
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
# --------------------------

echo
echo "============================================================"
echo "  ApiGuard Wrapper - Build with Aegis Only"
echo "============================================================"
echo

# Step 1: Build & Install Aegis
echo "[1/2] Building & Installing Aegis..."
echo "============================================================"
if [ -d "$SCRIPT_DIR/../aegis" ]; then
    cd "$SCRIPT_DIR/../aegis"
    mvn clean install -DskipTests
    
    if [ $? -ne 0 ]; then
        echo
        echo "[ERROR] Aegis build failed!"
        exit 1
    fi
    
    # Copy aegis JAR
    echo
    echo "[INFO] Copying aegis FAT JAR to lib folder..."
    if [ -f "$SCRIPT_DIR/../aegis/target/aegis-1.0.0.jar" ]; then
        cp -f "$SCRIPT_DIR/../aegis/target/aegis-1.0.0.jar" "$SCRIPT_DIR/lib/aegis-1.0.0.jar"
        echo "[INFO] aegis fat JAR copied successfully."
    elif [ -f "$HOME/.m2/repository/com/raks/aegis/1.0.0/aegis-1.0.0.jar" ]; then
        cp -f "$HOME/.m2/repository/com/raks/aegis/1.0.0/aegis-1.0.0.jar" "$SCRIPT_DIR/lib/"
        echo "[INFO] Fallback: Standard aegis-1.0.0.jar copied from .m2"
    else
        echo "[ERROR] No aegis JAR found."
        exit 1
    fi

    # Copy Web Resources
    echo
    echo "[INFO] Copying Aegis web resources..."
    if [ -d "$SCRIPT_DIR/../aegis/src/main/resources/web/aegis" ]; then
        mkdir -p "$SCRIPT_DIR/src/main/resources/web/aegis"
        cp -r "$SCRIPT_DIR/../aegis/src/main/resources/web/aegis/"* "$SCRIPT_DIR/src/main/resources/web/aegis/"
        echo "[INFO] Web resources copied."
    fi


    # Copy rules.yaml
    echo
    echo "[INFO] Copying rules.yaml to wrapper resources..."
    if [ -f "$SCRIPT_DIR/../aegis/src/main/resources/rules/rules.yaml" ]; then
        mkdir -p "$SCRIPT_DIR/src/main/resources/rules"
        cp -f "$SCRIPT_DIR/../aegis/src/main/resources/rules/rules.yaml" "$SCRIPT_DIR/src/main/resources/rules/"
        echo "[INFO] rules.yaml copied successfully."
    else
        echo "[WARN] rules.yaml not found in aegis source!"
    fi

else
    echo "[ERROR] Aegis project not found at ../aegis"
    exit 1
fi

# Step 2: Build ApiGuardWrapper
echo
echo "[2/2] Building ApiGuardWrapper..."
echo "============================================================"
cd "$SCRIPT_DIR"
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo
    echo "[ERROR] ApiGuardWrapper build failed!"
    exit 1
fi

echo
echo "============================================================"
echo "  BUILD SUCCESSFUL!"
echo "============================================================"
echo
echo "Wrapper JAR: $SCRIPT_DIR/target/apiguardwrapper-1.0.0-mule-application.jar"
echo
