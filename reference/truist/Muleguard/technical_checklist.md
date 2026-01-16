# Technical Checklist for MuleGuard Rules

This document translates the client's business checklist into specific MuleGuard Rule definitions.

## Rule Types Reference
*   `JSON_VALIDATION_REQUIRED`: Validates JSON structure/values.
*   `XML_ATTRIBUTE_NOT_EXISTS`: Ensures attributes are removed.
*   `GENERIC_TOKEN_SEARCH_FORBIDDEN`: Regex/String search for forbidden patterns.
*   `XML_ATTRIBUTE_EXISTS`: Validates attribute values.
*   `MANDATORY_PROPERTY_VALUE_CHECK`: Validates property key/value pairs.
*   `CONDITIONAL_CHECK`: Complex logic (e.g., "If Proxy, then check X").
*   `POM_VALIDATION_REQUIRED`: Validates POM elements.
*   `POM_VALIDATION_FORBIDDEN`: Clean up POM dependencies/plugins.
*   `XML_XPATH_EXISTS`: Validates existence of XML elements/logic.
*   `XML_ELEMENT_CONTENT_FORBIDDEN`: Validates content inside elements.

## Technical Rules

<table style="width:100%">
  <colgroup>
    <col style="width:8%">
    <col style="width:22%">
    <col style="width:15%">
    <col style="width:15%">
    <col style="width:32%">
    <col style="width:8%">
  </colgroup>
  <thead>
    <tr>
      <th>Rule ID</th>
      <th>Checklist Item</th>
      <th>Rule Type</th>
      <th>Target Files</th>
      <th>Technical Configuration (Parameters)</th>
      <th>Severity</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><b>BANK-001</b></td>
      <td>1. <code>mule-artifact.json</code> validation</td>
      <td><code>JSON_VALIDATION_REQUIRED</code></td>
      <td><code>mule-artifact.json</code></td>
      <td><code>minVersions.minMuleVersion</code>: "4.9.7"<br><code>requiredFields.javaSpecificationVersions</code>: ["17"]<br><code>requiredElements</code>: ["secureProperties"]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-002</b></td>
      <td>2. Remove <code>toApplicationCode</code></td>
      <td><code>XML_ATTRIBUTE_NOT_EXISTS</code></td>
      <td><code>src/main/mule/*.xml</code></td>
      <td><code>elements</code>: ["logger"]<br><code>forbiddenAttributes</code>: ["toApplicationCode"]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-003</b></td>
      <td>3. Remove <code>fromApplicationCode</code></td>
      <td><code>XML_ATTRIBUTE_NOT_EXISTS</code></td>
      <td><code>src/main/mule/*.xml</code></td>
      <td><code>elements</code>: ["logger"]<br><code>forbiddenAttributes</code>: ["fromApplicationCode"]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-004</b></td>
      <td>4. No <code>jce-encrypt</code></td>
      <td><code>GENERIC_TOKEN_SEARCH_FORBIDDEN</code></td>
      <td><code>src/main/mule/*.xml</code></td>
      <td><code>tokens</code>: ["jce-encrypt"]<br><code>matchMode</code>: "SUBSTRING"</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-005</b></td>
      <td>5. <code>soapVersion</code> check</td>
      <td><code>XML_ATTRIBUTE_EXISTS</code></td>
      <td><code>src/main/mule/*-config.xml</code></td>
      <td><code>elementAttributeSets</code>: [{element: "apikit-soap:config", attributes: {soapVersion: "SOAP_11"}}, {element: "apikit-soap:config", attributes: {soapVersion: "SOAP_12"}}]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-006</b></td>
      <td>6. Header Injection WARN</td>
      <td><code>MANDATORY_PROPERTY_VALUE_CHECK</code></td>
      <td><code>src/main/resources/policies/*.yaml</code></td>
      <td><code>properties</code>: [{name: "headerinjection.policy.inboundheadermap.x-tfc-request-out", values: ["WARN"]}, {name: "headerinjection.policy.inboundheadermap.x-tfc-response-in", values: ["WARN"]}]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-007</b></td>
      <td>7. <code>Truist-mTLS-AuthZ</code> 3.0.0 (EAPIGW)</td>
      <td><code>CONDITIONAL_CHECK</code></td>
      <td><code>src/main/resources/policies/*.yaml</code></td>
      <td><b>Precondition:</b> <code>PROJECT_CONTEXT</code> (nameContains: "proxy") OR <code>FILE_EXISTS</code> (policy file)<br><b>Success:</b> <code>MANDATORY_PROPERTY_VALUE_CHECK</code> (name: "truist.mTLS-AuthZ.policy.version", values: ["3.0.0"])</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-008</b></td>
      <td>8. Replace AAA/Basic Auth</td>
      <td><code>XML_XPATH_NOT_EXISTS</code></td>
      <td><code>src/main/mule/*.xml</code></td>
      <td><code>xpathExpressions</code>: ["//*[contains(local-name(), 'Truist-AAA-SOAP')]", "//*[contains(local-name(), 'Truist-AAA-HTTP')]", "//*[contains(local-name(), 'TruistBasicAuthnPolicy')]"]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-009</b></td>
      <td>9. <code>TruistCompositeAuthnPolicy</code> 3.0.0</td>
      <td><code>MANDATORY_PROPERTY_VALUE_CHECK</code></td>
      <td><code>src/main/resources/policies/*.yaml</code></td>
      <td><code>properties</code>: [{name: "truist.compositeauthn.policy.version", values: ["3.0.0"]}]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-010</b></td>
      <td>10. <code>TruistAuthzPolicy</code> 3.0.0</td>
      <td><code>MANDATORY_PROPERTY_VALUE_CHECK</code></td>
      <td><code>src/main/resources/policies/*.yaml</code></td>
      <td><code>properties</code>: [{name: "truist.authz.policy.version", values: ["3.0.0"]}]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-011</b></td>
      <td>11. <code>noname</code> policy 5.1.0</td>
      <td><code>MANDATORY_PROPERTY_VALUE_CHECK</code></td>
      <td><code>src/main/resources/policies/*.yaml</code></td>
      <td><code>properties</code>: [{name: "noname.policy.version", values: ["5.1.0"]}]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-012</b></td>
      <td>12. <code>ratelimit</code> policy 1.4.1</td>
      <td><code>MANDATORY_PROPERTY_VALUE_CHECK</code></td>
      <td><code>src/main/resources/policies/*.yaml</code></td>
      <td><code>properties</code>: [{name: "ratelimit.policy.version", values: ["1.4.1"]}]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-013</b></td>
      <td>13. <code>headerinjection</code> 1.3.2</td>
      <td><code>MANDATORY_PROPERTY_VALUE_CHECK</code></td>
      <td><code>src/main/resources/policies/*.yaml</code></td>
      <td><code>properties</code>: [{name: "headerinjection.policy.version", values: ["1.3.2"]}]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-014</b></td>
      <td>14. <code>headerremoval</code> 1.1.2</td>
      <td><code>MANDATORY_PROPERTY_VALUE_CHECK</code></td>
      <td><code>src/main/resources/policies/*.yaml</code></td>
      <td><code>properties</code>: [{name: "headerremoval.policy.version", values: ["1.1.2"]}]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-015</b></td>
      <td>15. DB2 DVIPA Migration</td>
      <td><code>GENERIC_TOKEN_SEARCH_FORBIDDEN</code></td>
      <td><code>*.properties</code></td>
      <td>Flags legacy F5 VIPs (wil-db2contstvip, db2contppvip, etc.) and suggests environment-specific DVIPA URLs (dsndb0e:5029, dsndb0h:5051, etc.).</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-016</b></td>
      <td>16. No other property changes</td>
      <td><code>MANUAL_CHECK</code></td>
      <td><code>*.properties</code></td>
      <td>Comparison check.</td>
      <td>LOW</td>
    </tr>
    <tr>
      <td><b>BANK-017</b></td>
      <td>17. Update dev tracker</td>
      <td><code>MANUAL_CHECK</code></td>
      <td>N/A</td>
      <td>Process check.</td>
      <td>LOW</td>
    </tr>
    <tr>
      <td><b>BANK-018</b></td>
      <td>18. Error strings update</td>
      <td><code>GENERIC_TOKEN_SEARCH_FORBIDDEN</code></td>
      <td><code>src/main/mule/error-handling.xml</code></td>
      <td><code>tokens</code>: ["Old Error String 1", "Old Error String 2"] <i>(Need specific strings)</i></td>
      <td>LOW</td>
    </tr>
    <tr>
      <td><b>BANK-019</b></td>
      <td>19. Remove DLP Code</td>
      <td><code>XML_XPATH_NOT_EXISTS</code></td>
      <td><code>src/main/mule/http-proxy.xml</code></td>
      <td><code>xpathExpressions</code>: ["//choice/when[contains(@expression, 'north.bound.dlp.flag')]"]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-020</b></td>
      <td>20. Remove DLP Properties</td>
      <td><code>GENERIC_TOKEN_SEARCH_FORBIDDEN</code></td>
      <td><code>src/main/resources/*.properties</code></td>
      <td><code>tokens</code>: ["north.bound.dlp.flag", "south.bound.dlp.flag", "wmq.dlp.requestqueue"]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-021</b></td>
      <td>21. SQL DB Props</td>
      <td><code>GENERIC_TOKEN_SEARCH_REQUIRED</code></td>
      <td><code>src/main/resources/*.properties</code></td>
      <td><i>Complex Logic</i>: If "jdbc:sqlserver" found, line MUST contain ";encrypt=false;trustServerCertificate=false".</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-022</b></td>
      <td>22. MQ Cipher Suite</td>
      <td><code>MANDATORY_PROPERTY_VALUE_CHECK</code></td>
      <td><code>src/main/resources/*.properties</code></td>
      <td><code>properties</code>: [{name: "ibm.mq.cipherSuite", values: ["TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"]}]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-023</b></td>
      <td>23. Parent POM 3.0.0</td>
      <td><code>POM_VALIDATION_REQUIRED</code></td>
      <td><code>pom.xml</code></td>
      <td><code>validationType</code>: "PARENT"<br><code>groupId</code>: "com.truist.eapi"<br><code>artifactId</code>: "MuleParentPom"<br><code>version</code>: "3.0.0"</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-024</b></td>
      <td>24. POM Properties Check</td>
      <td><code>POM_VALIDATION_REQUIRED</code></td>
      <td><code>pom.xml</code></td>
      <td><code>validationType</code>: "PROPERTIES"<br><code>properties</code>: [{name: "muleMavenPluginVersion", expectedValue: "4.6.0"}, {name: "app.runtime", expectedValue: "4.9.LTS"}, {name: "cicd.mule.version", expectedValue: "4.9.LTS"}]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-025</b></td>
      <td>25. MUnit Version 3.5.0</td>
      <td><code>POM_VALIDATION_REQUIRED</code></td>
      <td><code>pom.xml</code></td>
      <td><code>validationType</code>: "PROPERTIES"<br><code>properties</code>: [{name: "munit.version", expectedValue: "3.5.0"}]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-026</b></td>
      <td>26. Remove Plugins</td>
      <td><code>POM_VALIDATION_FORBIDDEN</code></td>
      <td><code>pom.xml</code></td>
      <td><code>validationType</code>: "PLUGINS"<br><code>forbiddenPlugins</code>: [{groupId: "org.apache.maven.plugins", artifactId: "maven-clean-plugin"}, {groupId: "org.apache.maven.plugins", artifactId: "maven-compiler-plugin"}, {groupId: "com.mulesoft.munit.tools", artifactId: "munit-maven-plugin"}]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-027</b></td>
      <td>27. Remove Runtime Deps</td>
      <td><code>POM_VALIDATION_FORBIDDEN</code></td>
      <td><code>pom.xml</code></td>
      <td><code>validationType</code>: "DEPENDENCIES"<br><code>forbiddenDependencies</code>: [{groupId: "com.mulesoft.mule.runtime", artifactId: "mule-core-ee"}, {groupId: "com.mulesoft.mule.runtime.modules", artifactId: "mule-module-spring-config-ee"}]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-028</b></td>
      <td>28. Remove Shared Libs</td>
      <td><code>POM_VALIDATION_FORBIDDEN</code></td>
      <td><code>pom.xml</code></td>
      <td><code>validationType</code>: "SHARED_LIBRARIES" (If supported, else Regex)<br><code>tokens</code>: ["spring-security-ldap", "spring-security-web"]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-029</b></td>
      <td>29. Add <code>eapimuleutilities</code></td>
      <td><code>POM_VALIDATION_REQUIRED</code></td>
      <td><code>pom.xml</code></td>
      <td><code>validationType</code>: "DEPENDENCIES"<br><code>dependencies</code>: [{groupId: "com.truist.eapi.crypto", artifactId: "eapimuleutilities"}]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-031</b></td>
      <td>31. Encrypt Logic Replace</td>
      <td><code>XML_XPATH_NOT_EXISTS</code> & <code>XML_XPATH_EXISTS</code></td>
      <td><code>src/main/mule/*.xml</code></td>
      <td><b>Rule 31A (Gone):</b> <code>//crypto:jce-encrypt-pbe</code><br><b>Rule 31B (Present):</b> <code>//set-variable[contains(@value, 'EncUtil::encryptJcePbe')]</code></td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-032</b></td>
      <td>32. Update <code>apimethod</code></td>
      <td><code>XML_XPATH_EXISTS</code></td>
      <td><code>src/main/mule/*.xml</code></td>
      <td><code>//set-variable[@variableName='apimethod' and contains(@value, 'vars.encryptedRequestPath')]</code></td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-035</b></td>
      <td>35. <code>TruistWSSEncDecPolicy</code> 3.0.0</td>
      <td><code>MANDATORY_PROPERTY_VALUE_CHECK</code></td>
      <td><code>src/main/resources/policies/*.yaml</code></td>
      <td><code>properties</code>: [{name: "truist.wssencdec.policy.version", values: ["3.0.0"]}]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-036</b></td>
      <td>36. Uncomment AutoDiscovery</td>
      <td><code>XML_XPATH_EXISTS</code></td>
      <td><code>src/main/mule/global-config.xml</code></td>
      <td><code>//*[local-name()='autodiscovery']</code></td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-037</b></td>
      <td>37. <code>TruistJSONEncDecPolicy</code> 3.0.0</td>
      <td><code>MANDATORY_PROPERTY_VALUE_CHECK</code></td>
      <td><code>src/main/resources/policies/*.yaml</code></td>
      <td><code>properties</code>: [{name: "truist.jsonencdec.policy.version", values: ["3.0.0"]}]</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-038</b></td>
      <td>38. Legacy Dependency Upgrade</td>
      <td><code>POM_VALIDATION_FORBIDDEN</code></td>
      <td><code>pom.xml</code></td>
      <td>Detects legacy dependencies and suggests upgrades (javax.mail, ojdbc8, org.codehaus, GUID groups).</td>
      <td>HIGH</td>
    </tr>
    <tr>
      <td><b>BANK-040</b></td>
      <td>40. Java 17 Error Migration</td>
      <td><code>GENERIC_TOKEN_SEARCH_FORBIDDEN</code></td>
      <td><code>src/main/**/*.xml</code>, <code>src/main/**/*.dwl</code></td>
      <td>Detects Java 8 style error fields (error.muleMessage, error.exception.cause, etc.) and suggests Java 17 structure.</td>
      <td>HIGH</td>
    </tr>
  </tbody>
</table>
