@echo off
setlocal

REM ===================================================================
REM MuleGuard Wrapper Deployment Script
REM ===================================================================
REM This script deploys a pre-built MuleGuard JAR to Mule Runtime
REM You can modify the JAR_PATH variable to point to a different JAR
REM ===================================================================

REM Configure the JAR file path (modify this as needed)
set "JAR_PATH=C:\muleguard-fixed\tmp\muleguard\muleguardwrapper\target\muleguardwrapper-1.0.0-mule-application.jar"

REM Configure the deployment name (the name it will have in Mule apps folder)
set "APP_NAME=muleguardwrapper.jar"

REM Configure Mule Runtime location
set "MULE_HOME=C:\raks\mule-enterprise-standalone-4.10.2"
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
