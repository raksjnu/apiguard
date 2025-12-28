@echo off
REM ==========================================
REM   API Discovery Tool - Rebuild & Restart
REM ==========================================

REM Set JAVA_HOME to JDK 17
set JAVA_HOME=C:\Program Files\Java\jdk-17
set PATH=%JAVA_HOME%\bin;%PATH%

echo.
echo ==========================================
echo    Rebuilding API Discovery Tool
echo ==========================================
echo.

REM Stop existing server
echo [INFO] Stopping existing server on port 8085...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8085 ^| findstr LISTENING') do (
    taskkill /F /PID %%a >nul 2>&1
)

REM Build
echo [INFO] Building with Maven...
call mvn clean package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed!
    pause
    exit /b 1
)

echo.
echo [SUCCESS] Build completed!
echo.

REM Start server
echo [INFO] Starting API Discovery Tool...
start "API Discovery Tool" cmd /c run-apidiscovery.bat

echo.
echo [SUCCESS] Server started!
echo [INFO] Access at: http://localhost:8085
echo.
pause
