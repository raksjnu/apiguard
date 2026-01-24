@echo off
REM ===================================================================
REM ApiGuard Wrapper - Build Script with ApiUrlComparison Only
REM ===================================================================
REM This script:
REM 1. Builds and Installs ApiUrlComparison (Dependency)
REM 2. Builds the ApiGuardWrapper Mule application
REM ===================================================================

setlocal
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

REM --- Java Version Check ---
if not defined JAVA_HOME (
    set "PREFERRED_JAVA_HOME=C:\Program Files\Java\jdk-17"
    if exist "%PREFERRED_JAVA_HOME%" (
        set "JAVA_HOME=%PREFERRED_JAVA_HOME%"
    )
)

if defined JAVA_HOME (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
    echo [INFO] Using JAVA_HOME: %JAVA_HOME%
) else (
    echo [INFO] JAVA_HOME not set. Using java from system PATH.
)
REM --------------------------

echo.
echo ============================================================
echo   ApiGuard Wrapper - Build with API Forge Only
echo ============================================================
echo.

REM Step 1: Build & Install ApiUrlComparison
echo [1/2] Building ^& Installing ApiUrlComparison...
echo ============================================================
if exist "%SCRIPT_DIR%..\apiurlcomparison" (
    cd /d "%SCRIPT_DIR%..\apiurlcomparison"
    call mvn clean install -DskipTests
    
    if errorlevel 1 (
        echo.
        echo [ERROR] ApiUrlComparison build failed!
        pause
        exit /b 1
    )
    
    REM Copy apiurlcomparison JAR to apiguardwrapper/lib
    echo.
    echo [INFO] Copying apiurlcomparison JAR to lib folder...
    cmd /c "if exist "%SCRIPT_DIR%..\apiurlcomparison\target\apiurlcomparison-1.0.0-jar-with-raks.jar" (copy /Y "%SCRIPT_DIR%..\apiurlcomparison\target\apiurlcomparison-1.0.0-jar-with-raks.jar" "%SCRIPT_DIR%lib\apiurlcomparison-1.0.0.jar" >nul && echo [INFO] apiurlcomparison JAR copied successfully || echo [WARN] Failed to copy apiurlcomparison JAR) else (echo [WARN] apiurlcomparison JAR not found in target)"

    REM Sync Web Resources to apiguardwrapper
    echo.
    echo [INFO] Synchronizing API Forge Web Resources...
    
    if exist "%SCRIPT_DIR%..\apiurlcomparison\src\main\resources\public" (
        echo [INFO] Source found. Syncing resources...
        if not exist "%SCRIPT_DIR%src\main\resources\web\apiurlcomparison" mkdir "%SCRIPT_DIR%src\main\resources\web\apiurlcomparison"
        xcopy /Y /S /E "%SCRIPT_DIR%..\apiurlcomparison\src\main\resources\public\*" "%SCRIPT_DIR%src\main\resources\web\apiurlcomparison\" >nul
        
        REM Sync Config and Test Data
        echo [INFO] Synchronizing Configuration and Test Data...
        copy /Y "%SCRIPT_DIR%..\apiurlcomparison\src\main\resources\config.yaml" "%SCRIPT_DIR%src\main\resources\web\apiforge\" >nul
        if not exist "%SCRIPT_DIR%src\main\resources\web\apiforge\testData" mkdir "%SCRIPT_DIR%src\main\resources\web\apiforge\testData"
        xcopy /Y /S /E "%SCRIPT_DIR%..\apiurlcomparison\src\main\resources\testData\*" "%SCRIPT_DIR%src\main\resources\web\apiforge\testData\" >nul
        
        echo [INFO] Resources, Config, and Test Data synchronized successfully.
    ) else (
        echo [WARN] Web source not found. Skipping sync.
    )
) else (
    echo [ERROR] ApiUrlComparison project not found at ..\apiurlcomparison
    pause
    exit /b 1
)

REM Step 2: Build ApiGuardWrapper
echo.
echo [2/2] Building ApiGuardWrapper...
echo ============================================================
cd /d "%SCRIPT_DIR%"
call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] ApiGuardWrapper build failed!
    pause
    exit /b 1
)

echo.
echo ============================================================
echo   BUILD SUCCESSFUL!
echo ============================================================
echo.
echo Wrapper JAR: %SCRIPT_DIR%target\apiguardwrapper-1.0.0-mule-application.jar
echo.
