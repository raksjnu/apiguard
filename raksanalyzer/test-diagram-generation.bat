@echo off
REM Test script to verify diagram generation
setlocal enabledelayedexpansion

echo ============================================================
echo   Testing TIBCO Diagram Generation
echo ============================================================
echo.

set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "PATH=%JAVA_HOME%\bin;%PATH%"

REM Create a simple test Java program to call the diagram generator
set TEST_FILE=TestDiagramGen.java

echo Creating test program...
(
echo import com.raks.raksanalyzer.generator.tibco.TibcoDiagramGenerator;
echo import java.io.File;
echo.
echo public class TestDiagramGen {
echo     public static void main^(String[] args^) {
echo         try {
echo             TibcoDiagramGenerator gen = new TibcoDiagramGenerator^(^);
echo             
echo             // Test FILE process
echo             File fileProcess = new File^("testdata/customerOrder/Service/orderServiceFILE.process"^);
echo             String puml = gen.generateIntegrationPuml^(fileProcess, "orderServiceFILE", new File^("testdata"^)^);
echo             
echo             System.out.println^("=== Generated PlantUML for orderServiceFILE ===="^);
echo             System.out.println^(puml^);
echo             System.out.println^("=== End PlantUML ===="^);
echo             System.out.println^(^);
echo             
echo             // Check for expected connectors
echo             boolean hasFileEventSource = puml.contains^("FileEventSource"^);
echo             boolean hasFileCopy = puml.contains^("FileCopyActivity"^);
echo             boolean hasFileRename = puml.contains^("FileRenameActivity"^);
echo             boolean hasFileRemove = puml.contains^("FileRemoveActivity"^);
echo             boolean hasListFiles = puml.contains^("ListFilesActivity"^);
echo             boolean hasFileWrite = puml.contains^("FileWriteActivity"^);
echo             boolean hasCallProcess = puml.contains^("partition"^);
echo             
echo             System.out.println^("Verification Results:"^);
echo             System.out.println^("  FileEventSource: " + hasFileEventSource^);
echo             System.out.println^("  FileCopyActivity: " + hasFileCopy^);
echo             System.out.println^("  FileRenameActivity: " + hasFileRename^);
echo             System.out.println^("  FileRemoveActivity: " + hasFileRemove^);
echo             System.out.println^("  ListFilesActivity: " + hasListFiles^);
echo             System.out.println^("  FileWriteActivity: " + hasFileWrite^);
echo             System.out.println^("  CallProcess ^(partition^): " + hasCallProcess^);
echo             
echo             if ^(hasFileEventSource ^&^& hasFileCopy ^&^& hasFileRename ^&^& hasFileRemove 
echo                 ^&^& hasListFiles ^&^& hasFileWrite ^&^& hasCallProcess^) {
echo                 System.out.println^(^);
echo                 System.out.println^("SUCCESS: All expected connectors found!"^);
echo                 System.exit^(0^);
echo             } else {
echo                 System.out.println^(^);
echo                 System.out.println^("FAILURE: Some connectors missing!"^);
echo                 System.exit^(1^);
echo             }
echo         } catch ^(Exception e^) {
echo             e.printStackTrace^(^);
echo             System.exit^(1^);
echo         }
echo     }
echo }
) > %TEST_FILE%

echo Compiling test program...
"%JAVA_HOME%\bin\javac" -cp "target\raksanalyzer-1.0.0.jar" %TEST_FILE%
if errorlevel 1 (
    echo FAILED to compile test program
    del %TEST_FILE%
    exit /b 1
)

echo Running test...
"%JAVA_HOME%\bin\java" -cp ".;target\raksanalyzer-1.0.0.jar" TestDiagramGen

set RESULT=%ERRORLEVEL%

REM Cleanup
del %TEST_FILE%
del TestDiagramGen.class

echo.
if %RESULT%==0 (
    echo ============================================================
    echo   TEST PASSED
    echo ============================================================
) else (
    echo ============================================================
    echo   TEST FAILED
    echo ============================================================
)

exit /b %RESULT%
