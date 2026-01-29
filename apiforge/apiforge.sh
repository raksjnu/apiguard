#!/bin/bash
# ====================================================
# ApiForge Launcher (Linux/Mac)
# ====================================================

# --- Architecture Configuration ---
# Default Heap Size (Edit this for larger baselines)
HEAP_SIZE="2G"

# check for Java
if ! command -v java &> /dev/null; then
    echo "[ERROR] Java is not installed or not in system PATH."
    echo "Please install Java 17+ and try again."
    exit 1
fi

echo "[INFO] Starting ApiForge with $HEAP_SIZE Heap Memory..."
echo "[INFO] Launching GUI..."

java -Xmx$HEAP_SIZE -jar apiforge-1.0.0.jar --gui &
