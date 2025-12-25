# Test script for TIBCO diagram generation
param(
    [string]$JarPath = "target\raksanalyzer-1.0.0.jar"
)

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Testing TIBCO Diagram Generation" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$javaExe = "$env:JAVA_HOME\bin\java.exe"

# Test by running the actual application and checking logs
Write-Host "Starting RaksAnalyzer server..." -ForegroundColor Yellow
$serverProcess = Start-Process -FilePath $javaExe -ArgumentList "-jar", $JarPath -PassThru -NoNewWindow -RedirectStandardOutput "test-output.log" -RedirectStandardError "test-error.log"

Start-Sleep -Seconds 5

Write-Host "Triggering analysis via API..." -ForegroundColor Yellow
try {
    $body = @{
        projectPath = "testdata"
        formats = @{
            pdf = $false
            word = $true
            excel = $false
        }
    } | ConvertTo-Json
    
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/analyze" -Method Post -ContentType "application/json" -Body $body
    
    Write-Host "Analysis completed!" -ForegroundColor Green
} catch {
    Write-Host "Error calling API: $_" -ForegroundColor Red
}

Start-Sleep -Seconds 2

# Stop server
Write-Host "Stopping server..." -ForegroundColor Yellow
Stop-Process -Id $serverProcess.Id -Force
Start-Sleep -Seconds 2

# Check logs for connector rendering
Write-Host ""
Write-Host "Checking logs for rendered connectors..." -ForegroundColor Yellow
$logContent = Get-Content "logs\raksanalyzer.log" -Tail 200

$foundConnectors = @()
$expectedConnectors = @(
    "FileReadActivity",
    "FileCopyActivity", 
    "FileRenameActivity",
    "FileRemoveActivity",
    "ListFilesActivity",
    "FileWriteActivity",
    "JDBCQueryActivity",
    "JDBCUpdateActivity"
)

foreach ($connector in $expectedConnectors) {
    if ($logContent -match "Rendering connector.*$connector") {
        $foundConnectors += $connector
        Write-Host "  [OK] Found: $connector" -ForegroundColor Green
    } else {
        Write-Host "  [MISS] Missing: $connector" -ForegroundColor Red
    }
}

$foundCallProcess = $logContent -match "Rendering CallProcess"
if ($foundCallProcess) {
    Write-Host "  [OK] Found: CallProcess (partition)" -ForegroundColor Green
} else {
    Write-Host "  [MISS] Missing: CallProcess (partition)" -ForegroundColor Red
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
if ($foundConnectors.Count -eq $expectedConnectors.Count -and $foundCallProcess) {
    Write-Host "  TEST PASSED: All connectors found!" -ForegroundColor Green
    Write-Host "============================================================" -ForegroundColor Cyan
    exit 0
} else {
    Write-Host "  TEST FAILED: Missing connectors" -ForegroundColor Red
    Write-Host "  Found: $($foundConnectors.Count)/$($expectedConnectors.Count)" -ForegroundColor Red
    Write-Host "============================================================" -ForegroundColor Cyan
    exit 1
}
