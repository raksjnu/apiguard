@echo off
REM ===================================================================
REM ApiGuard Wrapper - Complete Dependency Build Script
REM ===================================================================
REM This script:
REM 1. Builds and Installs RaksAnalyzer (Dependency)
REM 2. Builds and Installs MuleGuard (Dependency)
REM 3. Builds and Installs ApiDiscovery (Dependency)
REM 4. Builds the ApiGuardWrapper Mule application
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
echo   ApiGuard Wrapper - Full Dependency Build
echo ============================================================
echo.

REM Step 1: Build & Install RaksAnalyzer
echo [1/5] Building ^& Installing RaksAnalyzer...
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

REM Step 2: Build & Install MuleGuard
echo.
echo [2/5] Building ^& Installing MuleGuard...
echo ============================================================
if exist "%SCRIPT_DIR%..\muleguard" (
    cd /d "%SCRIPT_DIR%..\muleguard"
    call mvn clean install -DskipTests
    
    if errorlevel 1 (
        echo.
        echo [ERROR] MuleGuard build failed!
        pause
        exit /b 1
    )
    
    REM Copy muleguard JAR to apiguardwrapper/lib
    echo.
    echo [INFO] Copying muleguard JAR to lib folder...
    cmd /c "if exist "%USERPROFILE%\.m2\repository\com\raks\muleguard\1.0.0\muleguard-1.0.0.jar" (xcopy /Y /Q "%USERPROFILE%\.m2\repository\com\raks\muleguard\1.0.0\muleguard-1.0.0.jar" "%SCRIPT_DIR%lib\" >nul 2>&1 && echo [INFO] muleguard-1.0.0.jar copied successfully || echo [WARN] Failed to copy muleguard JAR) else (echo [WARN] muleguard JAR not found in .m2 repository)"
) else (
    echo [ERROR] MuleGuard project not found at ..\muleguard
    pause
    exit /b 1
)

REM Step 3: Build & Install ApiDiscovery
echo.
echo [3/5] Building ^& Installing ApiDiscovery...
echo ============================================================
if exist "%SCRIPT_DIR%..\apidiscovery" (
    cd /d "%SCRIPT_DIR%..\apidiscovery"
    call mvn clean install -DskipTests
    
    if errorlevel 1 (
        echo.
        echo [ERROR] ApiDiscovery build failed!
        pause
        exit /b 1
    )
    
    REM Copy apidiscovery JAR to apiguardwrapper/lib
    echo.
    echo [INFO] Copying apidiscovery JAR to lib folder...
    cmd /c "if exist "%USERPROFILE%\.m2\repository\com\raks\apidiscovery\1.0.0\apidiscovery-1.0.0.jar" (xcopy /Y /Q "%USERPROFILE%\.m2\repository\com\raks\apidiscovery\1.0.0\apidiscovery-1.0.0.jar" "%SCRIPT_DIR%lib\" >nul 2>&1 && echo [INFO] apidiscovery-1.0.0.jar copied successfully || echo [WARN] Failed to copy apidiscovery JAR) else (echo [WARN] apidiscovery JAR not found in .m2 repository)"
) else (
    echo [ERROR] ApiDiscovery project not found at ..\apidiscovery
    pause
    exit /b 1
)

REM Step 4: Build & Install ApiUrlComparison
echo.
echo [4/5] Building ^& Installing ApiUrlComparison...
echo ============================================================
if exist "%SCRIPT_DIR%..\apiurlcomparison" (
    cd /d "%SCRIPT_DIR%..\apiurlcomparison"
    call mvn clean install -DskipTests
    
    if errorlevel 1 (
        echo.
        echo [ERROR] ApiUrlComparison build failed!
        pause
        exit /b 1
    )
    
    REM Copy apiurlcomparison JAR to apiguardwrapper/lib
    echo.
    echo [INFO] Copying apiurlcomparison JAR to lib folder...
    cmd /c "if exist "%SCRIPT_DIR%..\apiurlcomparison\target\apiurlcomparison-1.0.0-jar-with-raks.jar" (copy /Y "%SCRIPT_DIR%..\apiurlcomparison\target\apiurlcomparison-1.0.0-jar-with-raks.jar" "%SCRIPT_DIR%lib\apiurlcomparison-1.0.0.jar" >nul && echo [INFO] apiurlcomparison JAR copied successfully || echo [WARN] Failed to copy apiurlcomparison JAR) else (echo [WARN] apiurlcomparison JAR not found in target)"
) else (
    echo [ERROR] ApiUrlComparison project not found at ..\apiurlcomparison
    pause
    exit /b 1
)

REM Step 5: Build ApiGuardWrapper
echo.
echo [5/5] Building ApiGuardWrapper...
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
