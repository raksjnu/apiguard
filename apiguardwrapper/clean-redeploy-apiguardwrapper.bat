@echo off
setlocal
cd /d "%~dp0"

echo ============================================================
echo   ApiGuard Wrapper - Clean Redeploy
echo ============================================================
echo.
echo.

REM External project builds removed per user request

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
