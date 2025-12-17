# OPTIONAL_PROPERTY_VALUE_CHECK

## Overview

Validates **optional property values** in configuration files. If property does NOT exist → PASS. If property exists → value must match allowed values.

## Use Cases

- Validate optional configuration when present
- Ensure correct values for optional properties
- Allow flexibility while enforcing correctness
- Check conditional configuration

## Parameters

Same as [MANDATORY_PROPERTY_VALUE_CHECK](MANDATORY_PROPERTY_VALUE_CHECK.md), but with different validation logic.

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `fileExtensions` | List<String> | File extensions to check |
| `environments` | List<String> | Environment names to check |
| `properties` | List<Map> | List of optional property configurations |

## Configuration Examples

### Example 1: Optional Debug Configuration

```yaml
- id: "RULE-140"
  name: "Optional Debug Configuration"
  description: "If debug property exists, it must be true or false"
  enabled: true
  severity: MEDIUM
  checks:
    - type: OPTIONAL_PROPERTY_VALUE_CHECK
      params:
        fileExtensions: [".properties"]
        environments: ["ALL"]
        properties:
          - name: "debug.enabled"
            values: ["true", "false"]
```

### Example 2: Optional Cache Settings

```yaml
- id: "RULE-141"
  name: "Optional Cache Configuration"
  description: "If cache.type exists, validate allowed values"
  enabled: true
  severity: LOW
  checks:
    - type: OPTIONAL_PROPERTY_VALUE_CHECK
      params:
        fileExtensions: [".properties"]
        environments: ["DEV", "QA", "PROD"]
        properties:
          - name: "cache.type"
            values: ["memory", "redis", "none"]
          - name: "cache.ttl"
            values: ["300", "600", "1800", "3600"]
```

## Validation Logic

- **Property NOT found** → ✅ PASS (optional, so absence is OK)
- **Property found with valid value** → ✅ PASS
- **Property found with invalid value** → ❌ FAIL

## Error Messages

```
DEV.properties: Optional property 'debug.enabled' found but value does not match expected values [true, false]
```

## Related Rule Types

- **[MANDATORY_PROPERTY_VALUE_CHECK](MANDATORY_PROPERTY_VALUE_CHECK.md)** - For required properties
- **[MANDATORY_SUBSTRING_CHECK](MANDATORY_SUBSTRING_CHECK.md)** - Simpler substring validation
