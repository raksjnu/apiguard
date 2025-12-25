@echo off
REM ===================================================================
REM RaksAnalyzer Startup Script
REM ===================================================================
REM This script starts RaksAnalyzer in the current terminal window
REM ===================================================================

setlocal
cd /d "%~dp0"

REM --- Java Version Check ---
set "PREFERRED_JAVA_HOME=C:\Program Files\Java\jdk-17"

if exist "%PREFERRED_JAVA_HOME%" (
    set "JAVA_HOME=%PREFERRED_JAVA_HOME%"
    set "PATH=%JAVA_HOME%\bin;%PATH%"
    echo [INFO] Using Preferred JDK 17 at: %JAVA_HOME%
) else (
    echo [WARN] Preferred JDK 17 not found at: %PREFERRED_JAVA_HOME%
    echo [INFO] Falling back to system JAVA_HOME: %JAVA_HOME%
)
REM --------------------------

echo.
echo ============================================================
echo   Starting RaksAnalyzer
echo ============================================================
echo.

REM Kill any process using port 8080
echo [INFO] Checking for processes using port 8080...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8080 ^| findstr LISTENING') do (
    echo [INFO] Found process %%a using port 8080, terminating...
    taskkill /F /PID %%a >nul 2>&1
)
timeout /t 2 /nobreak >nul

REM Start the application
echo [INFO] JAR File Details:
dir "target\raksanalyzer-1.0.0.jar" | findstr "raksanalyzer-1.0.0.jar"
"%JAVA_HOME%\bin\java" -jar "target\raksanalyzer-1.0.0.jar"

echo.
echo [INFO] RaksAnalyzer has exited
echo.

pause
