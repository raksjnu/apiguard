@echo off
REM ===================================================================
REM Aegis - Build Script
REM ===================================================================
REM This script builds the Aegis Java project
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
echo   Aegis - Build
echo ============================================================
echo.

call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Aegis build failed!
    pause
    exit /b 1
)

echo.
echo ============================================================
echo   BUILD SUCCESSFUL!
echo ============================================================
echo.
echo Aegis JAR: %SCRIPT_DIR%target\aegis-1.0.0.jar
echo.
echo [INFO] To start the GUI, run: start-aegis-gui.bat
echo.
