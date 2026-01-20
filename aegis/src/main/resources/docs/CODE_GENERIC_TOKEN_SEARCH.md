# GENERIC_TOKEN_SEARCH

**Primary Capacity:** Universal Pattern & Token Engine | **Validation Target:** Any Text-Based File (Scripts, XML, Java, YAML)

## Overview

Advanced token search with **environment filtering** and **regex support**. This rule acts as a versatile engine that can operate in both `REQUIRED` and `FORBIDDEN` modes, with additional awareness of target environments. It is the core engine behind more specialized substring and token checks.

## Use Cases

- Detect environment-specific anti-patterns (e.g., "localhost" in Production).
- Enforce mandatory headers or license blocks in source files.
- Validate configuration compliance across different build stages.
- Target specific XML element attributes or plain text strings.

## Parameters

### Required Parameters

| Parameter | Type | Description |
| :--- | :--- | :--- |
| `filePatterns` | List | Glob patterns to match source or configuration files |
| `tokens` | List | List of tokens or patterns to search for |

### Optional Parameters

| Parameter | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `environments` | List | N/A | Filter files by environment keys (e.g., `PROD`, `QA`) |
| `searchMode` | String | `FORBIDDEN` | Choose `REQUIRED` or `FORBIDDEN` |
| `matchMode` | String | `SUBSTRING` | Choose `SUBSTRING`, `REGEX`, or `ELEMENT_ATTRIBUTE`. Setting to `REGEX` automatically enables regex matching. |
| `caseSensitive` | Boolean | `true` | Whether token matching is case-sensitive |
| `wholeWord` | Boolean | `false` | If `true`, ensures exact word matching (wraps tokens in `\b`). Ignored if `matchMode: REGEX`. |
| `message` | String | (Default) | Custom message to display on failure |
| `elementName` | String | N/A | For XML element-specific attribute searches |

## Configuration Examples

### Example 1: Environment-Specific Policy

Ensure that production configurations do not contain references to local development servers.

```yaml
- id: "RULE-PROD-NO-LOCALHOST"
  name: "No Localhost in Production"
  severity: CRITICAL
  checks:
    - type: GENERIC_TOKEN_SEARCH
      params:
        filePatterns: ["*.properties", "*.yaml"]
        environments: ["PROD"]
        tokens: ["localhost", "127.0.0.1"]
        searchMode: FORBIDDEN
```

### Example 2: Detect IP Addresses

Identify hardcoded IP addresses in configuration files for higher environments.

```yaml
- id: "RULE-NOMADIC-IP-CHECK"
  name: "IP Address Detection"
  severity: HIGH
  checks:
    - type: GENERIC_TOKEN_SEARCH
      params:
        filePatterns: ["config/*.yaml"]
        environments: ["QA", "PROD"]
        tokens: ["\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"]
        matchMode: REGEX
        searchMode: FORBIDDEN
```

### Example 3: Mandatory Cloud Metadata

Ensure all property files in any environment contain a specific metadata reference.

```yaml
- id: "RULE-MANDATORY-CLOUDFLARE"
  name: "Cloud Providers Metadata"
  checks:
    - type: GENERIC_TOKEN_SEARCH
      params:
        filePatterns: ["**/*.properties"]
        environments: ["ALL"]
        tokens: ["${cloud.provider}", "${region.id}"]
        searchMode: REQUIRED
```

## Error Configuration & Customization

By default, Aegis generates a technical description of the failure. You can override this by providing a custom `message` parameter in your rule configuration.

```yaml
      params:
        tokens: ["localhost"]
        searchMode: FORBIDDEN
        message: "Critical violation: Hardcoded localhost reference found in production config!"
```

## Best Practices

- **Filter by Environment**: Use the `environments` parameter to avoid noise in development or test configurations while keeping production strictly compliant.
- **Regex for Complexity**: Use `matchMode: REGEX` to search for patterns rather than literal strings (e.g., version formats, ID patterns).
- **Mode Clarity**: Clearly label whether a rule is for blocking (`FORBIDDEN`) or enforcing (`REQUIRED`) to make failure messages intuitive.

## Related Rule Types

- **[GENERIC_TOKEN_SEARCH_REQUIRED](GENERIC_TOKEN_SEARCH_REQUIRED.md)** - Simplified mandatory token check.
- **[GENERIC_TOKEN_SEARCH_FORBIDDEN](GENERIC_TOKEN_SEARCH_FORBIDDEN.md)** - Simplified forbidden token check.

## Solution Patterns and Technology Reference

Standard configurations for validating multi-technology stacks.

| Technology | Scenario | Search Mode | Target File |
| :--- | :--- | :--- | :--- |
| **☕ Java** | Block `System.out` | `FORBIDDEN` | `*.java` |
| **🐍 Python** | Block `print()` | `FORBIDDEN` | `*.py` |
| **📦 Node.js** | Block `console.log` | `FORBIDDEN` | `*.js` |
| **🐳 Docker** | Require standard base image | `REQUIRED` | `Dockerfile` |
| **🐎 MuleSoft** | Block non-persistent stores | `FORBIDDEN` | `*.xml` |

### ☕ Java Patterns

Enterprise standards for logging and output management.

```yaml
- id: "JAVA-LOG-CLEAN"
  name: "No System.out Usage"
  checks:
    - type: GENERIC_TOKEN_SEARCH
      params:
        filePatterns: ["src/**/*.java"]
        tokens: ["System.out.print", "System.err.print"]
        searchMode: FORBIDDEN
```

### 🐍 Python Patterns

Ensuring logging standards in production scripts.

```yaml
- id: "PY-LOG-ENFORCEMENT"
  name: "No Print Statements"
  checks:
    - type: GENERIC_TOKEN_SEARCH
      params:
        filePatterns: ["*.py"]
        tokens: ["print("]
        searchMode: FORBIDDEN
```
