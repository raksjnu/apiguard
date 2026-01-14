@echo off
setlocal
cd /d "%~dp0"

REM --- Java Version Check ---
if not defined JAVA_HOME (
    set "PREFERRED_JAVA_HOME=C:\Program Files\Java\jdk-17"
    if exist "%PREFERRED_JAVA_HOME%" (
        set "JAVA_HOME=%PREFERRED_JAVA_HOME%"
    )
)

if defined JAVA_HOME (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
) else (
    echo [INFO] JAVA_HOME not set. Using java from system PATH.
)
REM --------------------------

REM --- Mule Runtime Configuration ---
set "PREFERRED_MULE_HOME=C:\raks\mule-enterprise-standalone-4.10.2"
REM --------------------------

REM ===================================================================
REM ApiGuard Wrapper Deployment Script
REM ===================================================================

REM Configure the JAR file path
set "JAR_PATH=%~dp0target\apiguardwrapper-1.0.0-mule-application.jar"
set "APP_NAME=apiguardwrapper.jar"

REM Configure Mule Runtime location
REM Priority: 1) MULE_RUNTIME_HOME env var, 2) PREFERRED_MULE_HOME, 3) Relative path
if defined MULE_RUNTIME_HOME (
    set "MULE_HOME=%MULE_RUNTIME_HOME%"
    echo [INFO] Using MULE_RUNTIME_HOME: %MULE_HOME%
) else if exist "%PREFERRED_MULE_HOME%" (
    set "MULE_HOME=%PREFERRED_MULE_HOME%"
    echo [INFO] Using Preferred Mule Runtime at: %MULE_HOME%
) else (
    set "MULE_HOME=%~dp0..\mule-enterprise-standalone-4.10.2"
    echo [INFO] Using relative Mule Runtime at: %MULE_HOME%
)

set "MULE_APPS=%MULE_HOME%\apps"

REM ===================================================================
REM Deployment Process
REM ===================================================================

echo [INFO] ApiGuard Wrapper Deployment
echo [INFO] ========================================
echo [INFO] Source JAR: %JAR_PATH%
echo [INFO] Target: %MULE_APPS%\%APP_NAME%
echo.

REM Check if source JAR exists
if not exist "%JAR_PATH%" (
    echo [ERROR] Source JAR not found: %JAR_PATH%
    echo [ERROR] Please run build-apiguardwrapper.bat first.
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
echo [INFO] The application will be available at: http://localhost:8081/apiguard
echo.
