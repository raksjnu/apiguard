@echo off
REM RaksAnalyzer Startup Script
REM Uses Java 17 from the standard installation path

set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Starting RaksAnalyzer with Java 17...
java -version
echo.

java -jar target\raksanalyzer-1.0.0.jar

pause
