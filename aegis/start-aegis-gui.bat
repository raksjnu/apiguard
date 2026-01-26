@echo off
REM Aegis GUI Launcher for Windows
REM You can change the port by setting the AEGIS_PORT environment variable
REM Example: set AEGIS_PORT=9090 before running this script

REM Get the directory where this script is located
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

echo ============================================================
echo           Aegis GUI Launcher
echo ============================================================
echo.

REM Check if JAR exists
if not exist "target\aegis-1.0.0.jar" (
    echo ERROR: JAR file not found: target\aegis-1.0.0.jar
    echo Please run: mvn clean install -DskipTests
    echo.
    pause
    exit /b 1
)

set "JAR_FILE=target\aegis-1.0.0.jar"

REM Set default port if not specified
if "%AEGIS_PORT%"=="" set AEGIS_PORT=8080

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

echo Starting Aegis GUI on port %AEGIS_PORT%...
echo Using Java: "%JAVA_CMD%"
echo.
echo Press Ctrl+C to stop the server
echo.

"%JAVA_CMD%" -jar "%JAR_FILE%" --gui --port %AEGIS_PORT%

REM Keep window open if there was an error
if errorlevel 1 (
    echo.
    echo ERROR: Failed to start Aegis GUI
    echo.
    pause
)
