$ErrorActionPreference = "Continue"

# Configuration
$GITLAB_TOKEN = "glpat-8xWKilsBixw-i6hMcR1FvW86MQp1OmplYXcwCw.01.120yhiotd" # Extracted from properties
$GROUP_PATH = "raks-group" # From URL
$API_URL = "https://gitlab.com/api/v4"
$WORK_DIR = "C:\raks\apiguard\temp_config_repos"

# Repos to create
$REPOS = @("repo1-mule-api", "repo5-mule-batch", "repo7-mule-soap", "repo9-mule-hybrid")

# Ensure work dir exists
if (Test-Path $WORK_DIR) { Remove-Item -Recurse -Force $WORK_DIR }
New-Item -ItemType Directory -Force -Path $WORK_DIR | Out-Null

# Function to get Group ID
function Get-GroupId {
    param ($Path)
    $uri = "$API_URL/groups?search=$Path"
    $response = Invoke-RestMethod -Uri $uri -Headers @{ "PRIVATE-TOKEN" = $GITLAB_TOKEN }
    $group = $response | Where-Object { $_.path -eq $Path }
    return $group.id
}

$GROUP_ID = Get-GroupId -Path $GROUP_PATH
if (-not $GROUP_ID) {
    Write-Error "Could not find Group ID for $GROUP_PATH"
    exit 1
}
Write-Host "Found Group ID: $GROUP_ID"

# Loop through repos
foreach ($repoBase in $REPOS) {
    $repoName = "${repoBase}_config"
    $localPath = Join-Path $WORK_DIR $repoName
    
    Write-Host "Processing $repoName..."

    # 1. Create Directory Structure (FLAT, no repo_config parent)
    New-Item -ItemType Directory -Force -Path "$localPath\Properties\OCP" | Out-Null
    New-Item -ItemType Directory -Force -Path "$localPath\Policies" | Out-Null
    New-Item -ItemType Directory -Force -Path "$localPath\Deployment" | Out-Null

    # 2. Create Dummy Files
    "app.name=$repoBase`napp.env=dev`nhttp.port=8081" | Set-Content "$localPath\Properties\OCP\dev.properties"
    "app.name=$repoBase`napp.env=prod`nhttp.port=8081" | Set-Content "$localPath\Properties\OCP\prod.properties"
    
    "<policy><id>client-id-enforcement</id><enabled>true</enabled></policy>" | Set-Content "$localPath\Policies\secure.policy"
    
    "replicas: 2`nmemory: 1024m" | Set-Content "$localPath\Deployment\cloudhub.deploy"

    # 3. Create Project in GitLab (Ignore if exists, handle 400)
    try {
        $body = @{
            name = $repoName
            namespace_id = $GROUP_ID
            visibility = "private"
        } | ConvertTo-Json
        
        $createUri = "$API_URL/projects"
        Invoke-RestMethod -Uri $createUri -Method Post -Body $body -ContentType "application/json" -Headers @{ "PRIVATE-TOKEN" = $GITLAB_TOKEN } | Out-Null
        Write-Host "Created GitLab project $repoName"
    } catch {
         # Check if error is 'has already been taken'
        $errJson = $_.ErrorDetails.Message | ConvertFrom-Json
        if ($errJson.message.name -contains "has already been taken") {
            Write-Host "Project $repoName already exists, will overwrite content..."
        } else {
            Write-Host "Warning during project creation: $($_.Exception.Message)"
        }
    }

    # 4. Git Operations
    Set-Location $localPath
    if (Test-Path ".git") { Remove-Item -Recurse -Force ".git" } # Ensure clean slate
    git init | Out-Null
    git config user.email "bot@raks.com"
    git config user.name "RaksBot"
    git add . 
    git commit -m "Fix structure: Initial configuration setup"
    
    # Add Remote with Token Auth
    $remoteUrl = "https://oauth2:${GITLAB_TOKEN}@gitlab.com/${GROUP_PATH}/${repoName}.git"
    git remote add origin $remoteUrl
    
    # Push (Force to overwrite if exists)
    Write-Host "Pushing to $repoName..."
    git push -u origin main --force 2>&1 | Write-Host
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Main push failed, trying master..."
        git push -u origin master --force 2>&1 | Write-Host
    }
}

Write-Host "All repositories processed."
