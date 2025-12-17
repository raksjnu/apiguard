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

echo [INFO] Building Apiguard Portal using %JAVA_HOME%...

REM Already in project dir
call mvn clean package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build Failed!
    exit /b %ERRORLEVEL%
)

REM Configure Mule Home (Up one level, then into mule)
set "MULE_HOME=%~dp0..\mule\mule-enterprise-standalone-4.10.1"
if not exist "%MULE_HOME%" set "MULE_HOME=C:\raks\mule-enterprise-standalone-4.10.1"

echo [INFO] Deploying to Standalone Runtime...
copy "target\apiguard-portal-1.0.0-SNAPSHOT-mule-application.jar" "%MULE_HOME%\apps\apiguard-portal.jar" /Y

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Deployment Copy Failed!
    exit /b %ERRORLEVEL%
)

echo [INFO] Deployment Successful!
echo [INFO] Monitor the Mule Runtime console for startup logs.
