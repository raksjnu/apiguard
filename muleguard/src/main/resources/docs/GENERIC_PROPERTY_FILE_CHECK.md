# GENERIC_PROPERTY_FILE_CHECK

## Overview

Generic property file validation with flexible configuration options. Provides comprehensive validation for property files across environments.

## Use Cases

- General property file validation
- Multi-environment configuration checks
- Property format validation
- Configuration completeness verification

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `fileExtensions` | List<String> | File extensions to check |
| `environments` | List<String> | Environment names to validate |
| `validationRules` | List<Map> | Custom validation rules |

#### Validation Rule Map

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | String | Yes | Validation type: `REQUIRED`, `FORBIDDEN`, `FORMAT` |
| `pattern` | String | Yes | Pattern to match or validate |
| `message` | String | No | Custom error message |

## Configuration Examples

### Example 1: Basic Property Validation

```yaml
- id: "RULE-170"
  name: "Property File Format Validation"
  description: "Validate property file format and required keys"
  enabled: true
  severity: MEDIUM
  checks:
    - type: GENERIC_PROPERTY_FILE_CHECK
      params:
        fileExtensions: [".properties"]
        environments: ["ALL"]
        validationRules:
          - type: "REQUIRED"
            pattern: "app.name"
          - type: "REQUIRED"
            pattern: "app.version"
```

### Example 2: Format Validation

```yaml
- id: "RULE-171"
  name: "Property Value Format"
  description: "Ensure property values follow expected formats"
  enabled: true
  severity: LOW
  checks:
    - type: GENERIC_PROPERTY_FILE_CHECK
      params:
        fileExtensions: [".properties"]
        environments: ["DEV", "QA", "PROD"]
        validationRules:
          - type: "FORMAT"
            pattern: "port=\\d+"
            message: "Port must be numeric"
          - type: "FORMAT"
            pattern: "timeout=\\d+[smh]"
            message: "Timeout must include unit (s/m/h)"
```

### Example 3: Forbidden Patterns

```yaml
- id: "RULE-172"
  name: "No Hardcoded Values"
  description: "Prevent hardcoded values in property files"
  enabled: true
  severity: HIGH
  checks:
    - type: GENERIC_PROPERTY_FILE_CHECK
      params:
        fileExtensions: [".properties", ".yaml"]
        environments: ["PROD"]
        validationRules:
          - type: "FORBIDDEN"
            pattern: "password=.*"
            message: "Passwords must use secure properties"
          - type: "FORBIDDEN"
            pattern: "localhost"
            message: "No hardcoded localhost in production"
```

## Error Messages

```
PROD.properties: Required property 'app.name' not found
DEV.properties: Port must be numeric (validation failed for 'port=abc')
QA.properties: No hardcoded localhost in production
```

## Related Rule Types

- **[MANDATORY_PROPERTY_VALUE_CHECK](MANDATORY_PROPERTY_VALUE_CHECK.md)** - Specific property-value validation
- **[MANDATORY_SUBSTRING_CHECK](MANDATORY_SUBSTRING_CHECK.md)** - Token-based validation
- **[GENERIC_TOKEN_SEARCH](GENERIC_TOKEN_SEARCH.md)** - Advanced token search
