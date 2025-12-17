# GENERIC_TOKEN_SEARCH

## Overview

Advanced token search with **environment filtering** and **regex support**. This is the config-specific version of GENERIC_TOKEN_SEARCH_REQUIRED/FORBIDDEN with additional environment awareness.

## Use Cases

- Complex token validation with environment filtering
- Regex-based pattern matching in config files
- Environment-specific validation rules
- Advanced configuration compliance checks

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePatterns` | List<String> | Glob patterns to match files |
| `tokens` | List<String> | List of tokens to search for |

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `environments` | List<String> | `null` | Filter files by environment names |
| `searchMode` | String | `FORBIDDEN` | `REQUIRED` or `FORBIDDEN` |
| `matchMode` | String | `SUBSTRING` | `SUBSTRING`, `REGEX`, or `ELEMENT_ATTRIBUTE` |
| `elementName` | String | `null` | For XML element-specific searches |

## Configuration Examples

### Example 1: Environment-Specific Token Search

```yaml
- id: "RULE-150"
  name: "No Hardcoded Localhost in Production"
  description: "Production configs must not contain localhost"
  enabled: true
  severity: CRITICAL
  checks:
    - type: GENERIC_TOKEN_SEARCH
      params:
        filePatterns: ["*.properties"]
        environments: ["PROD"]
        tokens: ["localhost", "127.0.0.1"]
        searchMode: FORBIDDEN
```

### Example 2: Regex Pattern with Environment Filter

```yaml
- id: "RULE-151"
  name: "IP Address Detection in Config"
  description: "Detect hardcoded IP addresses in QA and PROD configs"
  enabled: true
  severity: HIGH
  checks:
    - type: GENERIC_TOKEN_SEARCH
      params:
        filePatterns: ["*.yaml", "*.properties"]
        environments: ["QA", "PROD"]
        tokens: ["\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"]
        matchMode: REGEX
        searchMode: FORBIDDEN
```

### Example 3: Required Token in All Environments

```yaml
- id: "RULE-152"
  name: "API Key Configuration Required"
  description: "All environment configs must reference API key property"
  enabled: true
  severity: HIGH
  checks:
    - type: GENERIC_TOKEN_SEARCH
      params:
        filePatterns: ["*.properties"]
        environments: ["ALL"]
        tokens: ["${api.key}", "${secure::api.key}"]
        searchMode: REQUIRED
```

## Error Messages

```
PROD.properties: Forbidden token 'localhost' found in file
QA.yaml: Required token(s) not found in files matching: *.yaml
```

## Related Rule Types

- **[GENERIC_TOKEN_SEARCH_REQUIRED](GENERIC_TOKEN_SEARCH_REQUIRED.md)** - Code-specific version
- **[GENERIC_TOKEN_SEARCH_FORBIDDEN](GENERIC_TOKEN_SEARCH_FORBIDDEN.md)** - Code-specific version
- **[MANDATORY_SUBSTRING_CHECK](MANDATORY_SUBSTRING_CHECK.md)** - Simpler config-specific validation
