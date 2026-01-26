@echo off
setlocal
cd /d "%~dp0"

REM --- Java Version Check ---
set "PREFERRED_JAVA_HOME=C:\Program Files\Java\jdk-17"

if exist "%PREFERRED_JAVA_HOME%" (
    set "JAVA_HOME=%PREFERRED_JAVA_HOME%"
    set "PATH=%JAVA_HOME%\bin;%PATH%"
    echo [INFO] Using Preferred JDK 17 at: %JAVA_HOME%
)


REM Check for process on port 6060 and kill it to ensure clean start
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":6060"') do (
    echo [WARN] Port 6060 is in use by PID %%a. Killing process to ensure clean start...
    taskkill /F /PID %%a >nul 2>&1
)

REM Set APP_HOME to current directory (project root)
set "APP_HOME=%~dp0."
echo [INFO] Starting GitAnalyzer...
echo [INFO] APP_HOME: %APP_HOME%

if exist "target\gitanalyzer-1.0.0.jar" (
    set "JAR_FILE=target\gitanalyzer-1.0.0.jar"
) else (
    echo [ERROR] JAR not found. Please run build.bat first.
    pause
    exit /b 1
)

java -Dapp.home="%APP_HOME%" -jar "%JAR_FILE%" %*
