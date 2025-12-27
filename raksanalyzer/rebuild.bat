@echo off
REM ===================================================================
REM RaksAnalyzer - Build Script
REM ===================================================================

setlocal
cd /d "%~dp0"

REM Java Version Check
set "PREFERRED_JAVA_HOME=C:\Program Files\Java\jdk-17"

if exist "%PREFERRED_JAVA_HOME%" (
    set "JAVA_HOME=%PREFERRED_JAVA_HOME%"
    set "PATH=%JAVA_HOME%\bin;%PATH%"
    echo [INFO] Using JDK 17 at: %JAVA_HOME%
) else (
    echo [WARN] Preferred JDK 17 not found, using system JAVA_HOME
)

echo.
echo ============================================================
echo   Building RaksAnalyzer
echo ============================================================
echo.

call mvn clean install -DskipTests

if %ERRORLEVEL% EQU 0 (
    echo.
    echo   BUILD SUCCESSFUL!
    echo.
) else (
    echo.
    echo   BUILD FAILED!
    echo.
    pause
    exit /b 1
)
