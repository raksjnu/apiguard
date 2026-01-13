<#
.SYNOPSIS
    Moves legacy repositories (flat projects) into a 'raks-group-legacy' subgroup.
.PARAMETER Token
    Your GitLab Personal Access Token.
.PARAMETER ParentGroupName
    The parent group name (e.g., raks-group).
#>

param (
    [Parameter(Mandatory=$true)]
    [string]$Token,
    [string]$ParentGroupName = "raks-group",
    [string]$BaseUrl = "https://gitlab.com"
)

$ErrorActionPreference = "Stop" # Stop on error to handle carefully

$Headers = @{
    "PRIVATE-TOKEN" = $Token
    "Content-Type"  = "application/json"
}

# 1. Get Parent Group ID
Write-Host "Locating Parent Group: $ParentGroupName..." -ForegroundColor Cyan
try {
    $Groups = Invoke-RestMethod -Uri "$BaseUrl/api/v4/groups?search=$ParentGroupName" -Headers $Headers -Method Get
    $ParentGroup = $Groups | Where-Object { $_.name -eq $ParentGroupName }
    if (-not $ParentGroup) {
        Write-Error "Parent Group '$ParentGroupName' not found."
        exit 1
    }
    $ParentId = $ParentGroup.id
    Write-Host "Found Parent ID: $ParentId" -ForegroundColor Green
} catch {
    Write-Error "API Error: $_"
    exit 1
}

# 2. Check/Create Target Subgroup 'raks-group-legacy'
$LegacyGroupName = "raks-group-legacy"
$LegacyGroupId = $null

Write-Host "Checking for Legacy Subgroup: $LegacyGroupName..." -ForegroundColor Cyan
try {
    $SubGroups = Invoke-RestMethod -Uri "$BaseUrl/api/v4/groups/$ParentId/subgroups?search=$LegacyGroupName" -Headers $Headers -Method Get
    $LegacyGroup = $SubGroups | Where-Object { $_.name -eq $LegacyGroupName }
    
    if ($LegacyGroup) {
        $LegacyGroupId = $LegacyGroup.id
        Write-Host "Legacy Subgroup already exists (ID: $LegacyGroupId)." -ForegroundColor Green
    } else {
        Write-Host "Creating Legacy Subgroup..."
        $Body = @{
            name = $LegacyGroupName
            path = $LegacyGroupName
            parent_id = $ParentId
            visibility = "private"
        } | ConvertTo-Json
        
        $NewGroup = Invoke-RestMethod -Uri "$BaseUrl/api/v4/groups" -Headers $Headers -Method Post -Body $Body
        $LegacyGroupId = $NewGroup.id
        Write-Host "Created Legacy Subgroup (ID: $LegacyGroupId)." -ForegroundColor Green
    }
} catch {
    Write-Error "Failed to manage Legacy Subgroup: $_"
    exit 1
}

# 3. Find Projects to Move
# Criteria: Direct children of ParentGroup, Name matches legacy pattern
Write-Host "Scanning for projects to move..." -ForegroundColor Cyan
$ProjectsToMove = @()

try {
    $AllProjects = Invoke-RestMethod -Uri "$BaseUrl/api/v4/groups/$ParentId/projects?per_page=100&include_subgroups=false" -Headers $Headers -Method Get
    
    foreach ($Proj in $AllProjects) {
        # Check matching pattern: repo* or RAKS-project
        if ($Proj.name -match "^repo\d+-" -or $Proj.name -eq "RAKS-project") {
            $ProjectsToMove += $Proj
        }
    }
} catch {
     Write-Error "Failed to list projects: $_"
     exit 1
}

if ($ProjectsToMove.Count -eq 0) {
    Write-Host "No legacy projects found matching 'repo*' or 'RAKS-project'." -ForegroundColor Yellow
    exit 0
}

Write-Host "Found $($ProjectsToMove.Count) projects to move." -ForegroundColor Cyan

# 4. Move Projects
foreach ($Proj in $ProjectsToMove) {
    Write-Host "Moving '$($Proj.name)' to 'raks-group-legacy'..." -ForegroundColor Yellow
    
    try {
        # API: POST /projects/:id/transfer
        # Body: namespace (ID or path of matching group)
        $TransferUrl = "$BaseUrl/api/v4/projects/$($Proj.id)/transfer"
        
        # Try using the Full Path of the subgroup
        $TargetNamespacePath = "raks-group/$LegacyGroupName"
        
        $Body = @{
            namespace = $TargetNamespacePath
        } | ConvertTo-Json
        
        Write-Host "  DEBUG: Sending to $TransferUrl with body: $Body" -ForegroundColor DarkGray
        
        try {
            Invoke-RestMethod -Uri $TransferUrl -Headers $Headers -Method Post -Body $Body
            Write-Host "  [OK] Moved." -ForegroundColor Green
        } catch {
             Write-Host "  [FAILED] Could not transfer project '$($Proj.name)': $_" -ForegroundColor Red
             # Check if we can get more error details
             if ($_.Exception.Response) {
                 $Stream = $_.Exception.Response.GetResponseStream()
                 $Reader = New-Object System.IO.StreamReader($Stream)
                 Write-Host "    Server Response: $($Reader.ReadToEnd())" -ForegroundColor Red
             }
        }
    } catch {
        Write-Error "  [FAILED] Unexpected error processing '$($Proj.name)': $_"
        # Continue to next
    }
}

Write-Host "Migration Complete." -ForegroundColor Green
