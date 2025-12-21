@echo off
REM ===================================================================
REM MuleGuard Wrapper - Complete Build Script
REM ===================================================================
REM This script:
REM 1. Builds the muleguard JAR
REM 2. Copies it to muleguardwrapper/lib/
REM 3. Builds the muleguardwrapper Mule application
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
echo   MuleGuard Wrapper - Complete Build
echo ============================================================
echo.

REM Step 1: Build muleguard JAR
echo [1/3] Building muleguard JAR...
echo ============================================================
cd /d "%SCRIPT_DIR%..\muleguard"
call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] MuleGuard build failed!
    pause
    exit /b 1
)

echo.
echo [INFO] MuleGuard JAR built successfully!
echo.

REM Step 2: Copy JAR to wrapper lib
echo [2/3] Copying JAR to muleguardwrapper\lib...
echo ============================================================
copy "target\muleguard-1.0.0-jar-with-raks.jar" "%SCRIPT_DIR%lib\muleguard-1.0.0-jar-with-raks.jar" /Y

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] JAR copy failed!
    pause
    exit /b 1
)

echo.
echo [INFO] JAR copied successfully!
echo.

REM Step 3: Build wrapper
echo [3/3] Building muleguardwrapper...
echo ============================================================
cd /d "%SCRIPT_DIR%"
call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] MuleGuardWrapper build failed!
    pause
    exit /b 1
)

echo.
echo ============================================================
echo   BUILD SUCCESSFUL!
echo ============================================================
echo.
echo MuleGuard JAR: %SCRIPT_DIR%lib\muleguard-1.0.0-jar-with-raks.jar
echo Wrapper JAR:   %SCRIPT_DIR%target\muleguardwrapper-1.0.0-mule-application.jar
echo.
echo Next steps:
echo   - To deploy to Mule: run deploy-muleguardwrapper.bat
echo   - To clean redeploy:  run clean-redeploy-muleguardwrapper.bat
echo.
