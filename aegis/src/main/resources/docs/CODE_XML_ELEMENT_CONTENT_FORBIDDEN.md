# XML_ELEMENT_CONTENT_FORBIDDEN

**Rule Type:** `CODE` - **Applies To:** XML configuration files

## Overview

Validates that XML elements do **NOT contain forbidden content or tokens**. This rule **fails** if any of the specified forbidden tokens are **found** within the element's text content. It is primarily used to block hardcoded credentials, deprecated values, or insecure configurations.

## Use Cases

- Prevent usage of deprecated values or legacy patterns.
- Block hardcoded credentials (passwords, secrets) in element bodies.
- Disallow specific configuration values (e.g., insecure protocols, debug levels).
- Enforce content restrictions for production-ready code.

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePatterns` | List<String> | Glob patterns to match XML files |
| `elementContentPairs` | List<Map> | Element names paired with their forbidden content tokens |

#### Element Content Pair Map Structure

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `element` | String | Yes | XML element name |
| `forbiddenTokens` | List<String> | Yes | Tokens that must NOT be present in the element's body |

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `matchMode` | String | `SUBSTRING` | Choose `SUBSTRING` or `REGEX` |
| `caseSensitive` | Boolean | `true` | Case sensitivity for content matching |

## Configuration Examples

### Example 1: Block Hardcoded Secrets
Ensure that password or secret-related elements do not contain common default or hardcoded values.

```yaml
- id: "RULE-XML-FORBIDDEN-CREDS"
  name: "No Hardcoded Credentials"
  severity: CRITICAL
  checks:
    - type: XML_ELEMENT_CONTENT_FORBIDDEN
      params:
        filePatterns: ["**/*.xml"]
        elementContentPairs:
          - element: "password"
            forbiddenTokens: ["admin", "password123", "default"]
          - element: "secret-key"
            forbiddenTokens: ["hardcoded", "placeholder"]
```

### Example 2: Block Deprecated Log Levels
Prevent usage of overly verbose or deprecated logging levels in production-bound configurations.

```yaml
- id: "RULE-XML-LOG-POLICY"
  name: "Forbidden Log Levels"
  severity: MEDIUM
  checks:
    - type: XML_ELEMENT_CONTENT_FORBIDDEN
      params:
        filePatterns: ["src/main/resources/**/*.xml"]
        elementContentPairs:
          - element: "logger"
            forbiddenTokens: ["TRACE", "ALL", "DEBUG"]
```

### Example 3: Block IP Addresses (Regex)
Ensure that host configurations use domain names instead of hardcoded IP addresses.

```yaml
- id: "RULE-XML-NO-IP-HOSTS"
  name: "No Hardcoded IPs in Hosts"
  severity: HIGH
  checks:
    - type: XML_ELEMENT_CONTENT_FORBIDDEN
      params:
        filePatterns: ["**/*.xml"]
        matchMode: REGEX
        elementContentPairs:
          - element: "host-config"
            forbiddenTokens: ["\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"]
```

## Error Messages

```
config.xml: Element 'password' contains forbidden token: admin
main-flow.xml: Element 'logger' contains forbidden token: TRACE
host-config.xml: Element 'host-config' contains forbidden IP pattern
```

## Best Practices

- **Strict Mode for Credentials**: Use `caseSensitive: true` (default) when blocking passwords to avoid false positives on legitimate similar strings, but consider `false` for logging levels.
- **REGEX for Complexity**: Use REGEX mode to block entire classes of strings, such as IP addresses or specific ID formats.
- **Fail Early**: Apply this rule to core configuration files to catch architectural violations before they reach higher environments.

## Related Rule Types

- **[XML_ELEMENT_CONTENT_REQUIRED](XML_ELEMENT_CONTENT_REQUIRED.md)** - Opposite: ensures tokens ARE present in the content.
- **[XML_XPATH_NOT_EXISTS](XML_XPATH_NOT_EXISTS.md)** - Block complex structures using XPath.
