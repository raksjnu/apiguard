@echo off
REM Security Vulnerability Scan Script for Windows
REM Run this script to generate security reports for enterprise review

echo ======================================
echo API Comparison Tool - Security Scan
echo ======================================
echo.

echo.

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

REM Check if Maven is available
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Maven is not installed or not in PATH
    exit /b 1
)

echo 1. Running OWASP Dependency Check...
echo    This will scan all dependencies for known vulnerabilities (CVEs)
echo.
call mvn org.owasp:dependency-check-maven:8.4.0:check -DfailBuildOnCVSS=7

if %ERRORLEVEL% EQU 0 (
    echo [32m✓[0m OWASP Dependency Check completed
    echo    Report: target\dependency-check-report.html
) else (
    echo [33m![0m OWASP Dependency Check found vulnerabilities or failed
)
echo.

echo 2. Generating Software Bill of Materials (SBOM)...
echo    This creates a CycloneDX SBOM for supply chain security
echo.
call mvn org.cyclonedx:cyclonedx-maven-plugin:2.7.9:makeAggregateBom

if %ERRORLEVEL% EQU 0 (
    echo [32m✓[0m SBOM generated
    echo    Report: target\bom.xml
) else (
    echo [33m![0m SBOM generation failed
)
echo.

echo 3. Generating License Report...
echo    This lists all third-party licenses for compliance review
echo.
call mvn license:add-third-party

if %ERRORLEVEL% EQU 0 (
    echo [32m✓[0m License report generated
    echo    Report: target\generated-sources\license\THIRD-PARTY.txt
) else (
    echo [33m![0m License report generation failed
)
echo.

echo 4. Generating Dependency Tree...
echo    This shows the complete dependency hierarchy
echo.
call mvn dependency:tree -DoutputFile=target\dependency-tree.txt

if %ERRORLEVEL% EQU 0 (
    echo [32m✓[0m Dependency tree generated
    echo    Report: target\dependency-tree.txt
) else (
    echo [33m![0m Dependency tree generation failed
)
echo.

echo ======================================
echo Security Scan Complete
echo ======================================
echo.
echo Generated Reports:
echo   1. target\dependency-check-report.html - Vulnerability scan
echo   2. target\bom.xml - Software Bill of Materials (SBOM)
echo   3. target\generated-sources\license\THIRD-PARTY.txt - License report
echo   4. target\dependency-tree.txt - Dependency tree
echo.
echo Please review these reports and share with your security team.
echo.
pause
