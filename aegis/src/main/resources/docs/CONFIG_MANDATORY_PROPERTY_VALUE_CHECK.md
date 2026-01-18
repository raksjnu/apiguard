# MANDATORY_PROPERTY_VALUE_CHECK

**Rule Type:** `CONFIG` | **Config Engine** | `.properties`

## Overview

Validates that **required property name-value pairs exist** in environment-specific configuration files. This rule ensures that critical configuration keys are not only present but also set to one of the approved values.

## Use Cases

- Enforce production-grade log levels (e.g., block `DEBUG` in `PROD`).
- Ensure security features like SSL or authentication are explicitly enabled.
- Validate environment-specific type tags (e.g., `environment.type=PROD`).
- Standardize timeout values or retry counts across different services.

## Parameters

### Required Parameters

| Parameter | Type | Description |
| :--- | :--- | :--- |
| `fileExtensions` | List | File extensions to check (e.g., `.properties`) |
| `environments` | List | Environment names to check (e.g., `PROD`, `ALL`) |
| `properties` | List<Map> | List of property configurations |

### Property Configuration Map

| Field | Type | Description |
| :--- | :--- | :--- |
| `name` | String | Exactly matching property key name |
| `values` | List | List of allowed values (OR logic) |
| `caseSensitiveName` | Boolean | Override global name case sensitivity |
| `caseSensitiveValue` | Boolean | Override global value case sensitivity |

### Optional Parameters

| Parameter | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `delimiter` | String | `=` | The character separating key and value |
| `caseSensitiveNames` | Boolean | `true` | Global case sensitivity for property names |
| `caseSensitiveValues` | Boolean | `true` | Global case sensitivity for property values |

## Configuration Examples

### Example 1: Enforce Production Log Level
Ensure that logging is set to a sustainable level for production environments.

```yaml
- id: "RULE-PROD-LOG-LOCK"
  name: "Production Log Level"
  severity: HIGH
  errorMessage: "Production Log Level violation.\n{DEFAULT_MESSAGE}"
  checks:
    - type: MANDATORY_PROPERTY_VALUE_CHECK
      params:
        fileExtensions: [".properties"]
        environments: ["PROD"]
        properties:
          - name: "log.level"
            values: ["INFO", "WARN", "ERROR"]
```

### Example 2: Infrastructure Compliance
Verify that SSL is enabled and timeout values are within organizational limits.

```yaml
- id: "RULE-INFRA-STANDARDS"
  name: "Required Infrastructure Settings"
  severity: HIGH
  checks:
    - type: MANDATORY_PROPERTY_VALUE_CHECK
      params:
        fileExtensions: [".properties"]
        environments: ["DEV", "QA", "PROD"]
        properties:
          - name: "ssl.enabled"
            values: ["true"]
          - name: "connect.timeout"
            values: ["30", "60", "120"]
```

### Example 3: Flexible Boolean Validation
Allow various string representations of "true" or "false" in a case-insensitive manner.

```yaml
- id: "RULE-FLEX-BOOLEAN"
  name: "Boolean Property Validation"
  severity: MEDIUM
  checks:
    - type: MANDATORY_PROPERTY_VALUE_CHECK
      params:
        fileExtensions: [".properties"]
        environments: ["ALL"]
        caseSensitiveValues: false
        properties:
          - name: "debug.mode"
            values: ["true", "false", "on", "off", "yes", "no"]
```

## Error Messages

```
PROD.properties: Property 'log.level' not found in file.
QA.properties: Property 'ssl.enabled' found but value 'false' does not match allowed values [true].
```

## Best Practices

- **Explicit Failures**: This rule fails if the property is missing OR if the value is incorrect. Use it when a configuration must be present and correctly set.
- **Service-Level Consistency**: Apply these rules to ensure all components in a multi-service architecture use consistent timeout or retry logic.
- **Environment Targeting**: Heavily use the `environments` parameter to apply stricter value requirements to `PROD` than to `DEV`.

## Related Rule Types

- **[OPTIONAL_PROPERTY_VALUE_CHECK](CONFIG_OPTIONAL_PROPERTY_VALUE_CHECK.md)** - Logic for properties that might not exist.
- **[MANDATORY_SUBSTRING_CHECK](CONFIG_MANDATORY_SUBSTRING_CHECK.md)** - Validates substrings within property values.
