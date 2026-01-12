@echo off
REM ===================================================================
REM ApiGuard Wrapper - Build with FileSync
REM ===================================================================
REM This script:
REM 1. Builds and Installs FileSync (Dependency)
REM 2. Builds the ApiGuardWrapper Mule application
REM ===================================================================

setlocal
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

REM --- Java Version Check ---
if not defined JAVA_HOME (
    set "PREFERRED_JAVA_HOME=C:\Program Files\Java\jdk-17"
    if exist "%PREFERRED_JAVA_HOME%" (
        set "JAVA_HOME=%PREFERRED_JAVA_HOME%"
    )
)

if defined JAVA_HOME (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
    echo [INFO] Using JAVA_HOME: %JAVA_HOME%
) else (
    echo [INFO] JAVA_HOME not set. Using java from system PATH.
)
REM --------------------------

echo.
echo ============================================================
echo   ApiGuard Wrapper - Build with FileSync
echo ============================================================
echo.

REM Step 1: Build & Package FileSync
echo [1/2] Building ^& Packaging FileSync...
echo ============================================================
if exist "%SCRIPT_DIR%..\filesync" (
    cd /d "%SCRIPT_DIR%..\filesync"
    call mvn clean install -DskipTests
    
    if errorlevel 1 (
        echo.
        echo [ERROR] FileSync build failed!
        pause
        exit /b 1
    )
    
    REM Copy filesync JAR to apiguardwrapper/lib
    echo.
    echo [INFO] Copying FileSync JAR to lib folder...
    
    if exist "%SCRIPT_DIR%..\filesync\target\filesync-1.0.0.jar" (
        copy /Y "%SCRIPT_DIR%..\filesync\target\filesync-1.0.0.jar" "%SCRIPT_DIR%lib\filesync-1.0.0.jar" >nul
        if %ERRORLEVEL% EQU 0 (
            echo [INFO] filesync-1.0.0.jar copied successfully
        ) else (
            echo [WARN] Failed to copy filesync JAR
        )
    ) else (
        echo [WARN] filesync-1.0.0.jar not found in target folder
    )
) else (
    echo [ERROR] FileSync project not found at ..\filesync
    pause
    exit /b 1
)

REM Step 2: Build ApiGuardWrapper
echo.
echo ============================================================
echo   Step 2: Building apiguardwrapper
echo ============================================================
cd /d "%SCRIPT_DIR%"
call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] ApiGuardWrapper build failed!
    pause
    exit /b 1
)

echo.
echo ============================================================
echo   BUILD SUCCESSFUL!
echo ============================================================
echo.
echo Wrapper JAR: %SCRIPT_DIR%target\apiguardwrapper-1.0.0-mule-application.jar
echo.
pause

