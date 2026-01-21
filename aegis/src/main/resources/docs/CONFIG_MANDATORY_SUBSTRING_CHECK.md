# MANDATORY_SUBSTRING_CHECK

**Rule Type:** `CONFIG` | **Config Engine** | `.properties`, `.yaml`

## Overview

Validates that **required or forbidden substrings exist** in environment-specific configuration files. This rule is optimized for filtering configuration files by their environment name (e.g., `DEV.properties`, `PROD.yaml`), making it an essential tool for cross-environment compliance.

## Use Cases

- Ensure critical configuration keys (like `api.key`) exist in all environment descriptors.
- Prevent unsafe settings (e.g., `debug=true`) from reaching production configurations.
- Enforce mandatory protocol usage (e.g., `https://` in callback URLs).
- Validate naming conventions or standard placeholders across different build stages.

## Parameters

### Required Parameters

| Parameter | Type | Description |
| :--- | :--- | :--- |
| `fileExtensions` | List | File extensions to scan (e.g., `.properties`, `.yaml`) |
| `tokens` | List | List of substrings to search for |
| `environments` | List | Environment keys (file basenames) to validate (e.g., `PROD`, `ALL`) |

### Optional Parameters

| Parameter | Type | Default | Description |
| :--- | :--- | :--- |
| `searchMode` | String | `REQUIRED` | Choose `REQUIRED` or `FORBIDDEN` |
| `caseSensitive` | Boolean | `true` | Whether substring matching is case-sensitive |
| `resolveProperties` | Boolean | `false` | Enable `${...}` resolution |
| `resolveLinkedConfig`| Boolean | `false` | Resolve from linked CONFIG project |
| `includeLinkedConfig`| Boolean | `false` | Scan files in linked CONFIG project |
| `message` | String | (Default) | Custom message to display on failure |
| `message` | String | (Default) | Custom message to display on failure |

## Configuration Examples

### Example 1: Required Service Discovery Configuration
Ensure that all environment property files contain the mandatory service ID and client identification.

```yaml
- id: "RULE-CONFIG-DISCOVERY"
  name: "Required Service Identifiers"
  severity: HIGH
  checks:
    - type: MANDATORY_SUBSTRING_CHECK
      params:
        fileExtensions: [".properties"]
        environments: ["DEV", "QA", "PROD"]
        tokens: ["service.id", "service.owner"]
        searchMode: REQUIRED
```

### Example 2: Block Insecure Production Flags
Prevent "debug" or "verbose" flags from being enabled in production configuration files.

```yaml
- id: "RULE-PROD-NO-DEBUG"
  name: "Disable Debug in Production"
  severity: CRITICAL
  checks:
    - type: MANDATORY_SUBSTRING_CHECK
      params:
        fileExtensions: [".properties", ".yaml"]
        environments: ["PROD"]
        tokens: ["debug=true", "verbose.logging=on"]
        searchMode: FORBIDDEN
```

### Example 3: Mandatory Protocol Enforcement
Ensure that all environments reference security components using standard case-insensitive keywords.

```yaml
- id: "RULE-SECURE-KEYWORDS"
  name: "Mandatory Security Keywords"
  severity: HIGH
  checks:
    - type: MANDATORY_SUBSTRING_CHECK
      params:
        fileExtensions: [".properties", ".yaml"]
        environments: ["ALL"]
        tokens: ["ssl", "tls", "https"]
        searchMode: REQUIRED
        caseSensitive: false
```

## Error Configuration & Customization

By default, Aegis generates a technical description of the failure. You can override this by providing a custom `message` parameter in your rule configuration.

```yaml
      params:
        tokens: ["debug=true"]
        searchMode: FORBIDDEN
        message: "Insecure debug flag found! Please disable for production."
```

## Best Practices

- **Environment Mapping**: This rule relies on matching the `environments` list against the filenames. Ensure your configuration files follow a consistent naming pattern (e.g., `ENV_NAME.properties`).
- **Global Enforcement**: Use `environments: ["ALL"]` to apply a rule across every discovered environment file automatically.
- **Substrings vs. Full Values**: Remember that this rule checks for *substrings*. If you need to validate that a property precisely equals a value, use **[MANDATORY_PROPERTY_VALUE_CHECK](CONFIG_MANDATORY_PROPERTY_VALUE_CHECK.md)**.

## Related Rule Types

- **[GENERIC_TOKEN_SEARCH](CODE_GENERIC_TOKEN_SEARCH.md)** - Similar logic but without environment-specific file filtering.
- **[MANDATORY_PROPERTY_VALUE_CHECK](CONFIG_MANDATORY_PROPERTY_VALUE_CHECK.md)** - Validates exact key-value pairs.
