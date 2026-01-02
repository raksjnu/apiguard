@echo off

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

echo Starting Mock API Server...
echo.
echo Mock servers will start on the following ports:
echo   - REST API 1: http://localhost:9091
echo   - REST API 2: http://localhost:9092
echo   - SOAP API 1: http://localhost:9093
echo   - SOAP API 2: http://localhost:9094
echo.
echo Press Ctrl+C to stop the servers.
echo.

call mvn compile exec:java -Dexec.mainClass="com.raks.apiurlcomparison.MockApiServer"

pause
