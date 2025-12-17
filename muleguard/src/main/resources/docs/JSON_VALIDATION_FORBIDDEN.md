# JSON_VALIDATION_FORBIDDEN

## Overview

Validates that **forbidden JSON elements do NOT exist** in JSON files. This rule **fails** if forbidden elements **ARE found**.

## Use Cases

- Prevent deprecated configuration keys
- Block specific JSON fields
- Disallow certain metadata in JSON files
- Enforce JSON structure restrictions

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePattern` | String | Filename pattern to match (e.g., `mule-artifact.json`) |
| `forbiddenElements` | List<String> | List of forbidden JSON keys (top-level only) |

## Configuration Examples

### Example 1: Block Deprecated Fields

```yaml
- id: "RULE-110"
  name: "No Deprecated Mule Artifact Fields"
  description: "Prevent usage of deprecated fields in mule-artifact.json"
  enabled: true
  severity: MEDIUM
  checks:
    - type: JSON_VALIDATION_FORBIDDEN
      params:
        filePattern: "mule-artifact.json"
        forbiddenElements:
          - "deprecatedField"
          - "legacyConfig"
```

### Example 2: Block Sensitive Data

```yaml
- id: "RULE-111"
  name: "No Credentials in Config JSON"
  description: "Prevent hardcoded credentials in JSON configuration"
  enabled: true
  severity: CRITICAL
  checks:
    - type: JSON_VALIDATION_FORBIDDEN
      params:
        filePattern: "config.json"
        forbiddenElements:
          - "password"
          - "apiKey"
          - "secret"
```

## Error Messages

```
mule-artifact.json has forbidden element: deprecatedField
config.json has forbidden element: password
```

## Related Rule Types

- **[JSON_VALIDATION_REQUIRED](JSON_VALIDATION_REQUIRED.md)** - Opposite: ensures elements DO exist
- **[GENERIC_TOKEN_SEARCH_FORBIDDEN](GENERIC_TOKEN_SEARCH_FORBIDDEN.md)** - More flexible JSON content validation
