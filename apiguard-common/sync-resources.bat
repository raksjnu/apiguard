@echo off
setlocal
echo [INFO] Syncing Raks Common Resources...

REM Try to find apiguard-common at one level up
set "COMMON_DIR=%~dp0..\apiguard-common"

REM If not found, try two levels up
if not exist "%COMMON_DIR%" (
    set "COMMON_DIR=%~dp0..\..\apiguard-common"
)

REM If still not found, error out
if not exist "%COMMON_DIR%" (
    echo [ERROR] Common directory not found. Checked:
    echo 1. %~dp0..\apiguard-common
    echo 2. %~dp0..\..\apiguard-common
    exit /b 1
)

set "TARGET_DIR=%~dp0src\main\resources\web\assets"
if not exist "%TARGET_DIR%" mkdir "%TARGET_DIR%"

echo [INFO] Found Common at: %COMMON_DIR%
echo [INFO] Copying Images...
xcopy /S /Y /I "%COMMON_DIR%\resources\images" "%TARGET_DIR%\images" >nul

echo [INFO] Copying CSS...
xcopy /S /Y /I "%COMMON_DIR%\resources\css" "%TARGET_DIR%\css" >nul

echo [INFO] Resources Synced Successfully to %TARGET_DIR%
endlocal
