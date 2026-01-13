@echo off
setlocal
cd /d "%~dp0"

echo ============================================================
echo   ApiGuard Wrapper - Clean Redeploy
echo ============================================================
echo.
echo.

REM --- Step 0: Build & Copy API Discovery (User Request) ---
echo [Step 0] Building API Discovery dependency...
set "SCRIPT_DIR=%~dp0"
if exist "%SCRIPT_DIR%..\apidiscovery" (
    cd /d "%SCRIPT_DIR%..\apidiscovery"
    call mvn clean package -DskipTests
    if errorlevel 1 (
       echo [ERROR] API Discovery build failed. Aborting.
       pause
       exit /b 1
    )
    echo [INFO] Copying API Discovery JAR to wrapper lib...
    copy /Y "target\apidiscovery-1.0.0-with-raks.jar" "%SCRIPT_DIR%lib\apidiscovery-1.0.0.jar"
    cd /d "%SCRIPT_DIR%"
)

echo [Step 1] Building Project...
call build-apiguardwrapper.bat

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed. Aborting deployment.
    exit /b 1
)

echo.
echo [Step 2] Deploying Project...
call deploy-apiguardwrapper.bat

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Deployment failed.
    exit /b 1
)

echo.
echo [INFO] Clean Redeploy Complete!
echo.
