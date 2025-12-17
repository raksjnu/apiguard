#!/bin/bash
# Security Vulnerability Scan Script
# Run this script to generate security reports for enterprise review

echo "======================================"
echo "API Comparison Tool - Security Scan"
echo "======================================"
echo ""

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven is not installed or not in PATH"
    exit 1
fi

echo "1. Running OWASP Dependency Check..."
echo "   This will scan all dependencies for known vulnerabilities (CVEs)"
echo ""
mvn org.owasp:dependency-check-maven:8.4.0:check -DfailBuildOnCVSS=7

if [ $? -eq 0 ]; then
    echo "✅ OWASP Dependency Check completed"
    echo "   Report: target/dependency-check-report.html"
else
    echo "⚠️  OWASP Dependency Check found vulnerabilities or failed"
fi
echo ""

echo "2. Generating Software Bill of Materials (SBOM)..."
echo "   This creates a CycloneDX SBOM for supply chain security"
echo ""
mvn org.cyclonedx:cyclonedx-maven-plugin:2.7.9:makeAggregateBom

if [ $? -eq 0 ]; then
    echo "✅ SBOM generated"
    echo "   Report: target/bom.xml"
else
    echo "⚠️  SBOM generation failed"
fi
echo ""

echo "3. Generating License Report..."
echo "   This lists all third-party licenses for compliance review"
echo ""
mvn license:add-third-party

if [ $? -eq 0 ]; then
    echo "✅ License report generated"
    echo "   Report: target/generated-sources/license/THIRD-PARTY.txt"
else
    echo "⚠️  License report generation failed"
fi
echo ""

echo "4. Generating Dependency Tree..."
echo "   This shows the complete dependency hierarchy"
echo ""
mvn dependency:tree -DoutputFile=target/dependency-tree.txt

if [ $? -eq 0 ]; then
    echo "✅ Dependency tree generated"
    echo "   Report: target/dependency-tree.txt"
else
    echo "⚠️  Dependency tree generation failed"
fi
echo ""

echo "======================================"
echo "Security Scan Complete"
echo "======================================"
echo ""
echo "Generated Reports:"
echo "  1. target/dependency-check-report.html - Vulnerability scan"
echo "  2. target/bom.xml - Software Bill of Materials (SBOM)"
echo "  3. target/generated-sources/license/THIRD-PARTY.txt - License report"
echo "  4. target/dependency-tree.txt - Dependency tree"
echo ""
echo "Please review these reports and share with your security team."
echo ""
