# XML_ELEMENT_CONTENT_REQUIRED

## Overview

Validates that XML elements contain **required content/tokens**. This rule **fails** if required content is **NOT found** in the element's text content.

## Use Cases

- Ensure specific values or patterns exist in element content
- Validate configuration values
- Check for required documentation or comments
- Enforce content standards

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePatterns` | List<String> | Glob patterns to match XML files |
| `elementContentPairs` | List<Map> | Element names with their required content tokens |

#### Element Content Pair Map Structure

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `element` | String | Yes | XML element name |
| `requiredTokens` | List<String> | Yes | Tokens that must be present in element content |

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `matchMode` | String | `SUBSTRING` | `SUBSTRING` or `REGEX` |
| `caseSensitive` | Boolean | `true` | Case sensitivity for content matching |
| `requireAll` | Boolean | `true` | If `true`, ALL tokens must be found. If `false`, at least ONE |

## Configuration Examples

### Example 1: Validate Logger Messages

```yaml
- id: "RULE-060"
  name: "Logger Messages Must Be Descriptive"
  description: "Logger messages must contain specific keywords"
  enabled: true
  severity: LOW
  checks:
    - type: XML_ELEMENT_CONTENT_REQUIRED
      params:
        filePatterns:
          - "src/main/mule/**/*.xml"
        elementContentPairs:
          - element: "logger"
            requiredTokens: ["Flow", "Request", "Response"]
        requireAll: false  # At least one keyword
```

### Example 2: Validate Documentation

```yaml
- id: "RULE-061"
  name: "Flow Documentation Required"
  description: "Flow doc:description must contain specific information"
  enabled: true
  severity: MEDIUM
  checks:
    - type: XML_ELEMENT_CONTENT_REQUIRED
      params:
        filePatterns:
          - "**/*.xml"
        elementContentPairs:
          - element: "doc:description"
            requiredTokens: ["Purpose:", "Input:", "Output:"]
        requireAll: true
```

### Example 3: Regex Pattern Matching

```yaml
- id: "RULE-062"
  name: "Version Format in Description"
  description: "Description must include version number"
  enabled: true
  severity: LOW
  checks:
    - type: XML_ELEMENT_CONTENT_REQUIRED
      params:
        filePatterns:
          - "**/pom.xml"
        elementContentPairs:
          - element: "description"
            requiredTokens: ["v\\d+\\.\\d+"]  # Regex: vX.Y
        matchMode: REGEX
```

## Error Messages

```
config.xml element 'logger' is missing required tokens: Flow, Request, Response
main-flow.xml element 'doc:description' is missing required tokens: Purpose:, Input:, Output:
```

## Related Rule Types

- **[XML_ELEMENT_CONTENT_FORBIDDEN](XML_ELEMENT_CONTENT_FORBIDDEN.md)** - Opposite: ensures content does NOT exist
- **[XML_XPATH_EXISTS](XML_XPATH_EXISTS.md)** - More complex XPath-based validation
