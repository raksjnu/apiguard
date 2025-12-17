# XML_XPATH_NOT_EXISTS

## Overview

Validates that **forbidden XPath expressions do NOT match** in XML files. This rule **fails** if any forbidden XPath expression **DOES** find matching nodes.

## Use Cases

- Prevent usage of deprecated XML elements
- Block specific configurations or patterns
- Enforce architectural constraints
- Disallow anti-patterns in Mule flows

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePatterns` | List<String> | Glob patterns to match XML files |
| `xpathExpressions` | List<Map> | List of XPath expressions that should NOT match |

#### XPath Expression Map Structure

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `xpath` | String | Yes | The XPath expression to evaluate |
| `failureMessage` | String | No | Custom message if XPath matches (forbidden) |

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `propertyResolution` | Boolean | `false` | Enable `${property}` placeholder resolution |

## Configuration Examples

### Example 1: Block Deprecated Component

```yaml
- id: "RULE-030"
  name: "No Deprecated HTTP Transport"
  description: "Prevent usage of deprecated HTTP transport"
  enabled: true
  severity: HIGH
  checks:
    - type: XML_XPATH_NOT_EXISTS
      params:
        filePatterns:
          - "**/*.xml"
        xpathExpressions:
          - xpath: "//*[local-name()='http-transport']"
            failureMessage: "Deprecated HTTP transport found - use HTTP connector instead"
```

### Example 2: Prevent Hardcoded Values

```yaml
- id: "RULE-031"
  name: "No Hardcoded Hosts"
  description: "Prevent hardcoded host values in HTTP requests"
  enabled: true
  severity: MEDIUM
  checks:
    - type: XML_XPATH_NOT_EXISTS
      params:
        filePatterns:
          - "src/main/mule/**/*.xml"
        xpathExpressions:
          - xpath: "//http:request[@host='localhost']"
            failureMessage: "Hardcoded 'localhost' found - use property placeholders"
          - xpath: "//http:request[contains(@host, '192.168')]"
            failureMessage: "Hardcoded IP address found in host attribute"
```

### Example 3: Enforce Architectural Rules

```yaml
- id: "RULE-032"
  name: "No Direct Database Calls in API Layer"
  description: "API flows should not contain direct database operations"
  enabled: true
  severity: HIGH
  checks:
    - type: XML_XPATH_NOT_EXISTS
      params:
        filePatterns:
          - "src/main/mule/api-*.xml"
        xpathExpressions:
          - xpath: "//db:select | //db:insert | //db:update | //db:delete"
            failureMessage: "Direct database operations forbidden in API layer - use service layer"
```

### Example 4: Block Synchronous Processing

```yaml
- id: "RULE-033"
  name: "No Synchronous VM Queues"
  description: "Prevent synchronous VM queue usage"
  enabled: true
  severity: MEDIUM
  checks:
    - type: XML_XPATH_NOT_EXISTS
      params:
        filePatterns:
          - "**/*.xml"
        xpathExpressions:
          - xpath: "//vm:publish[@sendCorrelationId='ALWAYS']"
            failureMessage: "Synchronous VM publish detected - use asynchronous pattern"
```

## Error Messages

When validation fails, you'll see messages like:

```
config.xml: Deprecated HTTP transport found - use HTTP connector instead (found 2 occurrence(s))
api-main.xml: Direct database operations forbidden in API layer - use service layer (found 1 occurrence(s))
```

## Related Rule Types

- **[XML_XPATH_EXISTS](XML_XPATH_EXISTS.md)** - Opposite: ensures XPath DOES match
- **[XML_ATTRIBUTE_NOT_EXISTS](XML_ATTRIBUTE_NOT_EXISTS.md)** - Simpler attribute validation
- **[XML_ELEMENT_CONTENT_FORBIDDEN](XML_ELEMENT_CONTENT_FORBIDDEN.md)** - Validate element content
