# JSON_VALIDATION_FORBIDDEN

**Rule Type:** `CODE` - **Applies To:** JSON configuration files (package.json, config.json, etc.)

## Overview

Validates that **forbidden JSON elements do NOT exist** in JSON files. This rule **fails** if forbidden elements **ARE found**. It is useful for blocking deprecated fields, insecure settings, or unapproved metadata.

## Use Cases

- Prevent usage of deprecated configuration keys in project descriptors.
- Block sensitive data fields (e.g., `password`, `secret`) in configuration JSON.
- Disallow certain unapproved metadata or development-only flags in production JSON.
- Enforce strict JSON structure by restricting specific keys.

### Global Settings (Optional)

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `resolveProperties` | Boolean | `false` | Enable `${...}` resolution |
| `resolveLinkedConfig`| Boolean | `false` | Resolve from linked CONFIG project |
| `includeLinkedConfig`| Boolean | `false` | Scan files in linked CONFIG project |

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePattern` | String | Filename pattern to match (e.g., `mule-artifact.json`, `package.json`) |

### Forbidden Parameters (At least one required)

| Parameter | Type | Description |
|-----------|------|-------------|
| **`forbiddenElements`** | List<String> | List of forbidden JSON keys (top-level or simple nested paths). Fails if **Exists**. |
| **`forbiddenFields`** | Map | Fails if field **Matches Value** (String equality). Logic: `Actual == ForbiddenValue` -> Fail. |
| **`forbiddenVersions`** | Map | Fails if field **Matches Version** (SemVer equality). Logic: `Actual == ForbiddenVersion` -> Fail. |

## Configuration Examples

### Example 1: Block Deprecated Project Fields
Prevents usage of legacy configuration fields in project descriptors.

```yaml
- id: "RULE-JSON-FORBIDDEN-01"
  name: "No Deprecated Project Fields"
  description: "Prevent usage of legacy fields in project descriptors"
  enabled: true
  severity: MEDIUM
  checks:
    - type: JSON_VALIDATION_FORBIDDEN
      params:
        filePattern: "project-descriptor.json"
        forbiddenElements:
          - "legacyMode"
          - "deprecatedConfig"
```

### Example 2: Block Sensitive Fields in Node.js
Ensures `package.json` does not contain sensitive or development-only keys.

```yaml
- id: "RULE-JSON-FORBIDDEN-NODE"
  name: "Clean Package JSON"
  description: "Prevent unapproved keys in package.json"
  enabled: true
  severity: HIGH
  checks:
    - type: JSON_VALIDATION_FORBIDDEN
      params:
        filePattern: "package.json"
        forbiddenElements:
          - "privateRepositoryUrl"
        forbiddenFields:
          "scripts/test": "echo 'no test'" # Forbid dummy test scripts
        forbiddenVersions:
          "engines/node": "14.17.0"      # Forbid a specific known-vulnerable version
```

## Error Messages

```
project-descriptor.json has forbidden element: legacyMode
Forbidden field value found 'scripts/test': 'echo 'no test''
Forbidden version found for 'engines/node': '14.17.0'
```

## Best Practices

### When to Use This Rule
- **Deprecation**: Use it to enforce a "hard cut-over" when migrating from old config formats to new ones.
- **Security**: Prevent hardcoded secrets or insecure flags (like `allowInsecure: true`) from being committed.
- **Cleanliness**: Keep project descriptors streamlined by blocking unapproved experimental fields.

#### GLOBAL Parameters (Optional)

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `resolveProperties` | Boolean | `false` | Enable `${...}` resolution |
| `resolveLinkedConfig`| Boolean | `false` | Resolve from linked CONFIG project |
| `includeLinkedConfig`| Boolean | `false` | Scan files in linked CONFIG project |

---

## Related Rule Types

- **[JSON_VALIDATION_REQUIRED](JSON_VALIDATION_REQUIRED.md)** - Opposite: ensures elements DO exist.
- **[GENERIC_TOKEN_SEARCH_FORBIDDEN](GENERIC_TOKEN_SEARCH_FORBIDDEN.md)** - More flexible search for forbidden patterns across any file type.
