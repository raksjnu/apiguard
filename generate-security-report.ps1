param (
    [string]$RootPath = "."
)

$OutputFile = "$RootPath\Security_Audit_Consolidated_Report.html"
$Date = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

# Header
$HtmlContent = @"
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
    <span class="timestamp">Generated: $Date</span>
    <h1>Security Audit & License Compliance Report</h1>
    
    <div class="summary">
        <h2>Executive Summary</h2>
        <p>This report consolidates security findings, dependency trees, and license audits for Maven projects found in:</p>
        <code>$RootPath</code>
    </div>
"@

# Find POM files (excluding target)
$PomFiles = Get-ChildItem -Path $RootPath -Filter "pom.xml" -Recurse | Where-Object { $_.FullName -notmatch "\\target\\" }

foreach ($Pom in $PomFiles) {
    $ProjectDir = $Pom.Directory.FullName
    $ProjectName = $Pom.Directory.Name
    
    $HtmlContent += @"
    <div class="audit-item">
        <h2>Project: $ProjectName</h2>
        <div class="path">Path: <code>$ProjectDir</code></div>
"@

    # 1. Dependency List
    $DepFile = Join-Path $ProjectDir "target/dependency-list.txt"
    if (Test-Path $DepFile) {
        $Deps = Get-Content $DepFile -Raw
        $HtmlContent += "<h3>Dependencies</h3><pre>$Deps</pre>"
    } else {
        $HtmlContent += "<h3>Dependencies</h3><p class='status-warn'>No dependency list found.</p>"
    }

    # 2. License Report
    $LicFile = Join-Path $ProjectDir "target/site/generated-sources/license/THIRD-PARTY.txt"
    if (Test-Path $LicFile) {
        $Lics = Get-Content $LicFile -Raw
        $HtmlContent += "<h3>License Audit</h3><pre>$Lics</pre>"
    } else {
        $HtmlContent += "<h3>License Audit</h3><p class='status-warn'>No license report found.</p>"
    }

    # 3. CVEs
    $CveFile = Join-Path $ProjectDir "target/dependency-check-report.html"
    if (Test-Path $CveFile) {
        $CveLink = "file:///$($CveFile.Replace('\', '/'))"
        $HtmlContent += @"
        <h3>Vulnerabilities (CVEs)</h3>
        <p>OWASP Dependency Check completed. <a href="$CveLink" target="_blank">View Detailed HTML Report</a></p>
        <iframe src="$CveLink" width="100%" height="300" style="border:1px solid #ccc;"></iframe>
"@
    } else {
        $HtmlContent += "<h3>Vulnerabilities</h3><p class='status-warn'>No OWASP report found (possibly failed).</p>"
    }

    $HtmlContent += "</div>" # End Project Div
}

$HtmlContent += @"
</div>
</body>
</html>
"@

$HtmlContent | Set-Content $OutputFile
Write-Host "Report generated at: $OutputFile"
