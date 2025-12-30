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

# Recursively find all pom.xml files
echo "[INFO] Searching for Maven projects..."
find . -name "pom.xml" -type f | while read -r pom_file; do
    project_dir=$(dirname "$pom_file")
    
    echo ""
    echo "--------------------------------------------------------"
    echo "Scanning Project: $project_dir"
    echo "--------------------------------------------------------"
    
    pushd "$project_dir" > /dev/null
    
    echo "[1/3] Listing Dependencies..."
    mvn dependency:list -DoutputFile=target/dependency-list.txt -Dsort=true
    
    echo "[2/3] Generating License Report..."
    mvn org.codehaus.mojo:license-maven-plugin:2.0.0:aggregate-add-third-party -Dlicense.useMissingFile -Dlicense.outputDirectory=target/site
    
    echo "[3/3] Checking for CVEs (OWASP Dependency Check)..."
    # Note: First run will download huge CVE database.
    mvn org.owasp:dependency-check-maven:8.4.3:check -Dformat=HTML -DautoUpdate=true
    
    echo ""
    echo "[REPORT] Reports generated in $project_dir/target/"
    echo "  - Dependencies: target/dependency-list.txt"
    echo "  - Licenses:     target/site/generated-sources/license/THIRD-PARTY.txt"
    echo "  - CVEs:         target/dependency-check-report.html"
    
    popd > /dev/null
done

echo ""
echo "========================================================"
echo "      Audit Completed"
echo "========================================================"
