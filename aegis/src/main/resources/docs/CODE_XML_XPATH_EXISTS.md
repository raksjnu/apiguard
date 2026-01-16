# XML_XPATH_EXISTS

**Rule Type:** `CODE` - **Applies To:** XML configuration files

## Overview

Validates that **required XPath expressions match** in XML files. This rule **fails** if any required XPath expression does **NOT** find matching nodes.

## Use Cases

- Verify specific XML elements or attributes exist
- Ensure required configuration is present in Mule flows
- Validate XML structure and hierarchy
- Check for mandatory namespaces or schemas

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePatterns` | List<String> | Glob patterns to match XML files |
| `xpathExpressions` | List<Map> | List of XPath expressions with optional custom failure messages |

#### XPath Expression Map Structure

Each item in `xpathExpressions` can have:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `xpath` | String | Yes | The XPath expression to evaluate |
| `failureMessage` | String | No | Custom message if XPath doesn't match |

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `propertyResolution` | Boolean | `false` | Enable `${property}` placeholder resolution |

## Configuration Examples

### Example 1: Basic - Ensure Logger Exists

```yaml
- id: "RULE-020"
  name: "Logger Component Required"
  description: "Ensure all flows have a logger component"
  enabled: true
  severity: MEDIUM
  checks:
    - type: XML_XPATH_EXISTS
      params:
        filePatterns:
          - "src/main/mule/**/*.xml"
        xpathExpressions:
          - xpath: "//logger"
            failureMessage: "Flow must contain at least one logger component"
```

### Example 2: Check for Specific Attribute

```yaml
- id: "RULE-021"
  name: "Flow Name Attribute Required"
  description: "All flows must have a name attribute"
  enabled: true
  severity: HIGH
  checks:
    - type: XML_XPATH_EXISTS
      params:
        filePatterns:
          - "**/*-config.xml"
        xpathExpressions:
          - xpath: "//flow[@name]"
            failureMessage: "Flow element missing required 'name' attribute"
```

### Example 3: Multiple XPath Validations

```yaml
- id: "RULE-022"
  name: "Required Error Handling Structure"
  description: "Ensure proper error handling is configured"
  enabled: true
  severity: HIGH
  checks:
    - type: XML_XPATH_EXISTS
      params:
        filePatterns:
          - "src/main/mule/**/*.xml"
        xpathExpressions:
          - xpath: "//error-handler"
            failureMessage: "Missing error-handler element"
          - xpath: "//on-error-continue | //on-error-propagate"
            failureMessage: "Error handler must have on-error-continue or on-error-propagate"
```

### Example 4: Namespace-Aware XPath

```yaml
- id: "RULE-023"
  name: "HTTP Listener Configuration"
  description: "Ensure HTTP listener configuration exists"
  enabled: true
  severity: HIGH
  checks:
    - type: XML_XPATH_EXISTS
      params:
        filePatterns:
          - "**/global-config.xml"
        xpathExpressions:
          - xpath: "//*[local-name()='listener-config']"
            failureMessage: "Missing HTTP listener configuration"
```

## Error Messages

When validation fails, you'll see messages like:

```
config.xml: Flow must contain at least one logger component (found 0 occurrence(s))
main-flow.xml: Missing error-handler element (found 0 occurrence(s))
```


## Best Practices

### When to Use This Rule
- ✅ Complex XML structure validation requiring XPath expressions
- ✅ Validating nested element relationships
- ✅ Checking conditional element presence
- ✅ Advanced attribute and element combinations

### XPath Pattern Examples
```yaml
# Check nested elements
xpathExpressions:
  - "//flow[@name='main']/http:listener"
  
# Validate attribute combinations
xpathExpressions:
  - "//tls:context[@enabledProtocols='TLSv1.2']"
```

### When to Use XPath vs Other Rules
- Use **XML_ATTRIBUTE_EXISTS** for simple attribute checks
- Use **XML_XPATH_EXISTS** for complex nested validations
- Use **XML_ELEMENT_CONTENT** for element text validation

## Related Rule Types

- **[XML_ELEMENT_CONTENT_REQUIRED](XML_ELEMENT_CONTENT_REQUIRED.md)** - Validate element content

## Solution Patterns and Technology References

The following table serves as a quick reference for enforcing complex structure validation using XPath.

| Technology | Best Practice Goal | Key Elements | XPath Logic |
| :--- | :--- | :--- | :--- |
| **⚡ TIBCO BW 5** | Process Validity | `ProcessDefinition` | Check for start/end activities |
| **⚡ TIBCO BW 6** | Namespace Binding | `service` | Namespace-aware paths |
| **🐎 MuleSoft** | Build Configuration | `mule-maven-plugin` | Value-based predicate check |
| **🌐 Web Svcs** | WSDL Compliance | `definitions` | Structure validation |

### ⚡ TIBCO BW 5.x Patterns

**Scenario**: A valid TIBCO BusinessWorks 5.x process definition must always have a defined start and end activity.

```yaml
id: "TIBCO-XPATH-01"
name: "Valid Process Definition"
description: "Ensure pd:ProcessDefinition has start and end activities"
enabled: true
severity: HIGH
checks:
  - type: XML_XPATH_EXISTS
    params:
      filePatterns: ["*.process"]
      xpathExpressions:
        - xpath: "//*[local-name()='ProcessDefinition']/*[local-name()='startType']"
        - xpath: "//*[local-name()='ProcessDefinition']/*[local-name()='endType']"
```

### ⚡ TIBCO BW 6.x Patterns

**Scenario**: In BW 6.x, validating that services are properly defined within the `TIBCO.xml` application descriptor ensures correct deployment bindings.

```yaml
id: "TIBCO-XPATH-02"
name: "Module Namespace Check"
description: "Ensure TIBCO.xml defines specific namespaces"
enabled: true
severity: MEDIUM
checks:
  - type: XML_XPATH_EXISTS
    params:
      filePatterns: ["TIBCO.xml"]
      xpathExpressions:
        - xpath: "/*[local-name()='application']/*[local-name()='service']"
```

### 🐎 MuleSoft / Java Patterns

**Scenario**: While simple attribute checks work for existence, XPath allows checking for specific values within nested elements, like verifying the `maven-mule-plugin` is not just present, but configured correctly.

```yaml
id: "MAVEN-01"
name: "Mule Maven Plugin Required"
description: "Ensure mule-maven-plugin is present in build/plugins"
enabled: true
severity: HIGH
checks:
  - type: XML_XPATH_EXISTS
    params:
      filePatterns: ["pom.xml"]
      xpathExpressions:
        - xpath: "//*[local-name()='plugin']/*[local-name()='artifactId' and text()='mule-maven-plugin']"
```

### 🌐 General XML / Web Services Patterns

**Scenario**: For SOAP-based integration projects, ensuring the WSDL strictly adheres to the schema (e.g., containing both definitions and services) prevents runtime faults.

```yaml
id: "WSDL-01"
name: "Valid WSDL Structure"
description: "Ensure wsdl:definitions contains wsdl:service"
enabled: true
severity: MEDIUM
checks:
  - type: XML_XPATH_EXISTS
    params:
      filePatterns: ["*.wsdl"]
      xpathExpressions:
        - xpath: "//*[local-name()='definitions']/*[local-name()='service']"
```
