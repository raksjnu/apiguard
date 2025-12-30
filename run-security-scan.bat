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
for /r %%i in (pom.xml) do (
    set "POM_PATH=%%~dpi"
    set "POM_FILE=%%i"
    
    echo "Processing %%i" | findstr /i "\\target\\" >nul
    if errorlevel 1 (
        echo.
        echo --------------------------------------------------------
        echo Scanning Project: %%~dpi
        echo --------------------------------------------------------
        
        pushd "%%~dpi"
            
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
    ) else (
        echo [SKIP] Skipping target directory: %%~dpi
    )
)

echo.
echo ========================================================
echo       Audit Completed
echo ========================================================
pause
