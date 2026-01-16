# XML_ATTRIBUTE_EXISTS

**Rule Type:** `CODE` - **Applies To:** XML configuration files

## Overview

Validates that **required XML attributes exist** on specified elements with optional value validation. This is one of the most flexible rules, supporting three distinct validation modes. The rule **fails** if required attributes or values are **NOT found**.

## Use Cases

- Ensure flow elements have required attributes (name, doc:id)
- Validate configuration completeness (cipher suites, protocols, timeouts)
- Enforce naming conventions (e.g., specific values for specific attributes)
- Check that elements contain specific attribute combinations
- Validate connector configurations (hosts, ports, credentials)
- Ensure security settings (TLS versions, encryption algorithms)

## Validation Modes

This rule supports **three distinct modes** that can be used independently or combined:

1. **Simple Attribute Existence** - Check if attributes exist (any value)
2. **Attribute Value Pairs** - Validate specific attribute values on elements
3. **Element Attribute Sets** - Validate multiple attributes on the same element

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePatterns` | List<String> | Glob patterns to match XML files (e.g., `src/main/mule/*.xml`, `**/*.xml`) |

### Mode-Specific Parameters

You must configure **at least one** of these modes:

#### Mode 1: Simple Attribute Existence

| Parameter | Type | Description |
|-----------|------|-------------|
| `elements` | List<String> | XML element names to check |
| `attributes` | List<String> | Attributes that MUST exist on those elements |

#### Mode 2: Attribute Value Pairs

| Parameter | Type | Description |
|-----------|------|-------------|
| `attributeValuePairs` | List<Map> | List of element-attribute-value triplets |
| `attributeValuePairs[].element` | String | Element name |
| `attributeValuePairs[].attribute` | String | Attribute name |
| `attributeValuePairs[].expectedValue` | String | Required attribute value |

#### Mode 3: Element Attribute Sets

| Parameter | Type | Description |
|-----------|------|-------------|
| `elementAttributeSets` | List<Map> | List of element configurations |
| `elementAttributeSets[].element` | String | Element name |
| `elementAttributeSets[].attributes` | Map<String,String> | Map of attribute names to expected values |

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `caseSensitive` | Boolean | `true` | Case sensitivity for value matching |
| `propertyResolution` | Boolean | `false` | If `true`, allows `${property}` placeholders as valid matches |

---

## Configuration Examples

### Mode 1: Simple Attribute Existence

#### Example 1: Single Element, Single Attribute

Check that `crypto:jce-config` has a `type` attribute (any value).

```yaml
- id: "RULE-030"
  name: "Crypto Config Must Have Type"
  description: "All crypto configurations must specify a type"
  enabled: true
  severity: HIGH
  checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: ["src/main/mule/*.xml"]
        elements: ["crypto:jce-config"]
        attributes: ["type"]
```

**Behavior**:
- ✅ PASS if `<crypto:jce-config type="JCE">` exists
- ✅ PASS if `<crypto:jce-config type="SYMMETRIC">` exists
- ❌ FAIL if `<crypto:jce-config>` exists without `type` attribute

---

#### Example 2: Single Element, Multiple Attributes

Check that `http:listener` has both `path` and `config-ref` attributes.

```yaml
- id: "RULE-031"
  name: "HTTP Listener Must Be Configured"
  description: "HTTP listeners must have path and config-ref"
  enabled: true
  severity: HIGH
  checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: ["src/main/mule/*.xml"]
        elements: ["http:listener"]
        attributes: ["path", "config-ref"]
```

**Behavior**:
- ✅ PASS if `<http:listener path="/api" config-ref="HTTP_Config">` exists
- ❌ FAIL if `<http:listener path="/api">` exists (missing config-ref)
- ❌ FAIL if `<http:listener config-ref="HTTP_Config">` exists (missing path)

---

#### Example 3: Multiple Elements, Same Attributes

Check that multiple elements all have the same required attributes.

```yaml
- id: "RULE-032"
  name: "All Components Must Have Names"
  description: "Flow components must have name and doc:id"
  enabled: true
  severity: MEDIUM
  checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: ["src/main/mule/*.xml"]
        elements: 
          - "flow"
          - "sub-flow"
          - "error-handler"
        attributes: 
          - "name"
          - "doc:id"
```

**Behavior**: Each element type must have both `name` and `doc:id` attributes.

---

### Mode 2: Attribute Value Pairs

#### Example 4: Single Element, Single Attribute-Value

Validate that `http:listener` has `path` attribute with value `/api`.

```yaml
- id: "RULE-033"
  name: "API Endpoint Path Validation"
  description: "HTTP listener must use /api path"
  enabled: true
  severity: HIGH
  checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: ["src/main/mule/*.xml"]
        attributeValuePairs:
          - element: "http:listener"
            attribute: "path"
            expectedValue: "/api"
```

**Behavior**:
- ✅ PASS if `<http:listener path="/api">` exists
- ❌ FAIL if `<http:listener path="/test">` exists (wrong value)
- ❌ FAIL if no http:listener with path="/api" exists

---

#### Example 5: Multiple Attribute-Value Pairs

Validate multiple specific attribute values.

```yaml
- id: "RULE-034"
  name: "Database Configuration Validation"
  description: "Database connector must use correct settings"
  enabled: true
  severity: HIGH
  checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: ["src/main/mule/*.xml"]
        attributeValuePairs:
          - element: "db:config"
            attribute: "name"
            expectedValue: "Database_Config"
          - element: "db:connection"
            attribute: "database"
            expectedValue: "production_db"
```

**Behavior**: Both attribute-value pairs must exist in the file.

---

### Mode 3: Element Attribute Sets

#### Example 6: Single Element, Multiple Attributes with Values

Validate that an element has multiple specific attribute values.

```yaml
- id: "RULE-035"
  name: "IBM MQ Cipher Suite Validation"
  description: "IBM MQ must use approved cipher suite and protocol"
  enabled: true
  severity: CRITICAL
  checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: ["src/main/mule/*.xml"]
        caseSensitive: true
        elementAttributeSets:
          - element: "ibm:connection"
            attributes:
              cipherSuite: "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"
              sslProtocol: "TLSv1.2"
```

**Behavior**:
- ✅ PASS if `<ibm:connection cipherSuite="TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384" sslProtocol="TLSv1.2">` exists
- ❌ FAIL if cipher suite is different
- ❌ FAIL if SSL protocol is different
- ❌ FAIL if either attribute is missing

---

#### Example 7: Multiple Element Attribute Sets

Validate multiple elements with different attribute requirements.

```yaml
- id: "RULE-036"
  name: "Connector Security Standards"
  description: "All connectors must meet security standards"
  enabled: true
  severity: HIGH
  checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: ["src/main/mule/*.xml"]
        elementAttributeSets:
          # HTTP connector must use HTTPS
          - element: "http:request-config"
            attributes:
              protocol: "HTTPS"
              port: "443"
          
          # FTP connector must use secure mode
          - element: "ftp:config"
            attributes:
              mode: "PASSIVE"
              secure: "true"
```

**Behavior**: All element attribute sets must be found in the files.

---

#### Example 8: Property Resolution Support

Allow property placeholders in attribute values.

```yaml
- id: "RULE-037"
  name: "HTTP Listener with Properties"
  description: "HTTP listener can use property placeholders"
  enabled: true
  severity: MEDIUM
  checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: ["src/main/mule/*.xml"]
        propertyResolution: true  # Allow ${...} placeholders
        elementAttributeSets:
          - element: "http:listener"
            attributes:
              path: "/api"
```

**Behavior**:
- ✅ PASS if `<http:listener path="/api">` exists
- ✅ PASS if `<http:listener path="${http.api.path}">` exists (property placeholder)
- ❌ FAIL if `<http:listener path="/test">` exists (wrong value)

---

#### Example 9: Case-Insensitive Matching

Perform case-insensitive value matching.

```yaml
- id: "RULE-038"
  name: "Protocol Validation (Case-Insensitive)"
  description: "HTTP protocol can be any case"
  enabled: true
  severity: LOW
  checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: ["src/main/mule/*.xml"]
        caseSensitive: false  # Case-insensitive matching
        elementAttributeSets:
          - element: "http:request-config"
            attributes:
              protocol: "HTTPS"
```

**Behavior**:
- ✅ PASS if `protocol="HTTPS"` exists
- ✅ PASS if `protocol="https"` exists
- ✅ PASS if `protocol="Https"` exists
- ❌ FAIL if `protocol="HTTP"` exists (different value)

---

### Combined Modes

#### Example 10: Using Multiple Modes Together

Combine different validation modes in a single rule.

```yaml
- id: "RULE-039"
  name: "Comprehensive HTTP Configuration"
  description: "Validate HTTP configuration completeness"
  enabled: true
  severity: HIGH
  checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: ["src/main/mule/*.xml"]
        propertyResolution: true
        
        # Mode 1: Simple existence
        elements: ["http:listener-config"]
        attributes: ["name", "doc:id"]
        
        # Mode 3: Specific attribute sets
        elementAttributeSets:
          - element: "http:listener-connection"
            attributes:
              host: "0.0.0.0"
              port: "8081"
              protocol: "HTTP"
```

**Behavior**: All validations must pass.

---

### Advanced Use Cases

#### Example 11: Multiple File Patterns

Validate across different file locations.

```yaml
- id: "RULE-040"
  name: "Global Configuration Validation"
  description: "Validate configurations across all XML files"
  enabled: true
  severity: MEDIUM
  checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: 
          - "src/main/mule/*.xml"
          - "src/main/mule/config/*.xml"
          - "src/main/mule/flows/*.xml"
        elements: ["configuration"]
        attributes: ["doc:name"]
```

---

#### Example 12: Security Configuration Validation

Comprehensive security settings validation.

```yaml
- id: "RULE-041"
  name: "TLS Security Standards"
  description: "All TLS configurations must meet security standards"
  enabled: true
  severity: CRITICAL
  checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: ["**/*.xml"]
        caseSensitive: true
        elementAttributeSets:
          # TLS context must use TLS 1.2+
          - element: "tls:context"
            attributes:
              enabledProtocols: "TLSv1.2,TLSv1.3"
          
          # Key store must be configured
          - element: "tls:key-store"
            attributes:
              type: "jks"
              algorithm: "SunX509"
```

---

## Error Messages

When validation fails, you'll see detailed messages:

```
Element 'crypto:jce-config' missing attribute 'type' in file: src/main/mule/global.xml
Element 'http:listener' with attribute 'path'='/api' not found in file: src/main/mule/api.xml
Element 'ibm:connection' with required attributes {cipherSuite=TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384, sslProtocol=TLSv1.2} not found in file: src/main/mule/mq.xml
```

---

## Best Practices

### When to Use Each Mode

**Mode 1 (Simple Existence)**: Use when you only care that attributes exist, not their values.
```yaml
# Good for: Ensuring documentation attributes exist
elements: ["flow", "sub-flow"]
attributes: ["name", "doc:id"]
```

**Mode 2 (Attribute Value Pairs)**: Use for simple single attribute-value validations.
```yaml
# Good for: Checking specific attribute values across different elements
attributeValuePairs:
  - element: "http:listener"
    attribute: "path"
    expectedValue: "/api"
```

**Mode 3 (Element Attribute Sets)**: Use for complex multi-attribute validations on the same element.
```yaml
# Good for: Security configurations requiring multiple specific values
elementAttributeSets:
  - element: "tls:context"
    attributes:
      protocol: "TLSv1.2"
      cipherSuite: "STRONG"
      verifyHostname: "true"
```

### Property Resolution

Enable `propertyResolution: true` when:
- ✅ Configuration values come from property files
- ✅ Environment-specific values are used
- ✅ You want to allow both hardcoded and property-based values

Disable `propertyResolution: false` when:
- ❌ You want to enforce hardcoded values only
- ❌ Property placeholders should not be allowed

### Case Sensitivity

Use `caseSensitive: true` (default) when:
- ✅ Exact value matching is required
- ✅ Values are case-sensitive (URLs, cipher suites, protocols)

Use `caseSensitive: false` when:
- ✅ Values can be any case (TRUE/true/True)
- ✅ User-friendly validation is desired

---

## Solution Patterns and Technology References

The following table provides a quick reference to common technology-specific validation patterns supported by strict attribute checking.

| Technology | Best Practice Goal | Key Elements | Attributes Checked |
| :--- | :--- | :--- | :--- |
| **🐎 MuleSoft 4** | Flow Identification | `flow`, `sub-flow` | `name`, `doc:id` |
| **⚡ TIBCO BW 6** | Process Configuration | `bpws:process` | `name`, `targetNamespace` |
| **⚡ TIBCO BW 5** | Activity Naming | `pd:startType` | `name` |
| **☕ Java/Maven** | Dependency Locking | `dependency` | `version` |

### 🐎 MuleSoft 4 Patterns

**Scenario**: You need to ensure comprehensive auditability of your Mule flows. To do this, every flow must have a stable `name` and a unique `doc:id` generated by Anypoint Studio.

```yaml
id: "MULE-FLOW-ATTRS"
name: "Mule Flow Identification"
description: "Flows must have name and doc:id attributes for auditability"
checks:
  - type: XML_ATTRIBUTE_EXISTS
    params:
      filePatterns: ["src/main/mule/**/*.xml"]
      elements: ["flow", "sub-flow", "error-handler"]
      attributes: ["name", "doc:id"]
```

### ⚡ TIBCO BW 6.x Patterns

**Scenario**: In BusinessWorks 6, process definitions are XML-based. Ensuring the `bpws:process` element has a defined target namespace prevents collisions in large projects.

```yaml
id: "TIBCO-BW6-PROCESS-ATTRS"
name: "TIBCO Process Attributes"
description: "Ensure TIBCO processes have namespace attributes"
checks:
  - type: XML_ATTRIBUTE_EXISTS
    params:
      filePatterns: ["**/*.bwp"]
      elements: ["bpws:process"]
      attributes: ["name", "targetNamespace"]
```

### ⚡ TIBCO BW 5.x Patterns

**Scenario**: For legacy BW 5.x projects, validating the `.process` file structure ensures that start activities are properly named, aiding in migration and documentation.

```yaml
id: "TIBCO-BW5-START-ACTIVITY"
name: "TIBCO BW 5.x Start Activity Name"
description: "Start activity must have a defined name"
checks:
  - type: XML_ATTRIBUTE_EXISTS
    params:
      filePatterns: ["**/*.process"]
      elements: ["pd:startType"]
      attributes: ["name"]
```

### ☕ Java / Maven Patterns

**Scenario**: To prevent "dependency drift," enforce that every dependency in your `pom.xml` explicitly states its version (unless you are using a BOM/Parent POM strategy, in which case you might use the FORBIDDEN rule instead).

```yaml
id: "JAVA-MAVEN-DEP-VERSION"
name: "Maven Dependency Version Enforced"
description: "Dependencies must have a version attribute"
checks:
  - type: XML_ATTRIBUTE_EXISTS
    params:
      filePatterns: ["pom.xml"]
      elements: ["dependency"]
      attributes: ["version"]
```

---

## Related Rule Types

- **[XML_ATTRIBUTE_NOT_EXISTS](XML_ATTRIBUTE_NOT_EXISTS.md)** - Opposite: ensures attributes do NOT exist
- **[XML_XPATH_EXISTS](XML_XPATH_EXISTS.md)** - More complex XPath-based validation
- **[XML_ELEMENT_CONTENT_REQUIRED](XML_ELEMENT_CONTENT_REQUIRED.md)** - Validates element content instead of attributes
