# GENERIC_TOKEN_SEARCH_REQUIRED

## Overview

Validates that **required tokens exist** in files matching specified patterns. This rule **fails** if any required token is **NOT found**.

## Use Cases

- Ensure specific imports are present in code files
- Verify required configuration keys exist
- Validate presence of mandatory comments or annotations
- Check for required DataWeave functions or variables

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePatterns` | List<String> | Glob patterns to match files (e.g., `**/*.xml`, `src/main/mule/*.xml`) |
| `tokens` | List<String> | List of tokens that must be found |

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `excludePatterns` | List<String> | `[]` | Glob patterns to exclude files |
| `matchMode` | String | `SUBSTRING` | `SUBSTRING` or `REGEX` - how to match tokens |
| `caseSensitive` | Boolean | `true` | Whether token matching is case-sensitive |
| `requireAll` | Boolean | `true` | If `true`, ALL tokens must be found. If `false`, at least ONE token must be found |

## Configuration Examples

### Example 1: Basic - Ensure Required Import Exists

```yaml
- id: "RULE-001"
  name: "Required Logger Import"
  description: "Ensure all XML files import the logger component"
  enabled: true
  severity: MEDIUM
  checks:
    - type: GENERIC_TOKEN_SEARCH_REQUIRED
      params:
        filePatterns:
          - "**/*.xml"
        tokens:
          - "http://www.mulesoft.org/schema/mule/logger"
        caseSensitive: true
```

### Example 2: Advanced - Regex Pattern with OR Logic

```yaml
- id: "RULE-002"
  name: "Required Error Handling"
  description: "Ensure error handling is present (on-error-continue OR on-error-propagate)"
  enabled: true
  severity: HIGH
  checks:
    - type: GENERIC_TOKEN_SEARCH_REQUIRED
      params:
        filePatterns:
          - "src/main/mule/**/*.xml"
        excludePatterns:
          - "**/test/**"
        tokens:
          - "on-error-continue"
          - "on-error-propagate"
        matchMode: SUBSTRING
        requireAll: false  # At least ONE must exist
```

### Example 3: Case-Insensitive Search

```yaml
- id: "RULE-003"
  name: "Required API Documentation"
  description: "Ensure API documentation keywords are present"
  enabled: true
  severity: LOW
  checks:
    - type: GENERIC_TOKEN_SEARCH_REQUIRED
      params:
        filePatterns:
          - "**/*.dwl"
        tokens:
          - "description"
          - "summary"
        caseSensitive: false
        requireAll: false
```

### Example 4: Regex Pattern Matching

```yaml
- id: "RULE-004"
  name: "Required Version Pattern"
  description: "Ensure version number follows semantic versioning"
  enabled: true
  severity: MEDIUM
  checks:
    - type: GENERIC_TOKEN_SEARCH_REQUIRED
      params:
        filePatterns:
          - "**/pom.xml"
        tokens:
          - "\\d+\\.\\d+\\.\\d+"  # Regex: X.Y.Z format
        matchMode: REGEX
```

## Error Messages

When validation fails, you'll see messages like:

```
config.xml is missing required token: http://www.mulesoft.org/schema/mule/logger
main.dwl is missing required token: description
```

## Related Rule Types

- **[GENERIC_TOKEN_SEARCH_FORBIDDEN](GENERIC_TOKEN_SEARCH_FORBIDDEN.md)** - Opposite: ensures tokens do NOT exist
- **[XML_XPATH_EXISTS](XML_XPATH_EXISTS.md)** - More precise XML validation using XPath
- **[MANDATORY_SUBSTRING_CHECK](MANDATORY_SUBSTRING_CHECK.md)** - Config-specific token validation with environment filtering
