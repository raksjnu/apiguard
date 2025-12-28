@echo off
REM ===================================================================
REM ApiGuard Wrapper - Build Script with RaksAnalyzer Only
REM ===================================================================
REM This script:
REM 1. Builds and Installs RaksAnalyzer (Dependency)
REM 2. Builds the ApiGuardWrapper Mule application
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
echo   ApiGuard Wrapper - Build with RaksAnalyzer Only
echo ============================================================
echo.

REM Step 1: Build & Install RaksAnalyzer
echo [1/2] Building ^& Installing RaksAnalyzer...
echo ============================================================
if exist "%SCRIPT_DIR%..\raksanalyzer" (
    cd /d "%SCRIPT_DIR%..\raksanalyzer"
    call mvn clean install -DskipTests
    
    if errorlevel 1 (
        echo.
        echo [ERROR] RaksAnalyzer build failed!
        pause
        exit /b 1
    )
    
    REM Copy raksanalyzer JAR to apiguardwrapper/lib
    echo.
    echo [INFO] Copying raksanalyzer JAR to lib folder...
    cmd /c "if exist "%USERPROFILE%\.m2\repository\com\raks\raksanalyzer\1.0.0\raksanalyzer-1.0.0.jar" (xcopy /Y /Q "%USERPROFILE%\.m2\repository\com\raks\raksanalyzer\1.0.0\raksanalyzer-1.0.0.jar" "%SCRIPT_DIR%lib\" >nul 2>&1 && echo [INFO] raksanalyzer-1.0.0.jar copied successfully || echo [WARN] Failed to copy raksanalyzer JAR) else (echo [WARN] raksanalyzer JAR not found in .m2 repository)"
) else (
    echo [ERROR] RaksAnalyzer project not found at ..\raksanalyzer
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
