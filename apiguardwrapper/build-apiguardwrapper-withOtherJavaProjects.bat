@echo off
REM ===================================================================
REM ApiGuard Wrapper - Complete Dependency Build Script
REM ===================================================================
REM This script:
REM 1. Builds and Installs RaksAnalyzer (Dependency)
REM 2. Builds and Installs ApiDiscovery (Dependency)
REM 3. Builds and Installs API Forge (Dependency)
REM 4. Builds and Installs GitAnalyzer (Dependency)
REM 5. Builds and Installs Aegis (Dependency)
REM 6. Builds the ApiGuardWrapper Mule application
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
echo [1/6] Building ^& Installing RaksAnalyzer...
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
    if exist "%SCRIPT_DIR%..\raksanalyzer\target\raksanalyzer-1.0.0.jar" (
        copy /Y "%SCRIPT_DIR%..\raksanalyzer\target\raksanalyzer-1.0.0.jar" "%SCRIPT_DIR%lib\" >nul
        echo [INFO] raksanalyzer JAR copied successfully
    ) else (
        echo [WARN] raksanalyzer JAR not found
    )
) else (
    echo [ERROR] RaksAnalyzer project not found at ..\raksanalyzer
    pause
    exit /b 1
)

REM Step 2: Build & Install ApiDiscovery
echo.
echo [2/6] Building ^& Installing ApiDiscovery...
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
    if exist "%SCRIPT_DIR%..\apidiscovery\target\apidiscovery-1.0.0.jar" (
        copy /Y "%SCRIPT_DIR%..\apidiscovery\target\apidiscovery-1.0.0.jar" "%SCRIPT_DIR%lib\" >nul
        echo [INFO] apidiscovery JAR copied successfully
    ) else (
        echo [WARN] apidiscovery JAR not found
    )
) else (
    echo [ERROR] ApiDiscovery project not found at ..\apidiscovery
    pause
    exit /b 1
)

REM Step 3: Build & Install API Forge
echo.
echo [3/6] Building ^& Installing API Forge...
echo ============================================================
if exist "%SCRIPT_DIR%..\apiforge" (
    cd /d "%SCRIPT_DIR%..\apiforge"
    call mvn clean install -DskipTests
    
    if errorlevel 1 (
        echo.
        echo [ERROR] API Forge build failed!
        pause
        exit /b 1
    )
    
    REM Copy apiforge JAR to apiguardwrapper/lib
    echo.
    echo [INFO] Copying apiforge JAR to lib folder...
    if exist "%SCRIPT_DIR%..\apiforge\target\apiforge-1.0.0.jar" (
        copy /Y "%SCRIPT_DIR%..\apiforge\target\apiforge-1.0.0.jar" "%SCRIPT_DIR%lib\" >nul
        echo [INFO] apiforge JAR copied successfully
    ) else (
        echo [WARN] apiforge JAR not found
    )

    REM Sync Web Resources to apiguardwrapper
    echo.
    echo [INFO] Synchronizing API Forge Web Resources...
    if not exist "%SCRIPT_DIR%src\main\resources\web\apiforge" mkdir "%SCRIPT_DIR%src\main\resources\web\apiforge"
    
    if exist "%SCRIPT_DIR%..\apiforge\src\main\resources\public" (
        xcopy /Y /S /E "%SCRIPT_DIR%..\apiforge\src\main\resources\public\*" "%SCRIPT_DIR%src\main\resources\web\apiforge\" >nul
    )
    
    REM Sync Config and Test Data
    copy /Y "%SCRIPT_DIR%..\apiforge\src\main\resources\config.yaml" "%SCRIPT_DIR%src\main\resources\web\apiforge\" >nul
    if not exist "%SCRIPT_DIR%src\main\resources\web\apiforge\testData" mkdir "%SCRIPT_DIR%src\main\resources\web\apiforge\testData"
    if exist "%SCRIPT_DIR%..\apiforge\src\main\resources\testData\*" (
        xcopy /Y /S /E "%SCRIPT_DIR%..\apiforge\src\main\resources\testData\*" "%SCRIPT_DIR%src\main\resources\web\apiforge\testData\" >nul
    )

) else (
    echo [ERROR] API Forge project not found at ..\apiforge
    pause
    exit /b 1
)

REM Step 4: Build & Install GitAnalyzer
echo.
echo [4/6] Building ^& Installing GitAnalyzer...
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
        copy /Y "%SCRIPT_DIR%..\gitanalyzer\target\gitanalyzer-1.0.0.jar" "%SCRIPT_DIR%lib\" >nul
        echo [INFO] gitanalyzer JAR copied successfully
    ) else (
        echo [WARN] gitanalyzer JAR not found
    )
) else (
    echo [ERROR] GitAnalyzer project not found at ..\gitanalyzer
    pause
    exit /b 1
)

REM Step 5: Build & Install Aegis
echo.
echo [5/6] Building ^& Installing Aegis...
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
    echo [INFO] Copying aegis JAR to lib folder...
    if exist "%SCRIPT_DIR%..\aegis\target\aegis-1.0.0.jar" (
        copy /Y "%SCRIPT_DIR%..\aegis\target\aegis-1.0.0.jar" "%SCRIPT_DIR%lib\" >nul
        echo [INFO] aegis JAR copied successfully
    ) else (
        echo [WARN] aegis JAR not found
    )

    REM Copy Web Resources
    if exist "%SCRIPT_DIR%..\aegis\src\main\resources\web\aegis" (
        if not exist "%SCRIPT_DIR%src\main\resources\web\aegis" mkdir "%SCRIPT_DIR%src\main\resources\web\aegis"
        xcopy /E /Y /I /Q "%SCRIPT_DIR%..\aegis\src\main\resources\web\aegis" "%SCRIPT_DIR%src\main\resources\web\aegis" >nul 2>&1
        echo [INFO] Aegis Web Resources synchronized successfully.
    )
) else (
    echo [ERROR] Aegis project not found at ..\aegis
    pause
    exit /b 1
)

REM Step 6: Build ApiGuardWrapper
echo.
echo [6/6] Building ApiGuardWrapper...
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
