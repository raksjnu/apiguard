@echo off
setlocal
cd /d "%~dp0"

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

echo Starting API Comparison Tool Web GUI...
echo.

if not exist "target\apiforge-1.0.0-jar-with-raks.jar" (
    echo [INFO] JAR not found. Building...
    call mvn clean package -DskipTests
)

echo [INFO] Launching JAR in GUI mode...
java -jar target\apiforge-1.0.0-jar-with-raks.jar --gui

pause
