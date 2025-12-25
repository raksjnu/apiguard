$root = 'C:\Users\raksj\Downloads\AnypointStudio'
$dest = 'c:\raks\apiguard\raksanalyzer\src\main\resources\images\mule'
$tempDir = Join-Path $env:TEMP "mule_icon_extraction"

if (-not (Test-Path $dest)) { New-Item -ItemType Directory -Path $dest | Out-Null }
if (Test-Path $tempDir) { Remove-Item -Path $tempDir -Recurse -Force }
New-Item -ItemType Directory -Path $tempDir | Out-Null

$jars = Get-ChildItem -Path $root -Recurse -Filter "*connector*.jar" | Where-Object { $_.Name -match "email|sockets|jms|vm|http" }

foreach ($jar in $jars) {
    # Unique temp subfolder for this jar
    $subTemp = Join-Path $tempDir ($jar.BaseName + "_" + (Get-Random))
    New-Item -ItemType Directory -Path $subTemp | Out-Null
    
    # Write-Host "Extracting $($jar.Name)..."
    try {
        Expand-Archive -Path $jar.FullName -DestinationPath $subTemp -Force -ErrorAction SilentlyContinue
        
        # Look for icons in the extracted content
        Get-ChildItem -Path $subTemp -Recurse -Filter "*.png" | Where-Object { 
            ($_.FullName -match "icon" -or $_.FullName -match "palette") -and 
            ($_.Name -notmatch "large|small|@2x") # Prefer standard size? Or just take all
        } | ForEach-Object {
            
            # Identify short plugin name from JAR name
            # mule-email-connector-1.7.2 -> email
            $pluginName = "unknown"
            if ($jar.Name -match "mule-([a-zA-Z0-9]+)-connector") {
                $pluginName = $matches[1]
            } elseif ($jar.Name -match "mule-([a-zA-Z0-9]+)-plugin") {
                $pluginName = $matches[1]
            }
            
            # Construct name: mule_email-icon.png
            # Or if file is "send.png", mule_email-send.png
            
            $newName = "mule_" + $pluginName + "-" + $_.Name
            
            $destPath = Join-Path $dest $newName
            Copy-Item -Path $_.FullName -Destination $destPath -Force
            # Write-Host "  -> $newName"
        }
    } catch {
        # Write-Warning "Failed to extract $($jar.Name)"
    }
}

# Cleanup
Remove-Item -Path $tempDir -Recurse -Force
Write-Host "Extraction complete."
