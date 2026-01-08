@echo off
REM Cleanup script for locked Git repositories
REM This script forcefully deletes temp repositories that may be locked by JGit

echo ========================================
echo Cleaning up temporary Git repositories
echo ========================================

set TEMP_DIR=C:\raks\temp

if not exist "%TEMP_DIR%" (
    echo Temp directory does not exist: %TEMP_DIR%
    exit /b 0
)

echo.
echo WARNING: This will delete all repositories in %TEMP_DIR%
echo Press Ctrl+C to cancel, or
pause

echo.
echo Attempting to delete %TEMP_DIR%...

REM Try normal deletion first
rd /s /q "%TEMP_DIR%" 2>nul

if exist "%TEMP_DIR%" (
    echo Normal deletion failed. Attempting forceful deletion...
    
    REM Use PowerShell for more aggressive deletion
    powershell -Command "Get-ChildItem -Path '%TEMP_DIR%' -Recurse | ForEach-Object { $_.Attributes = 'Normal' }; Remove-Item -Path '%TEMP_DIR%' -Recurse -Force"
    
    if exist "%TEMP_DIR%" (
        echo.
        echo FAILED: Could not delete %TEMP_DIR%
        echo.
        echo This usually means:
        echo 1. Mule Runtime is still running and holding file locks
        echo 2. Another process is accessing the files
        echo.
        echo SOLUTION:
        echo 1. Stop Mule Runtime completely (Ctrl+C in the run-muletime-raks.bat window)
        echo 2. Run this script again
        echo 3. Restart Mule Runtime
        echo.
        pause
        exit /b 1
    )
)

echo.
echo SUCCESS: Cleaned up %TEMP_DIR%
echo You can now restart Mule Runtime and try the bulk download again.
echo.
pause
