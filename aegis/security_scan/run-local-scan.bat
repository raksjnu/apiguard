@echo off 
pushd .. 
set "NVD_API_KEY=2e1b33c4-166a-4698-bf6f-686cf046d8fe" 
mvn org.owasp:dependency-check-maven:12.1.0:check -Dformat=HTML -DoutputDirectory=security_scan -DautoUpdate=true -DnvdApiKey=%NVD_API_KEY% -DfailOnError=false -DossindexAnalyzerEnabled=false 
if exist "target\dependency-check-report.html" move /Y "target\dependency-check-report.html" "security_scan\" 
mvn org.cyclonedx:cyclonedx-maven-plugin:2.7.9:makeAggregateBom -DoutputDirectory=security_scan 
mvn org.codehaus.mojo:license-maven-plugin:2.0.0:add-third-party -Dlicense.useMissingFile -Dlicense.outputDirectory=security_scan 
mvn dependency:tree -DoutputFile=security_scan\dependency-tree.txt 
echo Scan Complete. 
popd 
