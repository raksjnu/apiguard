@echo off
REM ============================================
REM API Discovery Tool - Windows Launcher
REM ============================================

echo.
echo ==========================================
echo    API Discovery Tool - Launcher
echo ==========================================
echo.

REM Kill any existing process on port 8085
echo [INFO] Checking for existing process on port 8085...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8085 ^| findstr LISTENING') do (
    echo [INFO] Found process %%a on port 8085, terminating...
    taskkill /F /PID %%a >nul 2>&1
    if errorlevel 1 (
        echo [WARN] Could not kill process %%a
    ) else (
        echo [INFO] Process %%a terminated successfully
    )
)

REM Wait a moment for port to be released
timeout /t 2 /nobreak >nul

REM Check if JAR exists
if not exist "target\apidiscovery-1.0.0.jar" (
    echo [INFO] JAR not found. Building...
    call mvn clean package -DskipTests
)

REM Start the application
echo.
echo [INFO] Starting API Discovery Tool...
echo.
java -jar target\apidiscovery-1.0.0.jar

REM If the application exits, pause to see any error messages
if errorlevel 1 (
    echo.
    echo [ERROR] Application exited with error code %errorlevel%
    pause
)
