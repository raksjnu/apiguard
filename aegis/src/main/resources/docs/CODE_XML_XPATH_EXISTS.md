# XML_XPATH_EXISTS

**Rule Type:** `CODE` - **Applies To:** XML configuration files

## Overview

Validates that **required XPath expressions match** in XML files. This rule **fails** if any required XPath expression does **NOT** find matching nodes. It is the core tool for complex XML structure validation.

## Use Cases

- Verify specific XML elements or attributes exist within a hierarchy.
- Ensure required configuration is present in integration flows (MSoft, TIBCO, etc.).
- Validate XML structure beyond simple existence (e.g., "Parent must have Child").
- Check for mandatory namespaces, schemas, or specific attribute values.

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePatterns` | List<String> | Glob patterns to match XML files |
| `xpathExpressions` | List<Map> | List of XPath expressions with optional custom failure messages |
| `requiredFields` | Map<String, String> | XPath-Value pairs for exact matching (Optional) |
| `minVersions` | Map<String, String> | XPath-MinVersion pairs for SemVer check (Optional) |
| `exactVersions` | Map<String, String> | XPath-Version pairs for Exact SemVer check (Optional) |

#### XPath Expression Map Structure

Each item in `xpathExpressions` can have:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `xpath` | String | Yes | The XPath expression to evaluate |
| `failureMessage` | String | No | Custom message if XPath doesn't match |

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `resolveProperties` | Boolean | `false` | Enable `${property}` placeholder resolution |
| `resolveLinkedConfig`| Boolean | `false` | Resolve from linked CONFIG project |
| `includeLinkedConfig`| Boolean | `false` | Scan files in linked CONFIG project |

## Configuration Examples

### Example 1: Basic component existence
Ensure that a specific component (e.g., a "logger" or custom plugin) is used in the configuration.

```yaml
- id: "RULE-XML-XPATH-01"
  name: "Logger Component Required"
  description: "Ensure all flows have at least one logger"
  enabled: true
  severity: MEDIUM
  checks:
    - type: XML_XPATH_EXISTS
      params:
        filePatterns:
          - "**/*.xml"
        xpathExpressions:
          - xpath: "//logger"
            failureMessage: "Configuration must contain at least one logger"
```

### Example 2: Check for specific nested configuration
Validates that a `service` element has a defined `endpoint` attribute.

```yaml
- id: "RULE-XML-XPATH-02"
  name: "Endpoint Config Required"
  description: "All services must have a defined endpoint"
  enabled: true
  severity: HIGH
  checks:
    - type: XML_XPATH_EXISTS
      params:
        filePatterns:
          - "**/*-config.xml"
        xpathExpressions:
          - xpath: "//service[@endpoint]"
            failureMessage: "Service element missing required 'endpoint' attribute"
```

### Example 3: Namespace-Aware local-name validation
Safely validate elements regardless of their specific prefix by using `local-name()`.

```yaml
- id: "RULE-XML-XPATH-NAMESPACE"
  name: "Listener Configuration Check"
  description: "Ensure specific listener configuration exists"
  enabled: true
  severity: HIGH
  checks:
    - type: XML_XPATH_EXISTS
      params:
        filePatterns:
          - "**/global-*.xml"
        xpathExpressions:
          - xpath: "//*[local-name()='listener-config']"
            failureMessage: "Missing required listener configuration"
```

## Error Messages

```
config.xml: Configuration must contain at least one logger (found 0 occurrence(s))
main-flow.xml: Service element missing required 'endpoint' attribute (found 0 occurrence(s))
```

## Best Practices

- **Use local-name() for portability**: When XML files use multiple namespaces/prefixes, `//*[local-name()='elementName']` is often more robust than prefix-dependent paths.
- **Fail Early**: Combine with `FILE_EXISTS` or `FILE_PATTERN` to ensure you are scanning the correct files.
- **Granular Paths**: Avoid too many `//` (deep descendants) if performance is a concern on massive XML files; use specific paths like `/root/parent/child` when possible.

## Related Rule Types

- **[XML_XPATH_NOT_EXISTS](XML_XPATH_NOT_EXISTS.md)** - Opposite: ensures nodes do NOT exist.
- **[XML_ATTRIBUTE_EXISTS](XML_ATTRIBUTE_EXISTS.md)** - Simpler check for high-level attributes.
- **[XML_ELEMENT_CONTENT_REQUIRED](XML_ELEMENT_CONTENT_REQUIRED.md)** - Validate the text content inside an element.

## Solution Patterns and Technology References

| Technology | Best Practice Goal | Key Elements | XPath Logic |
| :--- | :--- | :--- | :--- |
| **⚡ TIBCO BW 5** | Process Validity | `ProcessDefinition` | Check for start/end activities |
| **⚡ TIBCO BW 6** | Namespace Binding | `service` | Namespace-aware paths |
| **🐎 MuleSoft** | Build Configuration | `mule-maven-plugin` | Value-based predicate check |
| **🌐 Web Svcs** | WSDL Compliance | `definitions` | Structure validation |

### ⚡ TIBCO BW 5.x Patterns

```yaml
id: "TIBCO-XPATH-PD"
name: "Valid Process Definition"
description: "Ensure pd:ProcessDefinition has start and end activities"
checks:
  - type: XML_XPATH_EXISTS
    params:
      filePatterns: ["*.process"]
      xpathExpressions:
        - xpath: "//*[local-name()='ProcessDefinition']/*[local-name()='startType']"
        - xpath: "//*[local-name()='ProcessDefinition']/*[local-name()='endType']"
```

### 🐎 MuleSoft / Java Patterns

```yaml
id: "MAVEN-MULE-PLUGIN"
name: "Mule Maven Plugin Required"
description: "Ensure correct plugin is present in pom.xml build section"
checks:
  - type: XML_XPATH_EXISTS
    params:
      filePatterns: ["pom.xml"]
      xpathExpressions:
        - xpath: "//*[local-name()='plugin']/*[local-name()='artifactId' and text()='mule-maven-plugin']"
```
