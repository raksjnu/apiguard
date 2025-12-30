@echo off
setlocal

echo ========================================================
echo       Starting Security ^& License Audit
echo ========================================================

REM Check for JAVA_HOME
if "%JAVA_HOME%"=="" (
    echo [ERROR] JAVA_HOME is not set. Please set it to a JDK 17+ installation.
    exit /b 1
)

echo [INFO] Using Java at: %JAVA_HOME%

REM Recursively find all pom.xml files
echo [INFO] Searching for Maven projects...
for /r %%i in (pom.xml) do call :scan_project "%%i"

goto :end

:scan_project
    set "POM_FILE=%~1"
    set "POM_PATH=%~dp1"
    
    echo [DEBUG] Found POM file: "%POM_FILE%"
    echo [DEBUG] Path: "%POM_PATH%"
    
    echo Processing "%POM_FILE%" | findstr /i "\\target\\" >nul
    if not errorlevel 1 (
        echo [DEBUG] Target directory detected.
        goto :skip_target
    )
    
    echo.
    echo --------------------------------------------------------
    echo Scanning Project: "%POM_PATH%"
    echo --------------------------------------------------------
    
    pushd "%POM_PATH%"
        
    echo [1/3] listing Dependencies...
    call mvn dependency:list -DoutputFile=target/dependency-list.txt -Dsort=true
    
    echo [2/3] Generating License Report...
    call mvn org.codehaus.mojo:license-maven-plugin:2.0.0:aggregate-add-third-party -Dlicense.useMissingFile -Dlicense.outputDirectory=target/site
    
    echo [3/3] Checking for CVEs (OWASP Dependency Check)...
    REM Note: First run will download huge CVE database.
    call mvn org.owasp:dependency-check-maven:8.4.3:check -Dformat=HTML -DautoUpdate=true
    
    echo.
    echo [REPORT] Reports generated in target/
    echo   - Dependencies: target/dependency-list.txt
    echo   - Licenses:     target/site/generated-sources/license/THIRD-PARTY.txt
    echo   - CVEs:         target/dependency-check-report.html
    
    popd
    exit /b

:skip_target
    echo [SKIP] Skipping target directory: "%POM_PATH%"
    exit /b

:end

echo.
echo ========================================================
echo       Audit Completed
echo ========================================================
pause
