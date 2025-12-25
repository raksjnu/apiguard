$sources = @(
    'C:\Users\raksj\Downloads\AnypointStudio\configuration\org.eclipse.osgi',
    'C:\Users\raksj\Downloads\AnypointStudio\plugins'
)
$dest = 'c:\raks\apiguard\raksanalyzer\src\main\resources\images\mule'

# Ensure destination exists
if (-not (Test-Path -Path $dest)) {
    New-Item -ItemType Directory -Path $dest | Out-Null
}

function Get-PluginNameFromPath ($path) {
    # Extract plugin name from path string
    # Matches patterns like:
    # ...\org.mule.tooling.ui.modules.sockets_1.2.3\...
    # ...\org.mule.tooling.ui.modules.core_...
    # ...\org.mule.tooling.apikit.common_...
    
    if ($path -match "org\.mule\.tooling\.ui\.modules\.([a-zA-Z0-9]+)_") {
        return $matches[1]
    }
    if ($path -match "org\.mule\.tooling\.([a-zA-Z0-9\.]+)_") {
         return $matches[1]
    }
    return "unknown"
}

foreach ($srcRoot in $sources) {
    if (Test-Path $srcRoot) {
        Write-Host "Scanning $srcRoot..."
        # Broad filter for potential icon locations
        Get-ChildItem -Path $srcRoot -Recurse -Filter *.png -ErrorAction SilentlyContinue | Where-Object { 
            ($_.FullName -match "\\(icons|images|palette)\\") -and 
            ($_.Name -notmatch "wizard|banner|splash|background") 
        } | ForEach-Object {
            
            $pluginName = Get-PluginNameFromPath $_.FullName
            $cleanName = $_.Name.Replace("@2x", "")
            
            # If name is "send.png" and plugin is "sockets", result: "mule_sockets-send.png"
            # If name is "http-listener.png" and plugin is "http", result: "mule_http-http-listener.png" -> simplified to "mule_http-listener.png"? 
            # Let's keep it verbose to be safe: prefix-name.
            
            # Avoid overwriting with "unknown" if we already have a better one?
            # We'll rely on the fact that 'plugins' folder usually has the explicit names.
            
            $newName = "mule_" + $pluginName + "-" + $cleanName
            
            $destPath = Join-Path $dest $newName
            
            # Only copy if it doesn't exist OR if we are overwriting an "unknown" one with a specific one?
            # For simplicity, just copy. User wants EVERYTHING.
            
            try {
                Copy-Item -Path $_.FullName -Destination $destPath -Force
                # Write-Host "Copied: $newName"
            } catch {
                # Ignore errors
            }
        }
    }
}
Write-Host "Import valid."
