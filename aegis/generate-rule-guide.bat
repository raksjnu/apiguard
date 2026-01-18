@echo off
REM ===================================================================
REM Generate Rule Guide HTML from Markdown Documentation
REM ===================================================================
REM This script generates rule_guide.html during the build process
REM ===================================================================

echo [INFO] Generating rule_guide.html...

cd /d "%~dp0"

REM Run the generation utility
mvn exec:java -Dexec.mainClass="com.raks.aegis.util.GenerateRuleGuide" -Dexec.args="temp" -q

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to generate rule_guide.html
    exit /b 1
)

REM Also copy to src/main/resources/web/aegis if it was generated
if exist "temp\rule_guide.html" (
    copy /Y "temp\rule_guide.html" "src\main\resources\web\aegis\rule_guide.html" >nul
    echo [INFO] rule_guide.html generated and copied to resources.
) else (
    echo [WARN] rule_guide.html was not generated in temp folder.
)

echo [INFO] Rule guide generation complete.
