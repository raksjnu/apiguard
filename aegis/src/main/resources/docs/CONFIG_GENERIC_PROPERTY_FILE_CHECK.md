# GENERIC_PROPERTY_FILE_CHECK

**Primary Capacity:** Structural XML Analysis | **Validation Target:** XML, POM, Config Archive

## Overview

A versatile engine for validating property-based configuration files across different environments. It allows for complex validation logic including mandatory keys, forbidden values, and pattern-based format enforcement.

## Use Cases

- Enforce standard property naming across microservices.
- Ensure all environments have matching configuration keys (prevent "forgot to update PROD").
- Block hardcoded secrets or environment-specific triggers in production.
- Validate the format of critical configuration values (e.g., ports, timeouts, URLs).

## Parameters

### Required Parameters

| Parameter | Type | Description |
| :--- | :--- | :--- |
| `fileExtensions` | List | File extensions to scan (e.g., `.properties`, `.yaml`) |
| `environments` | List | Environment keys to target (e.g., `DEV`, `QA`, `PROD` or `ALL`) |
| `validationRules` | List<Map> | A collection of validation logic blocks |

### Validation Rule Map

| Field | Type | Description |
| :--- | :--- | :--- |
| `type` | String | `REQUIRED`, `FORBIDDEN`, or `FORMAT` |
| `pattern` | String | The key or regex pattern to evaluate |
| `message` | String | Custom message to display on failure |

## Configuration Examples

### Example 1: Mandatory Boilerplate Properties

Ensure that every property file contains the core application identification keys.

```yaml
- id: "RULE-CONFIG-BOILERPLATE"
  name: "Require App Metadata"
  severity: MEDIUM
  checks:
    - type: GENERIC_PROPERTY_FILE_CHECK
      params:
        fileExtensions: [".properties"]
        environments: ["ALL"]
        validationRules:
          - type: "REQUIRED"
            pattern: "app.id"
          - type: "REQUIRED"
            pattern: "app.version"
```

### Example 2: Type and Format Enforcement

Validate that technical configurations like ports and timeouts follow specific numeric or unit-based formats.

```yaml
- id: "RULE-CONFIG-TECHNICAL"
  name: "Technical Format Validation"
  severity: HIGH
  checks:
    - type: GENERIC_PROPERTY_FILE_CHECK
      params:
        fileExtensions: [".properties", ".yaml"]
        environments: ["DEV", "QA", "PROD"]
        validationRules:
          - type: "FORMAT"
            pattern: "http.port=\\d+"
            message: "Http port must be a numeric integer"
          - type: "FORMAT"
            pattern: "db.timeout=\\d+[ms]"
            message: "Database timeout must include 'ms' unit"
```

### Example 3: Production Security Guardrails
Block the usage of insecure protocols or hardcoded local development strings in production.

```yaml
- id: "RULE-PROD-SECURITY-KEYS"
  name: "Production Access Guard"
  severity: CRITICAL
  checks:
    - type: GENERIC_PROPERTY_FILE_CHECK
      params:
        fileExtensions: [".properties", ".yaml"]
        environments: ["PROD"]
        validationRules:
          - type: "FORBIDDEN"
            pattern: "auth.mode=local"
            message: "Production must use SSO or external auth"
          - type: "FORBIDDEN"
            pattern: "debug.enabled=true"
            message: "Debug mode is strictly forbidden in production"
```

## Error Configuration & Customization

By default, Aegis generates a technical description of the failure. You can override this by providing a custom `message` parameter in your rule configuration.

```yaml
      params:
        property: "api.key"
        mode: EXISTS
        message: "Security violation: Missing mandatory API Key in property file!"
```

qa-env.properties: Production must use SSO (found 'auth.mode=local').

## Best Practices

- **Atomic Rules**: Keep `validationRules` focused on specific areas (e.g., separate security rules from formatting rules) for cleaner error reporting.
- **Cross-Environment Consistency**: Use `environments: ["ALL"]` to ensure that if a property is added to `DEV`, it is also present (even if empty/placeholder) in `PROD`.
- **Regex Boundaries**: Use anchors (`^`, `$`) in your patterns if you want to match the whole key or value exactly.

## Related Rule Types

- **[MANDATORY_PROPERTY_VALUE_CHECK](CONFIG_MANDATORY_PROPERTY_VALUE_CHECK.md)** - Deep validation of property values.
- **[MANDATORY_SUBSTRING_CHECK](CONFIG_MANDATORY_SUBSTRING_CHECK.md)** - Simpler substring checks for config files.
- **[GENERIC_TOKEN_SEARCH](CODE_GENERIC_TOKEN_SEARCH.md)** - Advanced text-based search.
