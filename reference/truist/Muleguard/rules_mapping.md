# Rule Mapping Analysis

This document maps each item from the `client_checklist.md` to a specific technical implementation strategy for MuleGuard.

## Legend
- **XPath**: XML structure validation.
- **Regex**: Text or pattern matching in files.
- **Existence**: checking for file presence or absence.
- **Manual**: Cannot be fully automated, requires human review.

## Mapping Table

| ID | Description | Rule Type | Target | Logic / XPath / Pattern | Severity |
|----|-------------|-----------|--------|-------------------------|----------|
| 1 | `mule-artifact.json` specific elements | JSON/Regex | `mule-artifact.json` | Check for `minMuleVersion`, `javaSpecificationVersions`, `secureProperties` only. | FAIL |
| 2 | Remove `toApplicationCode` in Loggers | XPath/Regex | `*.xml` | `//logger/@toApplicationCode` should NOT exist. | FAIL |
| 3 | Remove `fromApplicationCode` in Loggers | XPath/Regex | `*.xml` | `//logger/@fromApplicationCode` should NOT exist. | FAIL |
| 4 | No `jce-encrypt` | Regex | `*.xml` | Pattern `jce-encrypt` should NOT be found. | FAIL |
| 5 | `soapVersion` in `apikit-soap:config` | XPath | `*-config.xml` | `//apikit-soap:config/@soapVersion` MUST be `SOAP_11` OR `SOAP_12`. | FAIL |
| 6 | Header Injection WARN | Regex | `*.yaml` (policies) | `headerinjection.policy.inboundheadermap.x-tfc-request-out=WARN`<br>`headerinjection.policy.inboundheadermap.x-tfc-response-in=WARN` | WARN |
| 7 | `Truist-mTLS-AuthZ` version 3.0.0 (EAPIGW only) | XPath/Regex | `*.xml`/Policies | If config/dependency exists, version must be `3.0.0`. | FAIL |
| 8 | Replace legacy Auth policies | XPath | `*.xml` | `Truist-AAA-SOAP`, `Truist-AAA-HTTP`, `TruistBasicAuthnPolicy` should NOT exist. | FAIL |
| 9 | `TruistCompositeAuthnPolicy` 3.0.0 | Regex/XPath | Policy Files | Version check: `3.0.0` | FAIL |
| 10 | `TruistAuthzPolicy` 3.0.0 | Regex/XPath | Policy Files | Version check: `3.0.0` | FAIL |
| 11 | `noname` policy 5.1.0 | Regex/XPath | Policy Files | Version check: `5.1.0` | FAIL |
| 12 | `ratelimit` policy 1.4.1 | Regex/XPath | Policy Files | Version check: `1.4.1` | FAIL |
| 13 | `headerinjection` 1.3.2 | Regex/XPath | Policy Files | Version check: `1.3.2` | FAIL |
| 14 | `headerremoval` 1.1.2 | Regex/XPath | Policy Files | Version check: `1.1.2` | FAIL |
| 15 | DB URLs to DVIPA | Manual/Regex | `*.properties` | *Hard to automate specific DVIPA format without regex pattern from user.* Flagging all DB URLs for review? | INFO |
| 16 | No other property changes | Manual | `*.properties` | Comparison required. | INFO |
| 17 | Manual change dev tracker | Manual | N/A | Process check. | INFO |
| 18 | `error-handling.xml` strings | Manual/Regex | `error-handling.xml` | *Need list of "old" strings to automate.* | INFO |
| 19 | Remove DLP code (EAPIGW) | XPath | `http-proxy.xml` | XPath `//choice/when[contains(@expression, 'north.bound.dlp.flag')]` should NOT exist. | FAIL |
| 20 | Remove DLP properties (EAPIGW) | Regex | `mule-app.properties` | `north.bound.dlp.flag`, `south...`, `wmq...` should NOT exist. | FAIL |
| 21 | SQL DB URL Properties | Regex | `mule-app.properties` | If `jdbc:sqlserver` present, check for `;encrypt=false;trustServerCertificate=false`. | FAIL |
| 22 | MQ Cipher Suite | Regex | `*.properties` | `TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384` required if MQ used. | FAIL |
| 23 | Parent POM | XPath | `pom.xml` | `//project/parent[groupId='com.truist.eapi' and artifactId='MuleParentPom' and version='3.0.0']` MUST exist. | FAIL |
| 24 | POM Properties Update | XPath | `pom.xml` | `//properties/muleMavenPluginVersion` = `4.6.0`<br>`//properties/app.runtime` = `4.9.LTS`<br>`//properties/cicd.mule.version` = `4.9.LTS` | FAIL |
| 25 | MUnit Version | XPath | `pom.xml` | If `//properties/munit.version` exists, it MUST be `3.5.0`. | FAIL |
| 26 | Ban specific plugins | XPath | `pom.xml` | `maven-clean-plugin`, `maven-compiler-plugin`, `munit-maven-plugin` should NOT be explicitly defined in `<plugins>` (unless overridden correctly? User said "Remove"). | FAIL |
| 27 | Remove runtime dependencies | XPath | `pom.xml` | `mule-core-ee` and `mule-module-spring-config-ee` should NOT be in `<dependencies>`. | FAIL |
| 28 | Remove shared libraries | XPath | `pom.xml` | `spring-security-ldap` and `spring-security-web` should NOT be in `<sharedLibrary>`. | FAIL |
| 29 | Add `eapimuleutilities` | XPath | `pom.xml` | `//dependencies/dependency[artifactId='eapimuleutilities']` MUST exist. | FAIL |
| 30 | Verify Dependency Versions | XPath | `pom.xml` | *General check, covered by specific rules?* | INFO |
| 31 | Encrypt Logic Update | XPath | `*.xml` | `//crypto:jce-encrypt-pbe` should NOT exist. `//set-variable[contains(@value, 'EncUtil::encryptJcePbe')]` SHOULD exist. | FAIL |
| 32 | Update `apimethod` logic | XPath | `*.xml` | `//set-variable[@variableName='apimethod' and contains(text(), 'encodedRequestPath')]` (approx check). | FAIL |
| 33 | Splunk Logs | Manual | Logs | Runtime check. | INFO |
| 35 | `TruistWSSEncDecPolicy` 3.0.0 | Regex | Policy | Version check `3.0.0` | FAIL |
| 36 | Uncomment Auto-Discovery | XPath | `*.xml` | `//api-gateway:autodiscovery` MUST exist (not commented out). | FAIL |
| 37 | `TruistJSONEncDecPolicy` 3.0.0 | Regex | Policy | Version check `3.0.0` | FAIL |
| 38 | Testing ?wsdl | Manual | Testing | Runtime check. | INFO |

## Next Steps
1.  Generate `custom_rules.yaml` based on the automated rules above.
2.  Review with user.
