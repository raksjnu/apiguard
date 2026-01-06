@echo off
setlocal
cd /d "%~dp0"

echo [INFO] Build Script for GitAnalyzer
echo [INFO] Checking for existing process on port 6060...

REM Check for process on port 6060 and kill it
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":6060"') do (
    echo [WARN] Port 6060 is in use by PID %%a. Killing process...
    taskkill /F /PID %%a >nul 2>&1
)

REM Double check java.exe if port check fails or for good measure
REM taskkill /F /IM "java.exe" /FI "WINDOWTITLE eq GitAnalyzer*" >nul 2>&1

echo [INFO] running mvn clean install...
call mvn clean install -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build Failed!
    pause
    exit /b %ERRORLEVEL%
)

echo [INFO] Build Successful.
echo [INFO] Run start.bat to launch the application.
pause
