@echo off
setlocal

REM Set JAVA_HOME to JDK 17
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [INFO] Using Preferred JDK 17 at: %JAVA_HOME%
echo.

REM Build the project
echo ============================================================
echo   Building RaksAnalyzer
echo ============================================================
call mvn clean package -DskipTests
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Build failed!
    exit /b 1
)

echo.
echo ============================================================
echo   BUILD SUCCESSFUL!
echo ============================================================
echo.

REM Start the server
echo ============================================================
echo   Starting RaksAnalyzer Server
echo ============================================================
echo.

REM Check if port 8080 is in use
echo [INFO] Checking for processes using port 8080...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8080 ^| findstr LISTENING') do (
    echo [WARNING] Port 8080 is in use by PID %%a
    echo [INFO] Attempting to stop process...
    taskkill /F /PID %%a >nul 2>&1
    timeout /t 2 /nobreak >nul
)

REM Display JAR file info
echo [INFO] JAR File Details:
dir target\raksanalyzer-1.0.0.jar | findstr raksanalyzer

REM Start the application
echo.
echo [INFO] Starting RaksAnalyzer on http://localhost:8080
echo [INFO] Press Ctrl+C to stop the server
echo.
java -jar target\raksanalyzer-1.0.0.jar %*

endlocal
