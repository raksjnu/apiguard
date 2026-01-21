# OPTIONAL_PROPERTY_VALUE_CHECK

**Rule Type:** `CONFIG` | **Config Engine** | `.properties`

## Overview

Validates **optional property values** in configuration files. This rule is designed for settings that are not required for the application to run, but if they are present, they must adhere to specific value constraints.

## Use Cases

- Validate experimental or feature flags when enabled (e.g., `feature.x=true/false`).
- Restrict cache provider types if the user chooses to configure caching.
- Enforce allowed TTL (Time-To-Live) values for non-mandatory session management.
- Validate optional monitoring levels or diagnostic flags.

## Parameters

Same as **[MANDATORY_PROPERTY_VALUE_CHECK](CONFIG_MANDATORY_PROPERTY_VALUE_CHECK.md)**, but with different validation logic.

### Required Parameters

| Parameter | Type | Description |
| :--- | :--- | :--- |
| `fileExtensions` | List | File extensions to check (e.g., `.properties`) |
| `environments` | List | Environment names to check (e.g., `ALL`, `PROD`) |
| `properties` | List | List of optional property configurations |
| `resolveProperties` | Boolean | `false` | Enable `${...}` resolution |
| `resolveLinkedConfig`| Boolean | `false` | Resolve from linked CONFIG project |
| `includeLinkedConfig`| Boolean | `false` | Scan files in linked CONFIG project |

## Configuration Examples

### Example 1: Optional Feature Flags

Ensure that if a feature toggle is present, it uses valid boolean string values.

```yaml
- id: "RULE-OPTIONAL-TOGGLE"
  name: "Validate Optional Toggles"
  severity: MEDIUM
  errorMessage: "Optional toggle validation failed.\n{DEFAULT_MESSAGE}"
  checks:
    - type: OPTIONAL_PROPERTY_VALUE_CHECK
      params:
        fileExtensions: [".properties"]
        environments: ["ALL"]
        properties:
          - name: "feature.beta.enabled"
            values: ["true", "false"]
```

### Example 2: Optional Cache Tuning

Restrict choices for cache providers and durations when developers choose to override defaults.

```yaml
- id: "RULE-OPTIONAL-CACHE"
  name: "Optional Cache Settings"
  severity: LOW
  checks:
    - type: OPTIONAL_PROPERTY_VALUE_CHECK
      params:
        fileExtensions: [".properties"]
        environments: ["DEV", "QA", "PROD"]
        properties:
          - name: "cache.provider"
            values: ["memory", "redis", "in-memory"]
          - name: "cache.timeout"
            values: ["300", "600", "3600"]
```

## Validation Logic

- **✅ PASS**: Property is NOT found in the configuration file (since it is optional).
- **✅ PASS**: Property is found and its value matches one of the allowed `values`.
- **❌ FAIL**: Property is found but its value is NOT in the allowed list.

## Error Messages

```text
dev.properties: Optional property 'cache.provider' found but value 'custom-val' does not match allowed choices [memory, redis, in-memory].
```

## Best Practices

- **Use for "Nice to Have" Rules**: This is ideal for validating developer-facing flags or performance tuning parameters where a default exists in code if the property is missing.
- **Environment Variance**: Even for optional properties, you can restrict `PROD` to a subset of `DEV` values (e.g., only allowing certain cache providers in a production cloud environment).
- **Documentation**: Use the `values` list to document allowed types for other team members.

## Related Rule Types

- **[MANDATORY_PROPERTY_VALUE_CHECK](CONFIG_MANDATORY_PROPERTY_VALUE_CHECK.md)** - For properties that MUST be present.
- **[MANDATORY_SUBSTRING_CHECK](CONFIG_MANDATORY_SUBSTRING_CHECK.md)** - For validating parts of property values.
