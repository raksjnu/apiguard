# PowerShell script to convert rules.yaml to rules_data.js
# Simple parsing since we don't have python yaml lib

$yamlPath = "C:\raks\apiguard\aegis\src\main\resources\rules\rules.yaml"
$jsPath = "C:\raks\apiguard\aegis\rules_data.js"

if (-not (Test-Path $yamlPath)) {
    Write-Host "Error: rules.yaml not found at $yamlPath"
    exit 1
}

$yamlContent = Get-Content $yamlPath -Raw

# We will cheat slightly and wrap the content in a way that JS can parse it if it was JSON, 
# but since it's YAML, we can't easily convert it without a library in PS either without robust logic.
# However, Aegis is a Java app. Let's write a quick Java class that uses snakeyaml (which is already in the project dependencies) to do this.

Write-Host "PowerShell YAML parsing is complex. Switching strategy to Java."
