#!/bin/bash

ROOT_PATH="${1:-.}"
OUTPUT_FILE="$ROOT_PATH/Security_Audit_Consolidated_Report.html"
DATE=$(date "+%Y-%m-%d %H:%M:%S")

# Header
cat <<EOF > "$OUTPUT_FILE"
<!DOCTYPE html>
<html>
<head>
    <title>Security Audit Report</title>
    <style>
        body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f4f6f9; color: #333; margin: 0; padding: 20px; }
        .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); border-radius: 8px; }
        h1 { color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 10px; }
        h2 { color: #34495e; margin-top: 30px; border-left: 5px solid #3498db; padding-left: 10px; }
        h3 { color: #7f8c8d; }
        .timestamp { color: #7f8c8d; font-size: 0.9em; float: right; }
        .audit-item { background: #f8f9fa; border: 1px solid #e9ecef; padding: 15px; margin-bottom: 20px; border-radius: 5px; }
        .audit-item h4 { margin-top: 0; color: #2980b9; }
        pre { background: #2d3436; color: #dfe6e9; padding: 15px; overflow-x: auto; border-radius: 4px; font-family: Consolas, monospace; font-size: 0.9em; }
        .status-pass { color: green; font-weight: bold; }
        .status-fail { color: red; font-weight: bold; }
        .status-warn { color: orange; font-weight: bold; }
        table { border-collapse: collapse; width: 100%; margin-top: 10px; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background-color: #f2f2f2; }
    </style>
</head>
<body>
<div class="container">
    <span class="timestamp">Generated: $DATE</span>
    <h1>Security Audit & License Compliance Report</h1>
    
    <div class="summary">
        <h2>Executive Summary</h2>
        <p>This report consolidates security findings, dependency trees, and license audits for Maven projects found in:</p>
        <code>$ROOT_PATH</code>
    </div>
EOF

# Find POM files (excluding target)
find "$ROOT_PATH" -type d -name "target" -prune -o -name "pom.xml" -type f -print | while read -r pom_file; do
    project_dir=$(dirname "$pom_file")
    project_name=$(basename "$project_dir")
    
    cat <<EOF >> "$OUTPUT_FILE"
    <div class="audit-item">
        <h2>Project: $project_name</h2>
        <div class="path">Path: <code>$project_dir</code></div>
EOF

    # 1. Dependency List
    dep_file="$project_dir/target/dependency-list.txt"
    if [ -f "$dep_file" ]; then
        deps=$(cat "$dep_file")
        echo "<h3>Dependencies</h3><pre>$deps</pre>" >> "$OUTPUT_FILE"
    else
        echo "<h3>Dependencies</h3><p class='status-warn'>No dependency list found.</p>" >> "$OUTPUT_FILE"
    fi

    # 2. License Report
    lic_file="$project_dir/target/site/generated-sources/license/THIRD-PARTY.txt"
    if [ -f "$lic_file" ]; then
        lics=$(cat "$lic_file")
        echo "<h3>License Audit</h3><pre>$lics</pre>" >> "$OUTPUT_FILE"
    else
        echo "<h3>License Audit</h3><p class='status-warn'>No license report found.</p>" >> "$OUTPUT_FILE"
    fi

    # 3. CVEs
    cve_file="$project_dir/target/dependency-check-report.html"
    if [ -f "$cve_file" ]; then
        # Convert path to standard file URI if needed, but relative link usually works best for portable HTML
        # Using a relative path from the report file location
        rel_cve_path=$(realpath --relative-to="$ROOT_PATH" "$cve_file")
        cat <<EOF >> "$OUTPUT_FILE"
        <h3>Vulnerabilities (CVEs)</h3>
        <p>OWASP Dependency Check completed. <a href="$rel_cve_path" target="_blank">View Detailed HTML Report</a></p>
        <iframe src="$rel_cve_path" width="100%" height="300" style="border:1px solid #ccc;"></iframe>
EOF
    else
        echo "<h3>Vulnerabilities</h3><p class='status-warn'>No OWASP report found (possibly failed).</p>" >> "$OUTPUT_FILE"
    fi

    echo "</div>" >> "$OUTPUT_FILE"
done

cat <<EOF >> "$OUTPUT_FILE"
</div>
</body>
</html>
EOF

echo "Report generated at: $OUTPUT_FILE"
