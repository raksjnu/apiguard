<#
.SYNOPSIS
    Pushes the simulated enterprise test data to GitLab.
.DESCRIPTION
    This script iterates through the 'test-data/simulated-enterprise' directory.
    For each top-level folder, it creates a GitLab Group.
    For each sub-folder, it creates a GitLab Project within that group and pushes the content.
.PARAMETER Token
    Your GitLab Personal Access Token (requires 'api' scope).
.PARAMETER BaseUrl
    GitLab Base URL (default: https://gitlab.com).
#>

param (
    [Parameter(Mandatory=$true)]
    [string]$Token,
    [string]$ParentGroupName = "",
    [string]$BaseUrl = "https://gitlab.com"
)

$ErrorActionPreference = "Continue"
$TestDataDir = Join-Path $PSScriptRoot "test-data\simulated-enterprise"

if (-not (Test-Path $TestDataDir)) {
    Write-Error "Test data directory not found: $TestDataDir"
    exit 1
}

$Headers = @{
    "PRIVATE-TOKEN" = $Token
    "Content-Type"  = "application/json"
}

# 0. Get User Info & Parent Group (if applicable)
try {
    $UserInfo = Invoke-RestMethod -Uri "$BaseUrl/api/v4/user" -Headers $Headers -Method Get
    $Username = $UserInfo.username
    $UserId = $UserInfo.id
    Write-Host "Authenticated as: $Username (ID: $UserId)" -ForegroundColor Cyan
} catch {
    Write-Error "Failed to authenticate with token: $_"
    exit 1
}

$ParentGroupId = $null
if ($ParentGroupName) {
    try {
        $Groups = Invoke-RestMethod -Uri "$BaseUrl/api/v4/groups?search=$ParentGroupName" -Headers $Headers -Method Get
        $ParentGroup = $Groups | Where-Object { $_.name -eq $ParentGroupName }
        if ($ParentGroup) {
            $ParentGroupId = $ParentGroup.id
            Write-Host "Found Parent Group: $ParentGroupName (ID: $ParentGroupId)" -ForegroundColor Cyan
            Write-Host "Creating Subgroups under '$ParentGroupName'..." -ForegroundColor Cyan
        } else {
            Write-Error "Parent Group '$ParentGroupName' not found!"
            exit 1
        }
    } catch {
         Write-Error "Failed to search for parent group: $_"
         exit 1
    }
} else {
    Write-Host "Using Personal Namespace for projects (flat structure)." -ForegroundColor Cyan
}

$Groups = Get-ChildItem -Path $TestDataDir -Directory

foreach ($GroupDir in $Groups) {
    $GroupName = $GroupDir.Name
    $TargetNamespaceId = $UserId # Default to User
    
    # If Parent Group exists, create a Subgroup
    if ($ParentGroupId) {
        Write-Host "Processing Subgroup: $GroupName" -ForegroundColor Cyan
        
        # Check/Create Subgroup
        $SubGroupId = $null
        try {
            # Search subgroups
            $SubGroups = Invoke-RestMethod -Uri "$BaseUrl/api/v4/groups/$ParentGroupId/subgroups?search=$GroupName" -Headers $Headers -Method Get
            $ExistingSub = $SubGroups | Where-Object { $_.name -eq $GroupName }
            if ($ExistingSub) {
                $SubGroupId = $ExistingSub.id
                Write-Host "  - Subgroup '$GroupName' already exists (ID: $SubGroupId)."
            }
        } catch {}

        if (-not $SubGroupId) {
             Write-Host "  - Creating Subgroup '$GroupName' under parent..."
             $SubBody = @{
                name = $GroupName
                path = $GroupName
                parent_id = $ParentGroupId
                visibility = "private"
             } | ConvertTo-Json
             
             try {
                $NewSub = Invoke-RestMethod -Uri "$BaseUrl/api/v4/groups" -Headers $Headers -Method Post -Body $SubBody
                $SubGroupId = $NewSub.id
                Write-Host "  - Created Subgroup (ID: $SubGroupId)."
             } catch {
                Write-Error "  - Failed to create subgroup: $_"
                continue
             }
        }
        $TargetNamespaceId = $SubGroupId
    }

    # Process Projects
    $Projects = Get-ChildItem -Path $GroupDir.FullName -Directory

    foreach ($ProjDir in $Projects) {
        $ProjName = $ProjDir.Name # Use original name for nested structure
        if (-not $ParentGroupId) {
             # Flatten name only if no parent group
             $ProjName = "$GroupName-$ProjDir.Name"
        }

        Write-Host "  > Processing Project: $ProjName" -ForegroundColor Yellow

        # Check/Create Project
        $ProjUrl = $null
        
        # Search strategy depends on namespace type
        try {
             if ($ParentGroupId) {
                 # Search in Subgroup
                 $ExistingProjs = Invoke-RestMethod -Uri "$BaseUrl/api/v4/groups/$TargetNamespaceId/projects?search=$ProjName" -Headers $Headers -Method Get
             } else {
                 # Search in User Namespace
                 $ExistingProjs = Invoke-RestMethod -Uri "$BaseUrl/api/v4/users/$UserId/projects?search=$ProjName" -Headers $Headers -Method Get
             }
             
             $TargetProj = $ExistingProjs | Where-Object { $_.name -eq $ProjName }
             if ($TargetProj) {
                $ProjUrl = $TargetProj.http_url_to_repo
                Write-Host "    - Project already exists: $ProjUrl"
             }
        } catch {}

        if (-not $ProjUrl) {
            Write-Host "    - Creating Project '$ProjName'..."
            $ProjBody = @{
                name = $ProjName
                namespace_id = $TargetNamespaceId
                visibility = "private"
            } | ConvertTo-Json

            try {
                $NewProj = Invoke-RestMethod -Uri "$BaseUrl/api/v4/projects" -Headers $Headers -Method Post -Body $ProjBody
                $ProjUrl = $NewProj.http_url_to_repo
                Write-Host "    - Created Project."
            } catch {
                Write-Error "    - Failed to create project: $_"
                continue
            }
        }

        # Push to Git
        if ($ProjUrl) {
            Write-Host "    - Pushing code within $ProjDir.FullName..." 
            Push-Location $ProjDir.FullName
            
            if (-not (Test-Path ".git")) {
                git init | Out-Null
                git branch -m main | Out-Null
            }

            $CleanUrl = $ProjUrl -replace "https://", ""
            $AuthUrl = "https://oauth2:$Token@$CleanUrl"

            if (git remote | Select-String "origin") {
                git remote set-url origin $AuthUrl
            } else {
                git remote add origin $AuthUrl
            }

            git add . | Out-Null
            git commit -m "Initial commit" 2>$null | Out-Null
            git push -u origin main 2>&1 | Out-String | Write-Host
            
            Pop-Location
        }
    }
}

Write-Host "`nDone! Seeding Complete." -ForegroundColor Green
