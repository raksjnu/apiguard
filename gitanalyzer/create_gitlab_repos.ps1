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

    # 1. Create Directory Structure
    New-Item -ItemType Directory -Force -Path "$localPath\repo_config\Properties\OCP" | Out-Null
    New-Item -ItemType Directory -Force -Path "$localPath\repo_config\Policies" | Out-Null
    New-Item -ItemType Directory -Force -Path "$localPath\repo_config\Deployment" | Out-Null

    # 2. Create Dummy Files
    "app.name=$repoBase`napp.env=dev`nhttp.port=8081" | Set-Content "$localPath\repo_config\Properties\OCP\dev.properties"
    "app.name=$repoBase`napp.env=prod`nhttp.port=8081" | Set-Content "$localPath\repo_config\Properties\OCP\prod.properties"
    
    "<policy><id>client-id-enforcement</id><enabled>true</enabled></policy>" | Set-Content "$localPath\repo_config\Policies\secure.policy"
    
    "replicas: 2`nmemory: 1024m" | Set-Content "$localPath\repo_config\Deployment\cloudhub.deploy"

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
            Write-Host "Project $repoName already exists, proceeding to push..."
        } else {
            Write-Error "Failed to create project: $($_.Exception.Message)"
        }
    }

    # 4. Git Operations
    Set-Location $localPath
    git init | Out-Null
    git config user.email "bot@raks.com"
    git config user.name "RaksBot"
    git add . | Out-Null
    git commit -m "Initial configuration setup" | Out-Null
    
    # Add Remote with Token Auth
    $remoteUrl = "https://oauth2:${GITLAB_TOKEN}@gitlab.com/${GROUP_PATH}/${repoName}.git"
    git remote add origin $remoteUrl
    
    # Push (Force to overwrite if exists)
    try {
        git push -u origin main --force 2>&1 | Write-Host
        Write-Host "Pushed $repoName successfully."
    } catch {
        # Try master if main fails (older gitlab defaults)
        git push -u origin master --force 2>&1 | Write-Host
    }
}

Write-Host "All repositories processed."
