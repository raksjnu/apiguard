param (
    [string]$RootPath = "."
)

$Date = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

# Heuristics for License Safety
function Get-LicenseStatus {
    param ($LicenseText)
    if ($LicenseText -match "Apache|MIT|BSD|ISC|EPL|Safe") { return "pass" }
    if ($LicenseText -match "GPL|AGPL") { return "fail" }
    return "warn"
}

# Template for Header
$HeaderTemplate = @"
<!DOCTYPE html>
<html>
<head>
    <title>Security Audit Report</title>
    <style>
        body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f4f6f9; color: #333; margin: 0; padding: 20px; }
        .container { max-width: 1400px; margin: 0 auto; background: white; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); border-radius: 8px; }
        h1 { color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 10px; }
        h2 { color: #34495e; margin-top: 30px; border-left: 5px solid #3498db; padding-left: 10px; }
        h3 { color: #7f8c8d; margin-top: 20px; margin-bottom: 10px; }
        .timestamp { color: #7f8c8d; font-size: 0.9em; float: right; }
        .audit-item { background: #fff; border: 1px solid #e9ecef; padding: 20px; margin-bottom: 30px; border-radius: 5px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
        .path { font-family: monospace; color: #666; margin-bottom: 15px; font-size: 0.9em; }
        
        /* Tables */
        table { border-collapse: collapse; width: 100%; font-size: 0.9em; margin-bottom: 15px; }
        th { background-color: #f8f9fa; text-align: left; padding: 10px; border-bottom: 2px solid #ddd; color: #444; }
        td { padding: 8px 10px; border-bottom: 1px solid #eee; }
        tr:hover { background-color: #f1f1f1; }
        
        /* Status Tags */
        .tag { padding: 3px 8px; border-radius: 12px; font-size: 0.8em; font-weight: bold; display: inline-block; }
        .tag-pass { background-color: #e6ffed; color: #2ea44f; }
        .tag-fail { background-color: #ffeef0; color: #cb2431; }
        .tag-warn { background-color: #fff8c5; color: #b08800; }
        
        .section-header { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
        iframe { border: 1px solid #eee; border-radius: 4px; }
    </style>
</head>
<body>
<div class="container">
    <span class="timestamp">Generated: $Date</span>
    <h1>Security Audit & License Compliance Report</h1>
"@

# Find POM files (excluding target, test-data, reference)
$PomFiles = Get-ChildItem -Path $RootPath -Filter "pom.xml" -Recurse | Where-Object { 
    $_.FullName -notmatch "\\target\\" -and 
    $_.FullName -notmatch "\\test-data\\" -and 
    $_.FullName -notmatch "\\testdata\\" -and 
    $_.FullName -notmatch "\\reference\\" 
}

if ($PomFiles.Count -eq 0) {
    Write-Host "No Maven projects found in the specified path."
}

foreach ($Pom in $PomFiles) {
    $ProjectDir = $Pom.Directory.FullName
    $ProjectName = $Pom.Directory.Name
    
    # Ensure security_scan directory exists before writing
    $ScanDir = Join-Path $ProjectDir "security_scan"
    if (-not (Test-Path $ScanDir)) {
        Write-Host "Skipping $ProjectName (No security_scan directory found)"
        continue
    }
    
    $OutputFile = Join-Path $ScanDir "Security_Audit_Consolidated_Report.html"
    
    # Initialize Content with Header
    $HtmlContent = $HeaderTemplate
    $HtmlContent += @"
    <div class="summary">
        <p><strong>Project:</strong> $ProjectName</p>
        <p><strong>Path:</strong> <code>$ProjectDir</code></p>
    </div>
    <div class="audit-item">
"@

    # 1. Dependency Tree (Replaces List)
    $TreeFile = Join-Path $ScanDir "dependency-tree.txt"
    if (Test-Path $TreeFile) {
        $HtmlContent += "<h3>Dependency Tree</h3>"
        $TreeContent = Get-Content $TreeFile | Out-String
        $HtmlContent += "<div style='max-height: 300px; overflow-y: auto; background: #fafafa; border: 1px solid #ddd; padding: 10px; font-family: monospace; white-space: pre;'>$TreeContent</div>"
    } else {
        $HtmlContent += "<p class='tag tag-warn'>No dependency tree available.</p>"
    }

    # 2. SBOM
    $SbomFile = Join-Path $ScanDir "bom.xml"
    if (Test-Path $SbomFile) {
        $HtmlContent += "<h3>SBOM</h3>"
        $HtmlContent += "<p>Generated CycloneDX SBOM: <a href='bom.xml' target='_blank'>bom.xml</a></p>"
    }

    # 3. License Analysis
    $LicFile = Join-Path $ScanDir "THIRD-PARTY.txt"
    if (Test-Path $LicFile) {
        $HtmlContent += "<h3>License Compliance</h3>"
        $HtmlContent += "<table><thead><tr><th>License</th><th>Library</th><th>Enterprise Status</th></tr></thead><tbody>"
        
        $Lines = Get-Content $LicFile
        foreach ($Line in $Lines) {
            # Format: (License) Name (Group:Artifact:Version - URL)
            if ($Line -match "\((.*?)\) (.*?) \((.*?)\)") {
                $LicName = $matches[1]
                $LibName = $matches[2]
                $Coords  = $matches[3]
                
                $Status = Get-LicenseStatus $LicName
                $StatusClass = "tag-$Status"
                $StatusText = if ($Status -eq "pass") { "Permissive (Safe)" } elseif ($Status -eq "fail") { "Copyleft (Restricted)" } else { "Unknown" }
                $Explanation = ""
                if ($Status -eq "pass") { $Explanation = "Allows commercial use, modification, and distribution." }
                if ($Status -eq "fail") { $Explanation = "Requires derivative works to be open source (viral)." }
                
                $HtmlContent += "<tr><td><strong>$LicName</strong></td><td>$LibName<br><small>$Coords</small></td><td><span class='tag $StatusClass' title='$Explanation'>$StatusText</span></td></tr>"
            }
        }
        $HtmlContent += "</tbody></table>"
    }

    # 4. CVEs
    $CveFile = Join-Path $ScanDir "dependency-check-report.html"
    if (Test-Path $CveFile) {
        # Create a link relative to the HTML file (which is in the same folder)
        $CveLink = "dependency-check-report.html"
        $HtmlContent += @"
        <h3>Vulnerabilities (OWASP)</h3>
        <p><a href="$CveLink" target="_blank">Open Full OWASP Report</a></p>
        <iframe src="$CveLink" width="100%" height="600"></iframe>
"@
    } else {
        $HtmlContent += @"
        <h3>Vulnerabilities (OWASP)</h3>
        <p class='tag tag-fail'>Scan Failed: Report not found.</p>
        <div style="background: #fff3cd; padding: 10px; border: 1px solid #ffeeba; border-radius: 4px; margin-top: 5px; font-size: 0.9em;">
            <strong>Possible Cause:</strong> NVD API connection failed (403 Forbidden). / Scan configuration error.
        </div>
"@
    }

    $HtmlContent += @"
    </div>
</div>
</body>
</html>
"@
    
    $HtmlContent | Set-Content $OutputFile
    Write-Host "Report generated for $ProjectName at: $OutputFile"
}
