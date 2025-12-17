# XML_ELEMENT_CONTENT_FORBIDDEN

## Overview

Validates that XML elements do **NOT contain forbidden content/tokens**. This rule **fails** if forbidden content **IS found** in the element's text content.

## Use Cases

- Prevent deprecated values or patterns
- Block hardcoded credentials in element content
- Disallow specific configuration values
- Enforce content restrictions

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePatterns` | List<String> | Glob patterns to match XML files |
| `elementContentPairs` | List<Map> | Element names with their forbidden content tokens |

#### Element Content Pair Map Structure

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `element` | String | Yes | XML element name |
| `forbiddenTokens` | List<String> | Yes | Tokens that must NOT be present |

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `matchMode` | String | `SUBSTRING` | `SUBSTRING` or `REGEX` |
| `caseSensitive` | Boolean | `true` | Case sensitivity for content matching |

## Configuration Examples

### Example 1: Block Hardcoded Passwords

```yaml
- id: "RULE-070"
  name: "No Hardcoded Passwords in Config"
  description: "Prevent hardcoded passwords in configuration elements"
  enabled: true
  severity: CRITICAL
  checks:
    - type: XML_ELEMENT_CONTENT_FORBIDDEN
      params:
        filePatterns:
          - "**/*.xml"
        elementContentPairs:
          - element: "password"
            forbiddenTokens: ["admin", "password123", "default"]
          - element: "secret"
            forbiddenTokens: ["hardcoded"]
```

### Example 2: Block Deprecated Values

```yaml
- id: "RULE-071"
  name: "No Deprecated Log Levels"
  description: "Prevent usage of deprecated log level values"
  enabled: true
  severity: MEDIUM
  checks:
    - type: XML_ELEMENT_CONTENT_FORBIDDEN
      params:
        filePatterns:
          - "src/main/mule/**/*.xml"
        elementContentPairs:
          - element: "logger"
            forbiddenTokens: ["TRACE", "ALL"]
```

### Example 3: Block IP Addresses (Regex)

```yaml
- id: "RULE-072"
  name: "No Hardcoded IPs in Endpoints"
  description: "Prevent hardcoded IP addresses in endpoint URLs"
  enabled: true
  severity: HIGH
  checks:
    - type: XML_ELEMENT_CONTENT_FORBIDDEN
      params:
        filePatterns:
          - "**/*.xml"
        elementContentPairs:
          - element: "http:request"
            forbiddenTokens: ["\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"]
        matchMode: REGEX
```

## Error Messages

```
config.xml element 'password' has forbidden content: admin
main-flow.xml element 'logger' has forbidden content: TRACE
```

## Related Rule Types

- **[XML_ELEMENT_CONTENT_REQUIRED](XML_ELEMENT_CONTENT_REQUIRED.md)** - Opposite: ensures content DOES exist
- **[XML_XPATH_NOT_EXISTS](XML_XPATH_NOT_EXISTS.md)** - More complex XPath-based validation
