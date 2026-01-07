$ErrorActionPreference = "Stop"

# Configuration
$GITLAB_TOKEN = "glpat-8xWKilsBixw-i6hMcR1FvW86MQp1OmplYXcwCw.01.120yhiotd"
$GROUP_PATH = "raks-group"
$REPO_NAME = "repo1-mule-api"
$TEMP_DIR = "C:\raks\apiguard\temp_test_modification"

# Cleanup
if (Test-Path $TEMP_DIR) { Remove-Item -Recurse -Force $TEMP_DIR }
New-Item -ItemType Directory -Force -Path $TEMP_DIR | Out-Null

Set-Location $TEMP_DIR

# Clone
$repoUrl = "https://oauth2:${GITLAB_TOKEN}@gitlab.com/${GROUP_PATH}/${REPO_NAME}.git"
Write-Host "Cloning $REPO_NAME..."
git clone $repoUrl .

# Checkout and Create WIP Branch
Write-Host "Checking out wip branch..."
git checkout -B wip

# Modify Files
Write-Host "Modifying files..."
# 1. Modify README.md
$readmeContent = "`n`n## Diff Test Update`nThis section was added automatically to test the GitAnalyzer diff viewer."
Add-Content -Path "README.md" -Value $readmeContent

# 2. Modify mule-app.properties (if exists)
if (Test-Path "mule-app.properties") {
    Add-Content -Path "mule-app.properties" -Value "`n# Test Property`ntest.feature.enabled=true"
} else {
    Set-Content -Path "mule-app.properties" -Value "app.name=${REPO_NAME}`n# Test Property`ntest.feature.enabled=true"
}

# 3. Create a New File
Set-Content -Path "test_new_file.xml" -Value "<root><test>This is a new file</test></root>"

# 4. Delete a file (if exists, e.g. wip_changes.txt from previous runs)
if (Test-Path "wip_changes.txt") {
    Remove-Item "wip_changes.txt"
}

# Commit and Push
Write-Host "Committing and Pushing..."
git config user.email "tester@raks.com"
git config user.name "TesterBot"
git add .
git commit -m "Auto-generated test changes for Diff Analysis"
git push -u origin wip --force

Write-Host "Done. Changes pushed to 'wip' branch of $REPO_NAME"
