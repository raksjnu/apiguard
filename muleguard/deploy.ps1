Write-Host "=== MuleGuard Quick Deploy ==="
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Write-Host "Step 1: Building muleguard JAR..."
Set-Location $PSScriptRoot
mvn clean package -DskipTests -q
if ($LASTEXITCODE -ne 0) { exit 1 }

Write-Host "Step 2: Copying JAR..."
Copy-Item "target\muleguard-1.0.0-jar-with-raks.jar" "muleguardwrapper\lib\muleguard-1.0.0-jar-with-raks.jar" -Force

Write-Host "Step 3: Rebuilding wrapper..."
Set-Location "muleguardwrapper"
mvn clean package -DskipTests -q
if ($LASTEXITCODE -ne 0) { exit 1 }

Write-Host "DEPLOYMENT COMPLETE"
