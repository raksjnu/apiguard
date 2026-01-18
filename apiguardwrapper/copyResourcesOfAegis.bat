@echo off
REM ===================================================================
REM Copy Aegis Web Resources to Wrapper
REM ===================================================================
REM This script:
REM 1. Copies all web resources from Aegis project to Wrapper project
REM 2. Copies the generated rule_guide.html from Aegis temp folder
REM ===================================================================

echo [INFO] Syncing Aegis resources...

REM Define paths relative to this script (inside apiguardwrapper folder)
set "AEGIS_WEB_SRC=..\aegis\src\main\resources\web\aegis"
set "AEGIS_TEMP_SRC=..\aegis\temp"
set "WRAPPER_WEB_DST=src\main\resources\web\aegis"

REM Check if source exists
if not exist "%AEGIS_WEB_SRC%" (
    echo [ERROR] Aegis web source not found: %AEGIS_WEB_SRC%
    exit /b 1
)

REM 1. Copy all resources from Aegis Web folder
echo [INFO] Copying from %AEGIS_WEB_SRC% to %WRAPPER_WEB_DST%...
xcopy /E /Y /I /Q "%AEGIS_WEB_SRC%" "%WRAPPER_WEB_DST%" >nul
echo [INFO] Web resources synced.

REM 2. Copy rule_guide.html from temp (generated version)
echo [INFO] Copying rule_guide.html from %AEGIS_TEMP_SRC%...
if exist "%AEGIS_TEMP_SRC%\rule_guide.html" (
    copy /Y "%AEGIS_TEMP_SRC%\rule_guide.html" "%WRAPPER_WEB_DST%\rule_guide.html" >nul
    echo [INFO] rule_guide.html updated from temp.
) else (
    echo [WARN] rule_guide.html not found in temp! Keeping existing version.
)

echo [INFO] Resource sync complete.
