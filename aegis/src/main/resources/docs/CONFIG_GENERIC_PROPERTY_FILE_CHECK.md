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
| `requiredFields` | Map<String, String> | Key-value pairs for exact matching (Optional) |
| `minVersions` | Map<String, String> | Key-MinVersion pairs for SemVer check (Optional) |
| `exactVersions` | Map<String, String> | Key-Version pairs for Exact SemVer check (Optional) |
| `resolveProperties` | Boolean | Enable `${...}` resolution |
| `resolveLinkedConfig`| Boolean | Resolve from linked CONFIG project |
| `includeLinkedConfig`| Boolean | Scan files in linked CONFIG project |

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
            message: "Http port must be a numeric integer.\n{DEFAULT_MESSAGE}"
          - type: "FORMAT"
            pattern: "db.timeout=\\d+[ms]"
            message: "Database timeout must include 'ms' unit.\n{DEFAULT_MESSAGE}"
```

### Example 3: Version and Field Validation
Enforce specific versions and exact field values using standard parameters.

```yaml
- id: "RULE-PROP-STANDARDS"
  name: "Standard Property Validation"
  severity: HIGH
  checks:
    - type: GENERIC_PROPERTY_FILE_CHECK
      params:
        fileExtensions: [".properties"]
        environments: ["ALL"]
        requiredFields:
          "app.type": "microservice"
          "deploy.mode": "blue-green"
        minVersions:
          "lib.version": "1.2.0"
        exactVersions:
          "fixed.dep": "3.0.1"
```

### Example 4: Production Security Guardrails
Block the usage of insecure protocols or hardcoded local development strings in production.

```yaml
- id: "RULE-PROD-SECURITY-KEYS"
  name: "Production Access Guard"
  severity: CRITICAL
  errorMessage: "Production security violation found.\n{DEFAULT_MESSAGE}"
  checks:
    - type: GENERIC_PROPERTY_FILE_CHECK
      params:
        fileExtensions: [".properties", ".yaml"]
        environments: ["PROD"]
        validationRules:
          - type: "FORBIDDEN"
            pattern: "auth.mode=local"
            # Overrides the generic error message
            message: "Production must use SSO or external auth.\n{DEFAULT_MESSAGE}"
          - type: "FORBIDDEN"
            pattern: "debug.enabled=true"
```

## Error Configuration & Customization

You can customize error messages at two levels:

1.  **Rule Level (Recommended)**: Use `errorMessage` at the top level for a consistent message across all failures.
2.  **Check/Rule Level**: Use `message` inside `validationRules` to provide specific instructions for a single failure type.

**Both support tokens:**
- `{DEFAULT_MESSAGE}`: Detailed technical reason for the failure.
- `{CORE_DETAILS}`: Alias for default message.

```yaml
      params:
        property: "api.key"
        mode: EXISTS
        message: "Security violation: Missing mandatory API Key!\n{DEFAULT_MESSAGE}"
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
