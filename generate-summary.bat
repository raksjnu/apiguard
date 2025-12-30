@echo off
setlocal enabledelayedexpansion
REM Create a consolidated summary report
set REPORT_FILE=Security_Scan_Summary.txt
echo ======================================================== > %REPORT_FILE%
echo       SECURITY SCAN SUMMARY REPORT >> %REPORT_FILE%
echo       Generated: %DATE% %TIME% >> %REPORT_FILE%
echo ======================================================== >> %REPORT_FILE%
echo. >> %REPORT_FILE%

echo [INFO] Generating Summary Report...

REM Find all generated reports
for /r %%f in (dependency-check-report.html) do (
    set "REPORT_PATH=%%f"
    echo Project Report Found: >> %REPORT_FILE%
    echo   Path: %%f >> %REPORT_FILE%
    echo. >> %REPORT_FILE%
)

echo. >> %REPORT_FILE%
echo ======================================================== >> %REPORT_FILE%
echo End of Summary >> %REPORT_FILE%

echo.
echo [INFO] Summary Report created at: %CD%\%REPORT_FILE%
