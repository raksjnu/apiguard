@echo off
REM ===================================================================
REM ApiGuard Wrapper - Build with API Discovery
REM ===================================================================
REM This script:
REM 1. Builds and Installs API Discovery (Dependency)
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
echo   ApiGuard Wrapper - Build with API Discovery
echo ============================================================
echo.

REM Step 1: Build & Package API Discovery
echo [1/2] Building ^& Packaging API Discovery...
echo ============================================================
if exist "%SCRIPT_DIR%..\apidiscovery" (
    cd /d "%SCRIPT_DIR%..\apidiscovery"
    call mvn clean install -DskipTests
    
    if errorlevel 1 (
        echo.
        echo [ERROR] API Discovery build failed!
        pause
        exit /b 1
    )
    
    REM Copy apidiscovery JAR to apiguardwrapper/lib
    echo.
    echo [INFO] Copying API Discovery JAR to lib folder...
    
    if exist "%SCRIPT_DIR%..\apidiscovery\target\apidiscovery-1.0.0-with-raks.jar" (
        copy /Y "%SCRIPT_DIR%..\apidiscovery\target\apidiscovery-1.0.0-with-raks.jar" "%SCRIPT_DIR%lib\apidiscovery-1.0.0.jar" >nul
        if %ERRORLEVEL% EQU 0 (
            echo [INFO] apidiscovery-1.0.0.jar copied successfully
        ) else (
            echo [WARN] Failed to copy apidiscovery JAR
        )
    ) else (
        echo [WARN] apidiscovery-1.0.0-with-raks.jar not found in target folder
    )
) else (
    echo [ERROR] API Discovery project not found at ..\apidiscovery
    pause
    exit /b 1
)

REM Step 2: Build ApiGuardWrapper
echo.
echo ============================================================
echo   Step 2: Building apiguardwrapper
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
