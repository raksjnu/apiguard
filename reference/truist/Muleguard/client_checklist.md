# Client Checklist Items

| ID | Description | Mule Version/Groups | File Name/Type |
|----|-------------|---------------------|----------------|
| 1 | `mule-artifact.json` file must have following and ONLY three elements (in any sequence or order):<br>`"minMuleVersion": "4.9.7"`<br>`"javaSpecificationVersions": ["17"]`<br>`"secureProperties":` | All | `mule-artifact.json` |
| 2 | Remove `toApplicationCode` from all logger (.xml files) example - `toApplicationCode="#['OAC']"` / `toApplicationCode="#[vars.fromAppCode]"` | All | `*.xml` |
| 3 | Remove `fromApplicationCode` from all logger (.xml files) example - `fromApplicationCode="OAC"` / `fromApplicationCode="#['OAC']"` | 4.3 | `*.xml` |
| 4 | Scan codebase for any other occurrence of "jce-encrypt" and mark FAIL if found. | All | `*.xml` |
| 5 | `soapVersion` attribute from `apikit-soap:config` element should be changed to `SOAP_11` or `SOAP_12` based on initial version. | All | `<apiname>-config.xml` |
| 6 | `REQUEST_OUT` and `RESPONSE_IN` should be WARN in header injection policy as below:<br>`headerinjection.policy.inboundheadermap.x-tfc-request-out=WARN`<br>`headerinjection.policy.inboundheadermap.x-tfc-response-in=WARN` | All | all policy files |
| 7 | For EAPIGW proxy, if `Truist-mTLS-AuthZ` policy is present than only version upgrade required to 3.0.0 | EAPIGW | all policy files |
| 8 | If `Truist-AAA-SOAP` or `Truist-AAA-HTTP` or `TruistBasicAuthnPolicy` policy is present than it should be changed to `TruistCompositeAuthnPolicy` and `TruistAuthzPolicy`, both with version 3.0.0 | All | all policy files |
| 9 | `TruistCompositeAuthnPolicy` version be 3.0.0 | All | all policy files |
| 10 | `TruistAuthzPolicy` policy version be 3.0.0 | All | all policy files |
| 11 | `noname` policy version should be 5.1.0 | All | all policy files |
| 12 | `ratelimit` policy version should be 1.4.1 | All | all policy files |
| 13 | `headerinjection` policy version should be 1.3.2 | All | all policy files |
| 14 | `headerremoval` policy version should be 1.1.2 | All | all policy files |
| 15 | Change DB URLs to DVIPA URLs | All | all property file |
| 16 | There should be no other property change apart from mentioned property in this sheet | All | all property file |
| 17 | If any manual change than update dev tracker | All | dev tracker xls |
| 18 | Refer error strings tab and ensure no old runtime error strings found in upgraded code. All error strings should be updated as per 4.9 runtime | All | `error-handling.xml` |
| 19 | Removal of DLP code in case of EAPIGW proxies. Code block:<br>```xml<br><choice doc:name="Choice" doc:id="[ID]"><br>    <when expression="#[upper(p('north.bound.dlp.flag')) == 'ON']"><br>        <try doc:name="Try" doc:id="[ID]"><br>            <ibm-mq:publish ... destination="#[p('wmq.dlp.requestqueue')]" ...><br>            ...<br>            </ibm-mq:publish><br>            <error-handler>...<br>            </error-handler><br>        </try><br>    </when><br></choice>``` | EAPIGW | `http-proxy.xml` |
| 20 | Removal of DLP properties in case of EAPIGW proxies<br>`north.bound.dlp.flag`<br>`south.bound.dlp.flag`<br>`wmq.dlp.requestqueue` | EAPIGW | `mule-app.properties` |
| 21 | If API is using sql DB then, explicitly add following property to the DB url (if its not there already) in all env properties file. - `;encrypt=false;trustServerCertificate=false` | 4.3 | `mule-app.properties` |
| 22 | Check for cipher suite for MQ connector, correct value would be:<br>`TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384` | All | `mule-app.properties`, all properties files |
| 23 | Parent Pom added in pom.xml:<br>```xml<br><parent><br><groupId>com.truist.eapi</groupId><br><artifactId>MuleParentPom</artifactId><br><version>3.0.0</version><br></parent>``` | All | `pom.xml` |
| 24 | Update properties tag in pom.xml as below:<br>`<muleMavenPluginVersion>4.6.0</muleMavenPluginVersion>` (or `mule.maven.plugin.version`)<br>`<app.runtime>4.9.LTS</app.runtime>`<br>`<cicd.mule.version>4.9.LTS</cicd.mule.version>` | All | `pom.xml` |
| 25 | `<munit.version>3.5.0</munit.version>` if present | All | `pom.xml` |
| 26 | Remove below plugins if present:<br>1. `maven-clean-plugin` (version 3.0.0)<br>2. `maven-compiler-plugin` (version 3.10.1)<br>3. `munit-maven-plugin` | All | `pom.xml` |
| 27 | Remove version from all dependencies, mention dependency name and reason if version can't be removed.<br>Removal of runtime dependency:<br>`mule-core-ee` (4.3.0)<br>`mule-module-spring-config-ee` (4.3.0) | All | `pom.xml` |
| 28 | Remove below 2 shared library (if any is present):<br>`spring-security-ldap`<br>`spring-security-web` | All | `pom.xml` |
| 29 | Addition of following dependency:<br>```xml<br><dependency><br><groupId>com.truist.eapi.crypto</groupId><br><artifactId>eapimuleutilities</artifactId><br></dependency>``` | All | `pom.xml` |
| 30 | Verify dependency tab if any of mentioned dependency present in API. If yes, then update as per "new dependency column" | All | `pom.xml` |
| 31 | Call to `jce-encrypt-pbe` (doc:name="Encrypt Request Path") should be replaced with `EncUtil::encryptJcePbe` DataWeave expression.<br>**Old:** `<crypto:jce-encrypt-pbe .../>`<br>**New:** `<set-variable ... value="#[%dw 2.0 import java!com::truist::eapi::lib::EncUtil ... EncUtil::encryptJcePbe(...) ...]" .../>` | All | `setlogvariable subflow` |
| 32 | Update `apimethod` variable as it also has encrypt method call. It should refer to above variable.<br>`attributes.method ++ ':' ++ vars.encryptedRequestPath default ""` | All | `setlogvariable subflow` |
| 33 | Verify splunk logs, there should not be logging related error | All | testing |
| 35 | `TruistWSSEncDecPolicy` policy version should be 3.0.0 | All | all policy files |
| 36 | `auto discovery` should be uncommented:<br>`<api-gateway:autodiscovery` | All | `<apiname>-config.xml`, `http-proxy.xml` |
| 37 | `TruistJSONEncDecPolicy` policy version should be 3.0.0 | All | all policy files |
| 38 | Check if `?wsdl` is working. If it was working before change than it should work after change also.<br>For `Truist-mTLS-AuthZ` (1.1.1) Rest serviceType. | All | testing |
