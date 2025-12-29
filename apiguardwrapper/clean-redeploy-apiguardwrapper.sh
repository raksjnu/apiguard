#!/bin/bash
# ===================================================================
# ApiGuard Wrapper - Clean Redeploy (Linux/Mac)
# ===================================================================

# Resolve script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "============================================================"
echo "  ApiGuard Wrapper - Clean Redeploy"
echo "============================================================"
echo ""

echo "[Step 1] Building Project..."
./build-apiguardwrapper.sh

if [ $? -ne 0 ]; then
    echo "[ERROR] Build failed. Aborting deployment."
    exit 1
fi

echo ""
echo "[Step 2] Deploying Project..."
./deploy-apiguardwrapper.sh

if [ $? -ne 0 ]; then
    echo "[ERROR] Deployment failed."
    exit 1
fi

echo ""
echo "[INFO] Clean Redeploy Complete!"
echo ""
