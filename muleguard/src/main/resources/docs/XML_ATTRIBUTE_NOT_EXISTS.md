# XML_ATTRIBUTE_NOT_EXISTS

## Overview

Validates that **forbidden XML attributes do NOT exist** on specified elements. This rule **fails** if any of the specified forbidden attributes are found on the target elements.

## Use Cases

- Detect deprecated attributes (e.g., legacy code flags)
- Enforce cleanup of temporary or forbidden configuration
- Prevent usage of insecure or problematic attribute settings

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePatterns` | List<String> | Glob patterns to match XML files |
| `elements` | List<String> | List of XML element names to scan |
| `forbiddenAttributes` | List<String> | List of attributes that must NOT be present |

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `shouldIgnorePath` | Internal | N/A | Automatically ignores target, bin, .git, etc. |

## Configuration Examples

### Example 1: Remove Legacy Attributes
Ensure no elements contain attributes related to legacy application code tracking.

```yaml
- id: "RULE-010"
  name: "Remove unsupported from/to ApplicationCode"
  description: "Scans for legacy tracking attributes"
  enabled: true
  severity: HIGH
  checks:
    - type: XML_ATTRIBUTE_NOT_EXISTS
      params:
        filePatterns: ["src/main/mule/*.xml"]
        elements:
          - "request_in"
          - "request_out"
          - "ee:transform"
        forbiddenAttributes:
          - "fromApplicationCode"
          - "toApplicationCode"
          - "legacyFlag"
```

### Example 2: Clean up Connector Attributes
Ensure `doc:id` is not present if manual ID management is forbidden (example).

```yaml
- id: "RULE-099"
  name: "No Manual IDs"
  enabled: true
  checks:
    - type: XML_ATTRIBUTE_NOT_EXISTS
      params:
        filePatterns: ["src/main/mule/*.xml"]
        elements: ["http:listener"]
        forbiddenAttributes: ["doc:id"]
```

## Error Messages

```
Forbidden attribute 'fromApplicationCode' found on element 'request_in' in file: src/main/mule/common.xml
```

## Related Rule Types

- **[XML_ATTRIBUTE_EXISTS](XML_ATTRIBUTE_EXISTS.md)** - Opposite: ensures attributes DO exist
