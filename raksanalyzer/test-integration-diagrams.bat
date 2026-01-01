@echo off
setlocal enabledelayedexpansion

echo ========================================
echo Testing Integration Diagram Generation
echo ========================================
echo.

set PROJECT_ROOT=C:\raks\apiguard\raksanalyzer\testdata\TibcoApp1
set OUTPUT_DIR=C:\raks\apiguard\raksanalyzer\test-output

REM Create output directory
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

echo Testing 3 processes:
echo 1. orderServiceJMS.process (Service)
echo 2. orderServiceFILE.process (Service)  
echo 3. createOrderDummy.process (Business Process)
echo.

REM Test each process
call :test_process "Service\orderServiceJMS.process" "orderServiceJMS"
call :test_process "Service\orderServiceFILE.process" "orderServiceFILE"
call :test_process "BusinessProcess\createOrderDummy.process" "createOrderDummy"

echo.
echo ========================================
echo All tests complete!
echo Output files in: %OUTPUT_DIR%
echo ========================================
goto :eof

:test_process
set PROCESS_PATH=%~1
set PROCESS_NAME=%~2

echo.
echo Testing: %PROCESS_NAME%
echo -----------------------------------------

REM Generate the integration diagram PlantUML
java -cp "target\raksanalyzer-1.0.0.jar" com.raks.raksanalyzer.generator.tibco.TibcoDiagramGenerator "%PROJECT_ROOT%\%PROCESS_PATH%" "%PROJECT_ROOT%" > "%OUTPUT_DIR%\%PROCESS_NAME%_integration.puml" 2>&1

if errorlevel 1 (
    echo ERROR: Failed to generate diagram for %PROCESS_NAME%
) else (
    echo SUCCESS: Generated %PROCESS_NAME%_integration.puml
    
    REM Extract just Section 2 (Integration Diagram)
    findstr /B /C:"@startuml" /C:"skinparam" /C:"partition" /C:":" /C:"fork" /C:"end" /C:"@enduml" "%OUTPUT_DIR%\%PROCESS_NAME%_integration.puml" > "%OUTPUT_DIR%\%PROCESS_NAME%_section2_only.puml"
    
    echo Extracted Section 2 to %PROCESS_NAME%_section2_only.puml
)

goto :eof
