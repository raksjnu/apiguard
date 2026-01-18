@echo off

REM Get the directory where this script is located
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

REM Optional: Uncomment and set JAVA_HOME if the system JAVA_HOME is missing or incorrect
set "JAVA_HOME=C:\Program Files\Java\jdk-17"

if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

java -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Java not found!
    echo Please ensure Java is installed or set JAVA_HOME in this script.
    pause
    exit /b 1
)

echo [1/3] Copying latest rules.yaml to wrapper resources...
copy /Y "src\main\resources\rules\rules.yaml" "..\apiguardwrapper\src\main\resources\web\aegis\sample-rules.yaml"

echo [2/3] Building Aegis Core...
call mvn clean package -DskipTests -q
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Aegis build failed!
    pause
    exit /b %ERRORLEVEL%
)
echo Aegis JAR built successfully

echo [3/3] Copying JAR to wrapper and rebuilding...
copy /Y "target\aegis-1.0.0-jar-with-raks.jar" "..\apiguardwrapper\lib\aegis-1.0.0.jar"
echo JAR copied to wrapper/lib as aegis-1.0.0.jar

cd ..\apiguardwrapper
call mvn clean package -DskipTests -q
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: wrapper build failed!
    cd ..\aegis
    pause
    exit /b %ERRORLEVEL%
)
cd ..\aegis
echo Wrapper built successfully

echo === DEPLOYMENT COMPLETE ===
echo Next step: RESTART Anypoint Studio
pause
