# GENERIC_TOKEN_SEARCH_FORBIDDEN

## Overview

Validates that **forbidden tokens do NOT exist** in files matching specified patterns. This rule **fails** if any forbidden token **IS found**.

## Use Cases

- Prevent usage of deprecated APIs or functions
- Block hardcoded credentials or sensitive data
- Disallow specific libraries or imports
- Enforce coding standards by blocking anti-patterns

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePatterns` | List<String> | Glob patterns to match files (e.g., `**/*.xml`, `src/main/mule/*.xml`) |
| `tokens` | List<String> | List of tokens that must NOT be found |

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `excludePatterns` | List<String> | `[]` | Glob patterns to exclude files |
| `matchMode` | String | `SUBSTRING` | `SUBSTRING` or `REGEX` - how to match tokens |
| `caseSensitive` | Boolean | `true` | Whether token matching is case-sensitive |

## Configuration Examples

### Example 1: Block Deprecated Function

```yaml
- id: "RULE-010"
  name: "No Deprecated toBase64 Function"
  description: "Prevent usage of deprecated toBase64() function"
  enabled: true
  severity: HIGH
  checks:
    - type: GENERIC_TOKEN_SEARCH_FORBIDDEN
      params:
        filePatterns:
          - "**/*.dwl"
        tokens:
          - "toBase64()"
```

### Example 2: Block Hardcoded Credentials

```yaml
- id: "RULE-011"
  name: "No Hardcoded Passwords"
  description: "Prevent hardcoded passwords in configuration files"
  enabled: true
  severity: CRITICAL
  checks:
    - type: GENERIC_TOKEN_SEARCH_FORBIDDEN
      params:
        filePatterns:
          - "**/*.xml"
          - "**/*.properties"
        tokens:
          - "password="
          - "pwd="
          - "secret="
        caseSensitive: false
```

### Example 3: Block IP Addresses (Regex)

```yaml
- id: "RULE-012"
  name: "No Hardcoded IP Addresses"
  description: "Prevent hardcoded IP addresses in code"
  enabled: true
  severity: HIGH
  checks:
    - type: GENERIC_TOKEN_SEARCH_FORBIDDEN
      params:
        filePatterns:
          - "src/main/mule/**/*.xml"
        excludePatterns:
          - "**/test/**"
        tokens:
          - "\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"  # IP address pattern
        matchMode: REGEX
```

### Example 4: Block Multiple Deprecated APIs

```yaml
- id: "RULE-013"
  name: "No Deprecated Mule Components"
  description: "Block usage of deprecated Mule 3 components"
  enabled: true
  severity: MEDIUM
  checks:
    - type: GENERIC_TOKEN_SEARCH_FORBIDDEN
      params:
        filePatterns:
          - "**/*.xml"
        tokens:
          - "http://www.mulesoft.org/schema/mule/http-deprecated"
          - "mule-transport-"
          - "endpoint-ref"
```

## Error Messages

When validation fails, you'll see messages like:

```
config.xml has forbidden token: toBase64()
application.properties has forbidden token: password=
main.xml has forbidden token: 192.168.1.1
```

## Related Rule Types

- **[GENERIC_TOKEN_SEARCH_REQUIRED](GENERIC_TOKEN_SEARCH_REQUIRED.md)** - Opposite: ensures tokens DO exist
- **[XML_XPATH_NOT_EXISTS](XML_XPATH_NOT_EXISTS.md)** - More precise XML validation using XPath
- **[MANDATORY_SUBSTRING_CHECK](MANDATORY_SUBSTRING_CHECK.md)** - Config-specific validation with searchMode: FORBIDDEN
