@echo off
cd /d "%~dp0"
echo Running VerifyDiagramsTest...
call mvn test -Dtest=VerifyDiagramsTest -Dsurefire.useFile=false > test_output.txt 2>&1
echo Done.
