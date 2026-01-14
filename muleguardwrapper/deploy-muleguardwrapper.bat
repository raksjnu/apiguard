@echo off
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

REM ===================================================================
REM MuleGuard Wrapper Deployment Script
REM ===================================================================

REM Configure the JAR file path
set "JAR_PATH=%~dp0target\muleguardwrapper-1.0.0-mule-application.jar"
set "APP_NAME=muleguardwrapper.jar"

REM Configure Mule Runtime location (Relative first, then absolute fallback)
set "MULE_HOME=%~dp0..\mule\mule-enterprise-standalone-4.10.2"
if not exist "%MULE_HOME%" set "MULE_HOME=C:\raks\mule-enterprise-standalone-4.10.2"

set "MULE_APPS=%MULE_HOME%\apps"

REM ===================================================================
REM Deployment Process
REM ===================================================================

echo [INFO] MuleGuard Wrapper Deployment
echo [INFO] ========================================
echo [INFO] Source JAR: %JAR_PATH%
echo [INFO] Target: %MULE_APPS%\%APP_NAME%
echo.

REM Check if source JAR exists
if not exist "%JAR_PATH%" (
    echo [ERROR] Source JAR not found: %JAR_PATH%
    echo [ERROR] Please verify the JAR_PATH variable is correct.
    exit /b 1
)

REM Check if Mule apps directory exists
if not exist "%MULE_APPS%" (
    echo [ERROR] Mule apps directory not found: %MULE_APPS%
    echo [ERROR] Please verify MULE_HOME is correct.
    exit /b 1
)

echo [INFO] Deploying to Standalone Runtime...
copy "%JAR_PATH%" "%MULE_APPS%\%APP_NAME%" /Y

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Deployment Copy Failed!
    exit /b %ERRORLEVEL%
)

echo.
echo [INFO] ========================================
echo [INFO] Deployment Successful!
echo [INFO] ========================================
echo [INFO] Monitor the Mule Runtime console for startup logs.
echo [INFO] The application will be available at: http://localhost:8081/muleguard
echo.
