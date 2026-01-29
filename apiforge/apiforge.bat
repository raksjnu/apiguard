@echo off
REM ====================================================
REM ApiForge Launcher (Windows)
REM ====================================================

REM --- Architecture Configuration ---
REM Default Heap Size (Edit this for larger baselines)
set HEAP_SIZE=2G

REM check for Java
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java is not instaled or not in system PATH.
    echo Please install Java 17+ and try again.
    pause
    exit /b 1
)

echo [INFO] Starting ApiForge with %HEAP_SIZE% Heap Memory...
echo [INFO] Launching GUI...

start "ApiForge" java -Xmx%HEAP_SIZE% -jar apiforge-1.0.0.jar --gui

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to start ApiForge.
    pause
)
