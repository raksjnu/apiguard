@echo off
REM ===================================================================
REM ApiGuard Wrapper - Build Script with MuleGuard Only
REM ===================================================================
REM This script:
REM 1. Builds and Installs MuleGuard (Dependency)
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
echo   ApiGuard Wrapper - Build with MuleGuard Only
echo ============================================================
echo.

REM Step 1: Build & Install MuleGuard
echo [1/2] Building ^& Installing MuleGuard...
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
    
    REM Copy muleguard JAR to apiguardwrapper/lib
    echo.
    echo [INFO] Copying muleguard JAR to lib folder...
    cmd /c "if exist "%USERPROFILE%\.m2\repository\com\raks\muleguard\1.0.0\muleguard-1.0.0.jar" (xcopy /Y /Q "%USERPROFILE%\.m2\repository\com\raks\muleguard\1.0.0\muleguard-1.0.0.jar" "%SCRIPT_DIR%lib\" >nul 2>&1 && echo [INFO] muleguard-1.0.0.jar copied successfully || echo [WARN] Failed to copy muleguard JAR) else (echo [WARN] muleguard JAR not found in .m2 repository)"
) else (
    echo [ERROR] MuleGuard project not found at ..\muleguard
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
