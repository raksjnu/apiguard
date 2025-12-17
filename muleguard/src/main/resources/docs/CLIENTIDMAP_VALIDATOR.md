# CLIENTIDMAP_VALIDATOR

## Overview

Specialized validator for **client ID mapping** and **secure property** validation. Ensures client ID mappings follow specific patterns and secure properties are properly configured.

## Use Cases

- Validate client ID mapping format
- Ensure secure property references
- Check API key configurations
- Enforce security standards for credentials

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `fileExtensions` | List<String> | File extensions to check (typically `.properties`) |
| `environments` | List<String> | Environment names to validate |

## Built-in Validations

This rule performs the following checks automatically:

1. **Client ID Format**: Validates `clientId` properties match expected patterns
2. **Secure Properties**: Ensures sensitive properties use `${secure::...}` syntax
3. **API Key Format**: Validates API key references follow standards
4. **Credential Patterns**: Checks for proper credential configuration

## Configuration Examples

### Example 1: Basic Client ID Validation

```yaml
- id: "RULE-160"
  name: "Client ID Mapping Validation"
  description: "Validate client ID mappings in all environments"
  enabled: true
  severity: HIGH
  checks:
    - type: CLIENTIDMAP_VALIDATOR
      params:
        fileExtensions: [".properties"]
        environments: ["DEV", "QA", "PROD"]
```

### Example 2: Production-Only Validation

```yaml
- id: "RULE-161"
  name: "Production Client ID Security"
  description: "Strict client ID validation for production"
  enabled: true
  severity: CRITICAL
  checks:
    - type: CLIENTIDMAP_VALIDATOR
      params:
        fileExtensions: [".properties"]
        environments: ["PROD"]
```

## Validation Rules

The validator checks for:

- ✅ Proper `clientId` format (alphanumeric with hyphens/underscores)
- ✅ Secure property syntax: `${secure::propertyName}`
- ✅ No hardcoded credentials
- ✅ Valid API key references
- ❌ Plain text passwords or secrets
- ❌ Invalid client ID patterns

## Error Messages

```
PROD.properties: Invalid client ID format detected
DEV.properties: Secure property must use ${secure::...} syntax
QA.properties: Hardcoded credential detected - use secure properties
```

## Related Rule Types

- **[MANDATORY_PROPERTY_VALUE_CHECK](MANDATORY_PROPERTY_VALUE_CHECK.md)** - General property validation
- **[MANDATORY_SUBSTRING_CHECK](MANDATORY_SUBSTRING_CHECK.md)** - Token-based validation
