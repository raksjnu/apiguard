# XML_XPATH_EXISTS

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

## Related Rule Types

- **[XML_XPATH_NOT_EXISTS](XML_XPATH_NOT_EXISTS.md)** - Opposite: ensures XPath does NOT match
- **[XML_ATTRIBUTE_EXISTS](XML_ATTRIBUTE_EXISTS.md)** - Simpler attribute validation
- **[XML_ELEMENT_CONTENT_REQUIRED](XML_ELEMENT_CONTENT_REQUIRED.md)** - Validate element content
