# MANDATORY_PROPERTY_VALUE_CHECK

## Overview

Validates that **required property name-value pairs exist** in environment-specific configuration files. Ensures properties have correct values.

## Use Cases

- Validate property values match expected values
- Ensure configuration correctness across environments
- Check for required property settings
- Enforce configuration standards

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `fileExtensions` | List<String> | File extensions to check (e.g., `.properties`) |
| `environments` | List<String> | Environment names to check |
| `properties` | List<Map> | List of property configurations |

#### Property Configuration Map

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | String | Yes | Property name |
| `values` | List<String> | Yes | Allowed values (OR logic) |
| `caseSensitiveName` | Boolean | No | Override global name case sensitivity |
| `caseSensitiveValue` | Boolean | No | Override global value case sensitivity |

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `delimiter` | String | `=` | Property delimiter |
| `caseSensitiveNames` | Boolean | `true` | Global case sensitivity for property names |
| `caseSensitiveValues` | Boolean | `true` | Global case sensitivity for property values |

## Configuration Examples

### Example 1: Validate Log Level

```yaml
- id: "RULE-130"
  name: "Production Log Level"
  description: "Production must use INFO or WARN log level"
  enabled: true
  severity: HIGH
  checks:
    - type: MANDATORY_PROPERTY_VALUE_CHECK
      params:
        fileExtensions: [".properties"]
        environments: ["PROD"]
        properties:
          - name: "log.level"
            values: ["INFO", "WARN"]
```

### Example 2: Multiple Properties

```yaml
- id: "RULE-131"
  name: "Required Environment Configuration"
  description: "Validate required environment properties"
  enabled: true
  severity: HIGH
  checks:
    - type: MANDATORY_PROPERTY_VALUE_CHECK
      params:
        fileExtensions: [".properties"]
        environments: ["DEV", "QA", "PROD"]
        properties:
          - name: "environment.type"
            values: ["DEV", "QA", "PROD"]
          - name: "ssl.enabled"
            values: ["true"]
          - name: "timeout.seconds"
            values: ["30", "60", "120"]
```

### Example 3: Case-Insensitive Values

```yaml
- id: "RULE-132"
  name: "Boolean Property Validation"
  description: "Validate boolean properties accept various formats"
  enabled: true
  severity: MEDIUM
  checks:
    - type: MANDATORY_PROPERTY_VALUE_CHECK
      params:
        fileExtensions: [".properties"]
        environments: ["ALL"]
        caseSensitiveValues: false
        properties:
          - name: "debug.enabled"
            values: ["true", "false", "yes", "no"]
```

## Error Messages

```
PROD.properties: Property 'log.level' not found in file
DEV.properties: Property 'ssl.enabled' found but value does not match expected values [true]
```

## Related Rule Types

- **[OPTIONAL_PROPERTY_VALUE_CHECK](OPTIONAL_PROPERTY_VALUE_CHECK.md)** - Validates optional properties
- **[MANDATORY_SUBSTRING_CHECK](MANDATORY_SUBSTRING_CHECK.md)** - Simpler substring validation
