# MANDATORY_SUBSTRING_CHECK

## Overview

Validates that **required or forbidden tokens exist** in environment-specific configuration files. This is a config-specific rule that filters files by environment name.

## Use Cases

- Ensure required configuration keys exist in environment files
- Prevent forbidden values in specific environments
- Validate environment-specific property files
- Enforce configuration standards across environments

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `fileExtensions` | List<String> | File extensions to check (e.g., `.properties`, `.yaml`) |
| `tokens` | List<String> | List of tokens to search for |
| `environments` | List<String> | Environment names (file basenames) to check |

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `searchMode` | String | `REQUIRED` | `REQUIRED` (tokens must exist) or `FORBIDDEN` (tokens must NOT exist) |
| `caseSensitive` | Boolean | `true` | Whether token matching is case-sensitive |

## Configuration Examples

### Example 1: Ensure Required Properties

```yaml
- id: "RULE-120"
  name: "Required API Configuration"
  description: "All environment property files must have apiId"
  enabled: true
  severity: HIGH
  checks:
    - type: MANDATORY_SUBSTRING_CHECK
      params:
        fileExtensions: [".properties"]
        environments: ["DEV", "QA", "PROD"]
        tokens: ["apiId", "clientId"]
        searchMode: REQUIRED
```

### Example 2: Block Forbidden Values

```yaml
- id: "RULE-121"
  name: "No Debug Mode in Production"
  description: "Production properties must not have debug=true"
  enabled: true
  severity: CRITICAL
  checks:
    - type: MANDATORY_SUBSTRING_CHECK
      params:
        fileExtensions: [".properties"]
        environments: ["PROD"]
        tokens: ["debug=true", "verbose=true"]
        searchMode: FORBIDDEN
```

### Example 3: Case-Insensitive Search

```yaml
- id: "RULE-122"
  name: "SSL Configuration Required"
  description: "All environments must have SSL configuration"
  enabled: true
  severity: HIGH
  checks:
    - type: MANDATORY_SUBSTRING_CHECK
      params:
        fileExtensions: [".properties", ".yaml"]
        environments: ["ALL"]  # Will be expanded to global environments list
        tokens: ["ssl", "tls", "https"]
        searchMode: REQUIRED
        caseSensitive: false
```

## Error Messages

```
DEV.properties: Required token 'apiId' not found in file
PROD.properties: Forbidden token 'debug=true' found in file
```

## Related Rule Types

- **[GENERIC_TOKEN_SEARCH_REQUIRED](GENERIC_TOKEN_SEARCH_REQUIRED.md)** - More flexible token search without environment filtering
- **[MANDATORY_PROPERTY_VALUE_CHECK](MANDATORY_PROPERTY_VALUE_CHECK.md)** - Validates property name-value pairs
