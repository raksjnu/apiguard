@echo off
REM ===================================================================
REM ApiGuard Wrapper - Complete Dependency Build Script
REM ===================================================================
REM This script:
REM 1. Builds and Installs RaksAnalyzer (Dependency)
REM 2. Builds and Installs MuleGuard (Dependency)
REM 3. Builds the ApiGuardWrapper Mule application
REM ===================================================================

setlocal
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

REM --- Java Version Check ---
set "PREFERRED_JAVA_HOME=C:\Program Files\Java\jdk-17"

if exist "%PREFERRED_JAVA_HOME%" (
    set "JAVA_HOME=%PREFERRED_JAVA_HOME%"
    set "PATH=%JAVA_HOME%\bin;%PATH%"
    echo [INFO] Using Preferred JDK 17 at: %JAVA_HOME%
) else (
    echo [WARN] Preferred JDK 17 not found at: %PREFERRED_JAVA_HOME%
    echo [INFO] Falling back to system JAVA_HOME: %JAVA_HOME%
)
REM --------------------------

echo.
echo ============================================================
echo   ApiGuard Wrapper - Full Dependency Build
echo ============================================================
echo.

REM Step 1: Build & Install RaksAnalyzer
echo [1/3] Building & Installing RaksAnalyzer...
echo ============================================================
if exist "%SCRIPT_DIR%..\raksanalyzer" (
    cd /d "%SCRIPT_DIR%..\raksanalyzer"
    call mvn clean install -DskipTests
    
    if errorlevel 1 (
        echo.
        echo [ERROR] RaksAnalyzer build failed!
        pause
        exit /b 1
    )
) else (
    echo [ERROR] RaksAnalyzer project not found at ..\raksanalyzer
    pause
    exit /b 1
)

REM Step 2: Build & Install MuleGuard
echo.
echo [2/3] Building & Installing MuleGuard...
echo ============================================================
if exist "%SCRIPT_DIR%..\muleguard" (
    cd /d "%SCRIPT_DIR%..\muleguard"
    call mvn clean install -DskipTests
    
    if errorlevel 1 (
        echo.
        echo [ERROR] MuleGuard build failed!
        pause
        exit /b 1
    )
) else (
    echo [ERROR] MuleGuard project not found at ..\muleguard
    pause
    exit /b 1
)

REM Step 3: Build ApiGuardWrapper
echo.
echo [3/3] Building ApiGuardWrapper...
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
