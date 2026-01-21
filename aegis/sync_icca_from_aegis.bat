@echo off
setlocal enabledelayedexpansion

:: =============================================================================
:: Sync Script: ICCA -> Main Aegis
:: Description: Synchronizes relevant source files from icca to main project
:: =============================================================================

set "SOURCE=C:\raks\apiguard\aegis"
set "DEST=C:\raks\aegis_java_icca"

echo ============================================================
echo  Aegis Synchronization: Main -> ICCA (Updated Only)
echo ============================================================
echo SOURCE: %SOURCE%
echo DEST  : %DEST%
echo.

:: Exclusions for src folder
set "EXCLUDE_DIRS=target bin build .git .idea .vscode logs temp Aegis-reports .settings"

set "TEMP_LOG=%TEMP%\aegis_sync.log"
if exist "%TEMP_LOG%" del "%TEMP_LOG%"

echo Running synchronization...

:: 1. Sync the src directory
echo [1/2] Syncing src directory...
:: /XO: Exclude Older files (only copy newer ones)
:: /FP: Include Full Pathnames in the output
robocopy "%SOURCE%\src" "%DEST%\src" /E /XO /NP /FP /XD %EXCLUDE_DIRS% > "%TEMP_LOG%"

:: 2. Sync specific root files
echo [2/2] Syncing specific root files...
robocopy "%SOURCE%" "%DEST%" "ENTERPRISE_ONBOARDING.md" "pom.xml" "README.md" /XO /NP /FP >> "%TEMP_LOG%"

:: Display original logs (optional, but keep per user's request)
type "%TEMP_LOG%"

echo.
echo ============================================================
echo  SUMMARY OF COPIED FILES:
echo ============================================================
:: Extract lines indicating a copy occurred (Newer, New File, etc.)
findstr /C:"Newer" /C:"New File" /C:"Modified" "%TEMP_LOG%"
if %ERRORLEVEL% NEQ 0 echo No files were updated.

if exist "%TEMP_LOG%" del "%TEMP_LOG%"

:: Handle Robocopy exit codes
:: 0-7 are technical success codes (0=no change, 1=files copied, etc.)
if %ERRORLEVEL% GEQ 8 (
    echo [ERROR] Robocopy encountered a major error.
    exit /b %ERRORLEVEL%
) else (
    echo.
    echo [SUCCESS] Synchronization complete.
    exit /b 0
)

echo ============================================================
pause
