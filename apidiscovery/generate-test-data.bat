@echo off
setlocal
set "BASE_DIR=C:\raks\apiguard\apidiscovery\test-data\simulated-enterprise"

echo [INFO] Cleaning up safe...
if exist "%BASE_DIR%" rmdir /s /q "%BASE_DIR%"
mkdir "%BASE_DIR%"

echo [INFO] Generating Enterprise Structure...

:: --- Function to create a Mule Project ---
goto :Start

:CreateMuleProject
set "GRP=%~1"
set "PROJ=%~2"
set "DIR=%BASE_DIR%\%GRP%\%PROJ%"
if not exist "%DIR%" mkdir "%DIR%"
mkdir "%DIR%\src\main\mule" 2>nul
mkdir "%DIR%\src\main\resources" 2>nul
echo ^<project^>^<modelVersion^>4.0.0^</modelVersion^>^<groupId^>com.raks^</groupId^>^<artifactId^>%PROJ%^</artifactId^>^<packaging^>mule-application^</packaging^>^<dependencies^>^<dependency^>^<groupId^>org.mule.connectors^</groupId^>^<artifactId^>mule-http-connector^</artifactId^>^</dependency^>^<dependency^>^<groupId^>com.mulesoft.modules^</groupId^>^<artifactId^>mule-secure-configuration-property-module^</artifactId^>^</dependency^>^</dependencies^>^<build^>^<plugins^>^<plugin^>^<groupId^>org.mule.tools.maven^</groupId^>^<artifactId^>mule-maven-plugin^</artifactId^>^</plugin^>^</plugins^>^</build^>^</project^> > "%DIR%\pom.xml"
echo { "minMuleVersion": "4.4.0" } > "%DIR%\mule-artifact.json"
echo ^<mule xmlns="http://www.mulesoft.org/schema/mule/core" xmlns:http="http://www.mulesoft.org/schema/mule/http"^>^<http:listener-config name="HTTP_Listener_config" basePath="/api/v1/%PROJ%"/^>^<flow name="main"^>^<http:listener config-ref="HTTP_Listener_config" path="/" /^>^</flow^>^</mule^> > "%DIR%\src\main\mule\api.xml"
echo # Properties > "%DIR%\src\main\resources\config.properties"
echo http.port=8081 >> "%DIR%\src\main\resources\config.properties"
echo secure.key=1234567812345678 >> "%DIR%\src\main\resources\config.properties"
echo # Auto-generated Mule Project > "%DIR%\README.md"
goto :eof

:CreateSpringProject
set "GRP=%~1"
set "PROJ=%~2"
set "DIR=%BASE_DIR%\%GRP%\%PROJ%"
if not exist "%DIR%" mkdir "%DIR%"
mkdir "%DIR%\src\main\java\com\raks" 2>nul
echo ^<project^>^<dependencies^>^<dependency^>^<groupId^>org.springframework.boot^</groupId^>^<artifactId^>spring-boot-starter-web^</artifactId^>^</dependency^>^<dependency^>^<groupId^>org.springdoc^</groupId^>^<artifactId^>springdoc-openapi-ui^</artifactId^>^</dependency^>^</dependencies^>^</project^> > "%DIR%\pom.xml"
echo @RestController public class HelloController { } > "%DIR%\src\main\java\com\raks\HelloController.java"
echo server.port=8080 > "%DIR%\application.properties"
echo # Spring Boot Service > "%DIR%\README.md"
goto :eof

:CreatePythonProject
set "GRP=%~1"
set "PROJ=%~2"
set "DIR=%BASE_DIR%\%GRP%\%PROJ%"
if not exist "%DIR%" mkdir "%DIR%"
echo flask==2.0.1 > "%DIR%\requirements.txt"
echo from flask import Flask > "%DIR%\app.py"
echo app = Flask(__name__) >> "%DIR%\app.py"
echo @app.route('/api/%PROJ%') >> "%DIR%\app.py"
echo def hello(): return "Hello" >> "%DIR%\app.py"
echo # Python Flask Service > "%DIR%\README.md"
goto :eof

:Start

:: --- GROUP 1: Finance (Mixed Secure APIs) ---
mkdir "%BASE_DIR%\raks-group-finance"
call :CreateMuleProject "raks-group-finance" "finance-payments-api"
call :CreateMuleProject "raks-group-finance" "finance-ledger-sapi"
call :CreateSpringProject "raks-group-finance" "finance-reporting-service"
call :CreatePythonProject "raks-group-finance" "finance-risk-model"

:: Add Specific Metadata for Finance
echo owner=FinTech Team >> "%BASE_DIR%\raks-group-finance\finance-payments-api\README.md"
echo endpoint=https://api.finance.raks.com/payments >> "%BASE_DIR%\raks-group-finance\finance-payments-api\README.md"
echo cert_cn=finance.raks.com >> "%BASE_DIR%\raks-group-finance\finance-payments-api\manual-metadata.txt"

:: --- GROUP 2: HR (Internal APIs) ---
mkdir "%BASE_DIR%\raks-group-hr"
call :CreateMuleProject "raks-group-hr" "hr-employee-papi"
call :CreateMuleProject "raks-group-hr" "hr-onboarding-sapi"
call :CreateSpringProject "raks-group-hr" "hr-payroll-batch"
call :CreateSpringProject "raks-group-hr" "hr-benefits-portal"

:: Add Specific Metadata for HR
echo owner=HR Connect >> "%BASE_DIR%\raks-group-hr\hr-employee-papi\README.md"
echo PII_Risk=High >> "%BASE_DIR%\raks-group-hr\hr-payroll-batch\README.md"

:: --- GROUP 3: Logistics (Legacy & Hybrid) ---
mkdir "%BASE_DIR%\raks-group-logistics"
call :CreateMuleProject "raks-group-logistics" "log-tracking-api"
call :CreatePythonProject "raks-group-logistics" "log-route-optimizer"
:: Create a Dummy TIBCO Project
mkdir "%BASE_DIR%\raks-group-logistics\log-legacy-bw"
echo # TIBCO BW 5.x > "%BASE_DIR%\raks-group-logistics\log-legacy-bw\bw.vcrepo"
echo transport_http > "%BASE_DIR%\raks-group-logistics\log-legacy-bw\service.archive"
mkdir "%BASE_DIR%\raks-group-logistics\log-legacy-bw\AESchema"

:: --- GROUP 4: External (Public Facing) ---
mkdir "%BASE_DIR%\raks-group-external"
call :CreateMuleProject "raks-group-external" "ext-partner-gateway"
call :CreateMuleProject "raks-group-external" "ext-mobile-bff"
echo endpoint=https://api.raks.com/partners >> "%BASE_DIR%\raks-group-external\ext-partner-gateway\README.md"

echo.
echo [SUCCESS] Simulated Enterprise Data Generated at: %BASE_DIR%
