#!/bin/bash

echo "========================================================"
echo "      Starting Security & License Audit"
echo "========================================================"

# Check for JAVA_HOME
if [ -z "$JAVA_HOME" ]; then
    echo "[ERROR] JAVA_HOME is not set. Please set it to a JDK 17+ installation."
    exit 1
fi

echo "[INFO] Using Java at: $JAVA_HOME"

# NVD API Key (Set here to avoid 403 errors)
export NVD_API_KEY="2e1b33c4-166a-4698-bf6f-686cf046d8fe"

# Check if first argument is a specific path
if [ -n "$1" ] && [ -d "$1" ] && [ -f "$1/pom.xml" ]; then
    echo "[INFO] Target Path Provided: $1"
    process_project "$1/pom.xml"
    exit 0
fi

# Default to Shallow Scan, enable Recursive with -r or --recursive
RECURSIVE_MODE=false
if [[ "$1" == "-r" || "$1" == "--recursive" ]]; then
    RECURSIVE_MODE=true
fi

if [ "$RECURSIVE_MODE" = true ]; then
    echo "[INFO] Mode: RECURSIVE (Finding all pom.xml files nested deep)"
    find . -type d \( -name "target" -o -name "test-data" -o -name "testdata" -o -name "reference" \) -prune -o -name "pom.xml" -type f -print | while read -r pom_file; do
        process_project "$pom_file"
    done
else
    echo "[INFO] Mode: SHALLOW (Scanning immediate sub-folders only)"
    echo "[INFO] Use '$0 --recursive' to scan recursively."
    echo "[INFO] Use '$0 <path>' to scan a specific project."
    
    # Iterate over immediate directories
    for d in */; do
        if [ -f "${d}pom.xml" ]; then
            process_project "${d}pom.xml"
        fi
    done
fi

fi

echo ""
echo "========================================================"
echo "      Scan Completed. Generating HTML Dashboard..."
echo "========================================================"

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

# Determine Report Path based on logic similar to batch (SCAN_ROOT logic needed or just use current dir logic)
# In the shell script, if $1 is set, that's the root. Else it's current dir.
SCAN_ROOT="."
if [ -n "$1" ] && [ -d "$1" ]; then
    SCAN_ROOT="$1"
fi

bash "$SCRIPT_DIR/generate-security-report.sh" "$SCAN_ROOT"

echo ""
echo "========================================================"
echo "      Committing Reports to Git..."
echo "========================================================"

echo "[INFO] Adding all generated Security Reports..."
git add "**/security_scan/Security_Audit_Consolidated_Report.html"
git commit -m "chore: Update Security Audit Reports [skip ci]"
echo "[INFO] Pushing changes..."
git push origin master

echo ""
echo "========================================================"
echo "      Audit Completed"
echo "========================================================"

exit 0

# Function to extract processing logic
# Function to extract processing logic
process_project() {
    local pom_file="$1"
    local project_dir=$(dirname "$pom_file")
    
    echo ""
    echo "--------------------------------------------------------"
    echo "Scanning Project: $project_dir"
    echo "--------------------------------------------------------"
    
    pushd "$project_dir" > /dev/null
    
    mkdir -p "security_scan"
    
    # Generate local script
    local script_file="security_scan/run-local-scan.sh"
    echo "#!/bin/bash" > "$script_file"
    echo "export NVD_API_KEY=\"$NVD_API_KEY\"" >> "$script_file"
    echo "mvn org.owasp:dependency-check-maven:12.1.0:check -Dformat=HTML -DoutputDirectory=. -DautoUpdate=true -DnvdApiKey=\$NVD_API_KEY -DfailOnError=false -DossindexAnalyzerEnabled=false" >> "$script_file"
    echo "mvn org.cyclonedx:cyclonedx-maven-plugin:2.7.9:makeAggregateBom -DoutputDirectory=." >> "$script_file"
    echo "mvn org.codehaus.mojo:license-maven-plugin:2.0.0:add-third-party -Dlicense.useMissingFile -Dlicense.outputDirectory=." >> "$script_file"
    echo "mvn dependency:tree -DoutputFile=dependency-tree.txt" >> "$script_file"
    chmod +x "$script_file"
    
    echo "[1/4] Generating Dependency Tree..."
    mvn dependency:tree -B -DoutputFile="security_scan/dependency-tree.txt"
    
    echo "[2/4] Generating SBOM (CycloneDX)..."
    mvn org.cyclonedx:cyclonedx-maven-plugin:2.7.9:makeAggregateBom -B -DoutputDirectory="security_scan"
    
    echo "[3/4] Generating License Report..."
    mvn org.codehaus.mojo:license-maven-plugin:2.0.0:add-third-party -B -Dlicense.useMissingFile -Dlicense.outputDirectory="security_scan"
    
    echo "[4/4] Checking for CVEs (OWASP Dependency Check)..."
    if [ -n "$NVD_API_KEY" ]; then
        mvn org.owasp:dependency-check-maven:12.1.0:check -B -Dformat=HTML -DoutputDirectory="security_scan" -DautoUpdate=true -DnvdApiKey=$NVD_API_KEY -DossindexAnalyzerEnabled=false
    else
        echo "[WARNING] NVD_API_KEY not set. You may experience 403 errors."
        mvn org.owasp:dependency-check-maven:12.1.0:check -B -Dformat=HTML -DoutputDirectory="security_scan" -DautoUpdate=true -DossindexAnalyzerEnabled=false
    fi
    
    echo ""
    echo "[REPORT] Reports generated in $project_dir/security_scan/"
    echo "  - Tree:         security_scan/dependency-tree.txt"
    echo "  - SBOM:         security_scan/bom.xml"
    echo "  - Licenses:     security_scan/THIRD-PARTY.txt"
    echo "  - CVEs:         security_scan/dependency-check-report.html"
    
    popd > /dev/null
}

echo ""
echo "========================================================"
echo "      Audit Completed"
echo "========================================================"
