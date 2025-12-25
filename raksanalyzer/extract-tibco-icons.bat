@echo off
REM Extract Tibco Activity Icons from Installation
REM This script extracts icons from Tibco BW 5.x JAR files

setlocal enabledelayedexpansion

echo ============================================================
echo  Tibco Icon Extraction Utility
echo ============================================================
echo.

REM Set paths
set TIBCO_HOME=C:\tibco\bw\5.x\lib\palettes
set OUTPUT_DIR=src\main\resources\images\tibco
set TEMP_EXTRACT=temp_icon_extract

REM Create output directory
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

echo [INFO] Tibco Installation: %TIBCO_HOME%
echo [INFO] Output Directory: %OUTPUT_DIR%
echo.

REM Check if Tibco installation exists
if not exist "%TIBCO_HOME%" (
    echo [ERROR] Tibco installation not found at: %TIBCO_HOME%
    echo [INFO] Please update TIBCO_HOME variable in this script
    pause
    exit /b 1
)

REM List of palette JARs to extract from
set JARS=bw_jdbc.jar bw_http.jar bw_jms.jar bw_file.jar bw_xml.jar bw_parse.jar

echo [INFO] Extracting icons from Tibco palette JARs...
echo.

for %%J in (%JARS%) do (
    if exist "%TIBCO_HOME%\%%J" (
        echo [INFO] Processing %%J...
        
        REM Create temp directory
        if not exist "%TEMP_EXTRACT%" mkdir "%TEMP_EXTRACT%"
        
        REM Extract JAR
        jar xf "%TIBCO_HOME%\%%J" -C "%TEMP_EXTRACT%"
        
        REM Copy icons (look for common icon folders)
        if exist "%TEMP_EXTRACT%\icons" (
            xcopy /Y /S "%TEMP_EXTRACT%\icons\*.png" "%OUTPUT_DIR%\" >nul 2>&1
            xcopy /Y /S "%TEMP_EXTRACT%\icons\*.gif" "%OUTPUT_DIR%\" >nul 2>&1
        )
        
        if exist "%TEMP_EXTRACT%\images" (
            xcopy /Y /S "%TEMP_EXTRACT%\images\*.png" "%OUTPUT_DIR%\" >nul 2>&1
            xcopy /Y /S "%TEMP_EXTRACT%\images\*.gif" "%OUTPUT_DIR%\" >nul 2>&1
        )
        
        REM Clean up temp
        rmdir /S /Q "%TEMP_EXTRACT%"
        
        echo [INFO] Completed %%J
    ) else (
        echo [WARN] JAR not found: %%J
    )
)

echo.
echo [INFO] Icon extraction complete!
echo [INFO] Icons saved to: %OUTPUT_DIR%
echo.

REM List extracted icons
echo [INFO] Extracted icons:
dir /B "%OUTPUT_DIR%\*.png" 2>nul
dir /B "%OUTPUT_DIR%\*.gif" 2>nul

echo.
echo ============================================================
pause
