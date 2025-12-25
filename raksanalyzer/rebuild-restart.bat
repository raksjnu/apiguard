@echo off
REM ===================================================================
REM RaksAnalyzer - Rebuild and Restart Script
REM ===================================================================

setlocal
cd /d "%~dp0"

REM Java Version Check
set "PREFERRED_JAVA_HOME=C:\Program Files\Java\jdk-17"

if exist "%PREFERRED_JAVA_HOME%" (
    set "JAVA_HOME=%PREFERRED_JAVA_HOME%"
    set "PATH=%JAVA_HOME%\bin;%PATH%"
    echo [INFO] Using JDK 17 at: %JAVA_HOME%
) else (
    echo [WARN] Preferred JDK 17 not found, using system JAVA_HOME
)

echo.
echo ============================================================
echo   Building RaksAnalyzer
echo ============================================================
echo.

call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo   BUILD FAILED!
    echo.
    pause
    exit /b 1
)

echo.
echo   BUILD SUCCESSFUL!
echo.

echo.
echo ============================================================
echo   Starting RaksAnalyzer
echo ============================================================
echo.

REM Kill any process using port 8080
echo [INFO] Checking for processes using port 8080...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8080 ^| findstr LISTENING') do (
    echo [INFO] Terminating process %%a on port 8080...
    taskkill /F /PID %%a >nul 2>&1
)
timeout /t 2 /nobreak >nul

REM Start the application
java -jar "target\raksanalyzer-1.0.0.jar"

echo.
echo [INFO] RaksAnalyzer has exited
echo.

pause
