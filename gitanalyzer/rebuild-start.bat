@echo off
setlocal
cd /d "%~dp0"

echo [INFO] Rebuilding and Starting GitAnalyzer...

call build.bat
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed. Aborting start.
    exit /b %ERRORLEVEL%
)

call start.bat %*
