@echo off
REM ===================================================================
REM API Discovery Wrapper - Deploy to Standalone Mule Runtime
REM ===================================================================

setlocal
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

REM --- Mule Runtime Path ---
set "PREFERRED_MULE_HOME=C:\raks\mule-enterprise-standalone-4.10.1"

if exist "%PREFERRED_MULE_HOME%" (
    set "MULE_HOME=%PREFERRED_MULE_HOME%"
    echo [INFO] Using Preferred Mule Runtime at: %MULE_HOME%
) else (
    echo [WARN] Preferred Mule Runtime not found at: %PREFERRED_MULE_HOME%
    echo [INFO] Falling back to system MULE_HOME: %MULE_HOME%
)
REM --------------------------

echo.
echo ============================================================
echo   API Discovery Wrapper Deployment
echo ============================================================
echo.

set "SOURCE_JAR=%SCRIPT_DIR%target\apidiscoverywrapper-1.0.0-mule-application.jar"
set "TARGET_JAR=%MULE_HOME%\apps\apidiscoverywrapper.jar"

echo [INFO] Source: %SOURCE_JAR%
echo [INFO] Target: %TARGET_JAR%
echo.

if not exist "%SOURCE_JAR%" (
    echo [ERROR] Build artifact not found! Please run build-apidiscoverywrapper.bat first.
    pause
    exit /b 1
)

echo [INFO] Deploying to Standalone Runtime...
echo ============================================================
copy /Y "%SOURCE_JAR%" "%TARGET_JAR%"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ============================================================
    echo   Deployment Successful!
    echo ============================================================
    echo.
    echo [INFO] Monitor the Mule Runtime console for startup logs.
    echo [INFO] The application will be available at: http://localhost:8081
    echo.
) else (
    echo.
    echo [ERROR] Deployment failed!
    pause
    exit /b 1
)
