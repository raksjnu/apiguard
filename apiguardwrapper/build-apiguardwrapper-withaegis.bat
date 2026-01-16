@echo off
REM ===================================================================
REM ApiGuard Wrapper - Build Script with Aegis Only
REM ===================================================================
REM This script:
REM 1. Builds and Installs Aegis (Dependency)
REM 2. Copies Aegis Web Resources to Wrapper
REM 3. Builds the ApiGuardWrapper Mule application
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
echo   ApiGuard Wrapper - Build with Aegis Only
echo ============================================================
echo.

REM Step 1: Build & Install Aegis
echo [1/2] Building ^& Installing Aegis...
echo ============================================================
if exist "%SCRIPT_DIR%..\aegis" (
    cd /d "%SCRIPT_DIR%..\aegis"
    call mvn clean install -DskipTests
    
    if errorlevel 1 (
        echo.
        echo [ERROR] Aegis build failed!
        pause
        exit /b 1
    )
    
    REM Copy aegis JAR to apiguardwrapper/lib
    echo.
    echo [INFO] Copying aegis FAT JAR to lib folder...
    if exist "%SCRIPT_DIR%..\aegis\target\aegis-1.0.0-jar-with-raks.jar" (
        xcopy /Y /Q "%SCRIPT_DIR%..\aegis\target\aegis-1.0.0-jar-with-raks.jar" "%SCRIPT_DIR%lib\aegis-1.0.0.jar*" >nul 2>&1
        echo [INFO] aegis fat JAR copied successfully to lib\aegis-1.0.0.jar
    ) else (
        echo [WARN] aegis fat JAR not found in target folder!
        if exist "%USERPROFILE%\.m2\repository\com\raks\aegis\1.0.0\aegis-1.0.0.jar" (
            xcopy /Y /Q "%USERPROFILE%\.m2\repository\com\raks\aegis\1.0.0\aegis-1.0.0.jar" "%SCRIPT_DIR%lib\" >nul 2>&1
            echo [INFO] Fallback: Standard aegis-1.0.0.jar copied from .m2
        ) else (
            echo [ERROR] No aegis JAR found anywhere.
            pause
            exit /b 1
        )
    )

    REM Copy Web Resources
    echo.
    echo [INFO] Copying Aegis web resources...
    if exist "%SCRIPT_DIR%..\aegis\src\main\resources\web\aegis" (
        xcopy /E /Y /I /Q "%SCRIPT_DIR%..\aegis\src\main\resources\web\aegis" "%SCRIPT_DIR%src\main\resources\web\aegis" >nul 2>&1
        echo [INFO] Web resources copied.
    )

) else (
    echo [ERROR] Aegis project not found at ..\aegis
    pause
    exit /b 1
)

REM Step 2: Build ApiGuardWrapper
echo.
echo [2/2] Building ApiGuardWrapper...
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
