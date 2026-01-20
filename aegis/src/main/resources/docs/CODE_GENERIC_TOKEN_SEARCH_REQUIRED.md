# GENERIC_TOKEN_SEARCH_REQUIRED

**Rule Type:** `CODE` - **Applies To:** Any text-based source files

## Overview

Validates that **required tokens or patterns exist** in files matching specified patterns. This rule **fails** if any of the required tokens are **NOT found**. It is ideal for enforcing mandatory boilerplate, specific imports, or standard configuration keys across different frameworks.

## Use Cases

- Ensure specific imports or modules are present in source files.
- Verify that required configuration keys or property placeholders exist.
- Validate the presence of mandatory legal headers, author tags, or security annotations.
- Check for required framework-specific metadata files or structures.

## Parameters

### Required Parameters

| Parameter | Type | Description |
| :--- | :--- | :--- |
| `filePatterns` | List | Glob patterns to match source or configuration files |
| `tokens` | List | List of tokens that must be found in the files |

### Optional Parameters

| Parameter | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `excludePatterns` | List | `[]` | Glob patterns to exclude specific files |
| `matchMode` | String | `SUBSTRING` | Choose `SUBSTRING` or `REGEX`. Setting to `REGEX` automatically enables regex matching. |
| `caseSensitive` | Boolean | `true` | Whether token matching is case-sensitive |
| `wholeWord` | Boolean | `false` | If `true`, ensures exact word matching (wraps tokens in `\b`). Ignored if `matchMode: REGEX`. |
| `requireAll` | Boolean | `true` | If `true`, ALL tokens must be found. If `false`, at least ONE |

## Configuration Examples

### Example 1: Required Framework Import

Ensure that all implementation files in a project contain the necessary framework imports for logging.

```yaml
- id: "RULE-JAVA-REQD-LOGGER"
  name: "Required Logger Import"
  checks:
    - type: GENERIC_TOKEN_SEARCH_REQUIRED
      params:
        filePatterns: ["src/**/*.java"]
        tokens: ["import org.slf4j.Logger", "import org.slf4j.LoggerFactory"]
        requireAll: true
```

### Example 2: Mandatory Error Handling Pattern

Ensure that application configuration files contain mandatory error handling blocks (either "continue" or "propagate" strategies).

```yaml
- id: "RULE-CONFIG-ERROR-POLICY"
  name: "Required Error Handling"
  severity: HIGH
  checks:
    - type: GENERIC_TOKEN_SEARCH_REQUIRED
      params:
        filePatterns: ["src/main/resources/*.xml"]
        tokens: ["on-error-continue", "on-error-propagate"]
        requireAll: false  # At least one must exist
```

### Example 3: Regex Format Validation

Verify that a version string following semantic versioning exists in the project's build descriptor.

```yaml
- id: "RULE-VERSION-SEMVER"
  name: "Required SemVer Descriptor"
  checks:
    - type: GENERIC_TOKEN_SEARCH_REQUIRED
      params:
        filePatterns: ["**/pom.xml", "**/package.json"]
        tokens: ["\\d+\\.\\d+\\.\\d+"]
        matchMode: REGEX
```

## Error Messages

```text
DatabaseConfig.java: Missing required token: import org.slf4j.Logger
app-descriptor.xml: Missing at least one required token: [on-error-continue, on-error-propagate]
```

## Best Practices

- **Broad File Patterns**: Use this rule at a high level to ensure that *every* file of a certain type adheres to structural requirements (like license headers).
- **Combine with Exclusion**: Use `excludePatterns` to skip generated code, third-party libraries, or test files where these rules might not apply.
- **Fail on Composition**: This rule is most effective when used to ensure that cross-cutting concerns (logging, security, error handling) are at least *present* in the codebase.

## Related Rule Types

- **[GENERIC_TOKEN_SEARCH_FORBIDDEN](GENERIC_TOKEN_SEARCH_FORBIDDEN.md)** - Opposite: ensures tokens do NOT exist.
- **[XML_XPATH_EXISTS](XML_XPATH_EXISTS.md)** - More precise XML validation using structural paths.

## Solution Patterns and Technology Reference

Standard configurations for enforcing mandatory components.

| Technology | Requirement | Mode | Target File |
| :--- | :--- | :--- | :--- |
| **☕ Java** | Security Annotations | `REQUIRED` | `*Controller.java` |
| **🐍 Python** | Shebang Reference | `REQUIRED` | `*.py` |
| **📦 Node.js** | Module Exports | `REQUIRED` | `*.js` |
| **🐎 MuleSoft** | DataWeave Version | `REQUIRED` | `*.dwl` |

### ☕ Java / Spring Boot Patterns

Ensure controllers have standard security annotations.

```yaml
- id: "JAVA-SECURE-CONTROLLER"
  name: "Secure Controllers"
  checks:
    - type: GENERIC_TOKEN_SEARCH_REQUIRED
      params:
        filePatterns: ["**/*Controller.java"]
        tokens: ["@PreAuthorize", "@Secured"]
        requireAll: false
```

### 🐍 Python Patterns

Ensure scripts specify the required python version via shebang.

```yaml
- id: "PY-MIN-VERSION"
  name: "Python 3 Shebang"
  checks:
    - type: GENERIC_TOKEN_SEARCH_REQUIRED
      params:
        filePatterns: ["*.py"]
        tokens: ["#!/usr/bin/env python3"]
```
