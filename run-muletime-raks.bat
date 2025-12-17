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

REM Configure relative paths
set "MULE_HOME=%~dp0\mule\mule-enterprise-standalone-4.10.1" 
REM Fallback to hardcoded if relative doesn't exist (legacy support)
if not exist "%MULE_HOME%" set "MULE_HOME=C:\raks\mule-enterprise-standalone-4.10.1"

set "MULE_BASE=%MULE_HOME%"

echo [INFO] Starting Mule Standalone Runtime...
echo [INFO] MULE_HOME=%MULE_HOME%

if exist "%MULE_HOME%\bin\mule.bat" (
    cd /d "%MULE_HOME%\bin"
    call mule.bat
) else (
    echo [ERROR] Mule runtime not found at: %MULE_HOME%
    pause
    exit /b 1
)
