@echo off
echo ============================================================
echo   MuleGuard Clean Redeploy (Forces Mule to Reload)
echo ============================================================
echo.

REM Get script directory
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

REM Configure Mule location
set "MULE_HOME=C:\raks\mule-enterprise-standalone-4.10.2"
set "MULE_APPS=%MULE_HOME%\apps"
set "APP_NAME=muleguardwrapper"

echo [1/3] Removing old deployment...
if exist "%MULE_APPS%\%APP_NAME%.jar" (
    del /F "%MULE_APPS%\%APP_NAME%.jar"
    echo Deleted: %APP_NAME%.jar
)

if exist "%MULE_APPS%\%APP_NAME%-anchor.txt" (
    del /F "%MULE_APPS%\%APP_NAME%-anchor.txt"
    echo Deleted: %APP_NAME%-anchor.txt
)

if exist "%MULE_APPS%\%APP_NAME%" (
    rmdir /S /Q "%MULE_APPS%\%APP_NAME%"
    echo Deleted: %APP_NAME% folder
)

echo.
echo [2/3] Waiting 3 seconds for Mule to detect removal...
timeout /t 3 /nobreak >nul

echo.
echo [3/3] Deploying fresh copy...
copy "%SCRIPT_DIR%target\muleguardwrapper-1.0.0-mule-application.jar" "%MULE_APPS%\%APP_NAME%.jar" /Y

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Deployment failed!
    pause
    exit /b 1
)

echo.
echo ============================================================
echo   Clean Redeploy Complete!
echo ============================================================
echo.
echo Mule will now reload the application with the new version.
echo Monitor Mule console for: "Application deployed successfully"
echo.
echo The application will be available at: http://localhost:8082/muleguard
echo.
pause
