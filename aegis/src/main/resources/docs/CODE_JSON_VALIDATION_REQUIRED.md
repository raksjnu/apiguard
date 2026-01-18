# JSON_VALIDATION_REQUIRED

**Rule Type:** `CODE` - **Applies To:** JSON configuration files (package.json, etc.)

## Overview

Validates that **required JSON elements exist** in JSON files. This rule is essential for validating project descriptors like `mule-artifact.json`, `package.json`, or custom configuration files. It supports **version checking**, **specific field value validation**, and **field existence checks**.

## Use Cases

- Validate `mule-artifact.json` structure and runtime versions.
- Ensure `package.json` contains required scripts or engines configuration.
- Ensure required configuration keys exist in custom JSON config files.
- Enforce specific values for critical JSON fields (e.g., `javaSpecificationVersions`).

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePattern` | String | Precise filename to match (e.g., `mule-artifact.json`, `package.json`) |

### Validation Parameters (Must configure at least one)

| Parameter | Type | Description |
|-----------|------|-------------|
| `minVersions` | Map | Checks if a field's value meets a minimum version (Supports Arrays). Logic: `Actual >= Min`. If array: Pass if *any* element >= Min. |
| `requiredFields` | Map | Checks if a field matches an expected value (Supports Arrays). Logic: `Actual == Expected`. If array: Pass if *any* element matches (String equality or SemVer). |
| `requiredElements` | List | Checks if a field simply exists (value ignored) |

## Configuration Examples

### Example 1: Project Descriptor Validation (MuleSoft)
Validates minimum runtime version, required Java version, and presence of secure properties.

```yaml
- id: "RULE-JSON-MULE"
  name: "Validate Mule Project Descriptor"
  description: "Ensures mule-artifact.json meets minimum runtime requirements"
  enabled: true
  severity: HIGH
  checks:
    - type: JSON_VALIDATION_REQUIRED
      params:
        filePattern: "mule-artifact.json"
        minVersions:
          minMuleVersion: "4.9.0"
        requiredFields:
          javaSpecificationVersions: "17"
        requiredElements:
          - "secureProperties"
```

### Example 2: Node.js / TypeScript Package Validation
Ensures the project has a start script and requires a minimum Node engine.

```yaml
- id: "RULE-JSON-NODE"
  name: "Validate Node.js Package"
  description: "Ensures package.json has required scripts and engines"
  enabled: true
  severity: HIGH
  errorMessage: "Package validation failed.\n{DEFAULT_MESSAGE}"
  checks:
    - type: JSON_VALIDATION_REQUIRED
      params:
        filePattern: "package.json"
        requiredFields:
          scripts/start: "node index.js"
        minVersions:
          engines/node: "18.0.0"
        requiredElements:
          - "dependencies"
          - "license"
```

## Error Messages

```
Field 'minMuleVersion' version too low in mule-artifact.json: expected >= 4.9.0, got 4.4.0
Field 'engines/node' version too low in package.json: expected >= 18.0.0, got 14.17.0
Element 'secureProperties' missing in mule-artifact.json
```


## Best Practices

### When to Use This Rule
-  Validating project descriptors for runtime compatibility.
-  Ensuring minimum platform/engine versions for security.
-  Enforcing required configuration fields in application JSON.
-  Standardizing JSON structure across multiple repositories.
## Related Rule Types

- **[JSON_VALIDATION_FORBIDDEN](JSON_VALIDATION_FORBIDDEN.md)** - Opposite: ensures elements do NOT exist
