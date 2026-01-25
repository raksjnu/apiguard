@echo off
REM ===================================================================
REM ApiGuard Wrapper - Simple Build Script with GitAnalyzer
REM ===================================================================
REM This script:
REM 1. Builds and Installs GitAnalyzer JAR (includes web assets)
REM 2. Copies JAR to lib folder
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
    if exist "%SCRIPT_DIR%..\gitanalyzer\target\gitanalyzer-1.0.0.jar" (
        copy /Y "%SCRIPT_DIR%..\gitanalyzer\target\gitanalyzer-1.0.0.jar" "%SCRIPT_DIR%lib\gitanalyzer-1.0.0.jar" >nul
        if errorlevel 1 (
            echo [ERROR] Failed to copy gitanalyzer JAR
            pause
            exit /b 1
        )
        echo [INFO] gitanalyzer-1.0.0.jar copied successfully
    ) else (
        echo [ERROR] gitanalyzer JAR not found in target
        pause
        exit /b 1
    )

    REM Step 1.5: Sync Web Assets to Wrapper
    echo [1.5/2] Syncing GitAnalyzer Web Assets...
    echo ============================================================
    
    if not exist "%SCRIPT_DIR%..\gitanalyzer\src\main\resources\web" (
        echo [ERROR] GitAnalyzer web resources not found at: "%SCRIPT_DIR%..\gitanalyzer\src\main\resources\web"
        pause
        exit /b 1
    )

    if not exist "%SCRIPT_DIR%src\main\resources\web\gitanalyzer" (
        mkdir "%SCRIPT_DIR%src\main\resources\web\gitanalyzer"
    )
    
    echo [INFO] Syncing files...
    robocopy "%SCRIPT_DIR%..\gitanalyzer\src\main\resources\web" "%SCRIPT_DIR%src\main\resources\web\gitanalyzer" /MIR /V /NP
    
    REM Robocopy exit codes: 
    REM 0=No changes, 1=Files copied, 2-7=Success with minor differences
    REM GEQ 8 = Fatal Error
    if %ERRORLEVEL% GEQ 8 (
        echo.
        echo [ERROR] Robocopy failed with Exit Code: %ERRORLEVEL%
        pause
        exit /b 1
    )
    echo [INFO] Web assets synchronized successfully.

) else (
    echo [ERROR] GitAnalyzer project not found at ..\\gitanalyzer
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
