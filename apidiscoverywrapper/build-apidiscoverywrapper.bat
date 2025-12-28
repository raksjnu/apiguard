@echo off
REM ===================================================================
REM API Discovery Wrapper - Build Script
REM ===================================================================
REM This script:
REM 1. Builds and Packages API Discovery (Dependency)
REM 2. Builds the API Discovery Wrapper Mule application
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
echo   API Discovery Wrapper - Build
echo ============================================================
echo.

REM Step 1: Build & Package API Discovery
echo [1/2] Building & Packaging API Discovery...
echo ============================================================
if exist "%SCRIPT_DIR%..\apidiscovery" (
    cd /d "%SCRIPT_DIR%..\apidiscovery"
    call mvn clean package -DskipTests
    
    if errorlevel 1 (
        echo.
        echo [ERROR] API Discovery build failed!
        pause
        exit /b 1
    )
    
    REM Copy apidiscovery JAR to apidiscoverywrapper/lib
    echo.
    echo [INFO] Copying apidiscovery JAR to lib folder...
    cmd /c "if exist \"%SCRIPT_DIR%..\apidiscovery\target\apidiscovery-1.0.0-SNAPSHOT-jar-with-dependencies.jar\" (xcopy /Y /Q \"%SCRIPT_DIR%..\apidiscovery\target\apidiscovery-1.0.0-SNAPSHOT-jar-with-dependencies.jar\" \"%SCRIPT_DIR%lib\apidiscovery-1.0.0.jar\" >nul 2>&1 && echo [INFO] apidiscovery-1.0.0.jar copied successfully || echo [WARN] Failed to copy apidiscovery JAR) else (echo [WARN] apidiscovery JAR not found in target)"
) else (
    echo [ERROR] API Discovery project not found at ..\apidiscovery
    pause
    exit /b 1
)

REM Step 2: Build API Discovery Wrapper
echo.
echo [2/2] Building API Discovery Wrapper...
echo ============================================================
cd /d "%SCRIPT_DIR%"
call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] API Discovery Wrapper build failed!
    pause
    exit /b 1
)

echo.
echo ============================================================
echo   BUILD SUCCESSFUL!
echo ============================================================
echo.
echo Wrapper JAR: %SCRIPT_DIR%target\apidiscoverywrapper-1.0.0-mule-application.jar
echo.
