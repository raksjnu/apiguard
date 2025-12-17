@echo off
REM MuleGuard GUI Launcher for Windows
REM You can change the port by setting the MULEGUARD_PORT environment variable
REM Example: set MULEGUARD_PORT=9090 before running this script

REM Get the directory where this script is located
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

echo ============================================================
echo           MuleGuard GUI Launcher
echo ============================================================
echo.

REM Check if JAR exists
if not exist "target\muleguard-1.0.0-jar-with-raks.jar" (
    echo ERROR: JAR file not found!
    echo Please run: mvn clean package
    echo.
    pause
    exit /b 1
)

REM Set default port if not specified
if "%MULEGUARD_PORT%"=="" set MULEGUARD_PORT=8080

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

REM Find Java command
if defined JAVA_HOME (
    set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA_CMD=java"
)

REM Check Java Version (Simple check for "version" string output)
"%JAVA_CMD%" -version 2>&1 | findstr /i "version"
echo.

echo Starting MuleGuard GUI on port %MULEGUARD_PORT%...
echo Using Java: "%JAVA_CMD%"
echo.
echo Press Ctrl+C to stop the server
echo.

"%JAVA_CMD%" -cp target\muleguard-1.0.0-jar-with-raks.jar com.raks.muleguard.gui.MuleGuardGUI %MULEGUARD_PORT%

REM Keep window open if there was an error
if errorlevel 1 (
    echo.
    echo ERROR: Failed to start MuleGuard GUI
    echo.
    pause
)
