@echo off
REM ===================================================================
REM ApiGuard Wrapper - Build Script with GitAnalyzer
REM ===================================================================
REM This script:
REM 1. Builds and Installs GitAnalyzer (Dependency)
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
echo   ApiGuard Wrapper - Build with GitAnalyzer
echo ============================================================
echo.

REM Step 1: Build & Install GitAnalyzer
echo [1/2] Building ^& Installing GitAnalyzer...
echo ============================================================
if exist "%SCRIPT_DIR%..\gitanalyzer" (
    cd /d "%SCRIPT_DIR%..\gitanalyzer"
    call mvn clean install -DskipTests
    
    if errorlevel 1 (
        echo.
        echo [ERROR] GitAnalyzer build failed!
        pause
        exit /b 1
    )
    
    REM Copy gitanalyzer JAR to apiguardwrapper/lib
    echo.
    echo [INFO] Copying gitanalyzer JAR to lib folder...
    REM Try copying from target first as it is reliable for shaded/non-shaded logic in this context
    REM But following pattern uses .m2. Let's use target if available for simplicity or specific logic
    cmd /c "if exist "%SCRIPT_DIR%..\gitanalyzer\target\gitanalyzer-1.0.0.jar" (copy /Y "%SCRIPT_DIR%..\gitanalyzer\target\gitanalyzer-1.0.0.jar" "%SCRIPT_DIR%lib\gitanalyzer-1.0.0.jar" >nul && echo [INFO] gitanalyzer-1.0.0.jar copied successfully || echo [WARN] Failed to copy gitanalyzer JAR) else (echo [WARN] gitanalyzer JAR not found in target)"
) else (
    echo [ERROR] GitAnalyzer project not found at ..\gitanalyzer
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
