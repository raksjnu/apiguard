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
    find . -type d -name "target" -prune -o -name "pom.xml" -type f -print | while read -r pom_file; do
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

exit 0

# Function to extract processing logic
process_project() {
    local pom_file="$1"
    local project_dir=$(dirname "$pom_file")
    
    echo ""
    echo "--------------------------------------------------------"
    echo "Scanning Project: $project_dir"
    echo "--------------------------------------------------------"
    
    pushd "$project_dir" > /dev/null
    
    echo "[1/3] Listing Dependencies..."
    mvn dependency:list -B -DoutputFile=target/dependency-list.txt -Dsort=true
    
    echo "[2/3] Generating License Report..."
    mvn org.codehaus.mojo:license-maven-plugin:2.0.0:add-third-party -B -Dlicense.useMissingFile -Dlicense.outputDirectory=target/site
    
    echo "[3/3] Checking for CVEs (OWASP Dependency Check)..."
    # Note: First run will download huge CVE database.
    if [ -n "$NVD_API_KEY" ]; then
        mvn org.owasp:dependency-check-maven:8.4.3:check -B -Dformat=HTML -DautoUpdate=true -DnvdApiKey=$NVD_API_KEY
    else
        echo "[WARNING] NVD_API_KEY not set. You may experience 403 errors."
        mvn org.owasp:dependency-check-maven:8.4.3:check -B -Dformat=HTML -DautoUpdate=true
    fi
    
    echo ""
    echo "[REPORT] Reports generated in $project_dir/target/"
    echo "  - Dependencies: target/dependency-list.txt"
    echo "  - Licenses:     target/site/generated-sources/license/THIRD-PARTY.txt"
    echo "  - CVEs:         target/dependency-check-report.html"
    
    popd > /dev/null
}

echo ""
echo "========================================================"
echo "      Audit Completed"
echo "========================================================"
