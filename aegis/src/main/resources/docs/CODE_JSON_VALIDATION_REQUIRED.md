# JSON_VALIDATION_REQUIRED

**Rule Type:** `CODE` - **Applies To:** JSON configuration files (package.json, etc.)

## Overview

Validates that **required JSON elements exist** in JSON files. This rule is essential for validating `mule-artifact.json` and other configuration files. It supports **version checking**, **specific field value validation**, and **field existence checks**.

## Use Cases

- Validate `mule-artifact.json` structure and Mule runtime versions
- Ensure required configuration keys exist in custom JSON config files
- Enforce specific values for critical JSON fields (e.g., javaVersion)

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePattern` | String | Precise filename to match (e.g., `mule-artifact.json`) |

### Validation Parameters (Must configure at least one)

| Parameter | Type | Description |
|-----------|------|-------------|
| `minVersions` | Map | Checks if a field's version string is >= specified version (e.g., "4.4.0") |
| `requiredFields` | Map | Checks if a field exists AND has a specifically required value |
| `requiredElements` | List | Checks if a field simply exists (value ignored) |

## Configuration Examples

### Example 1: Full Mule Artifact Validation
Validates minimum Mule version, required Java version, and presence of secure properties.

```yaml
- id: "RULE-007"
  name: "Validate mule-artifact.json"
  description: "Ensures mule-artifact.json meets minimum runtime and security requirements"
  enabled: true
  severity: HIGH
  checks:
    - type: JSON_VALIDATION_REQUIRED
      params:
        filePattern: "mule-artifact.json"
        
        # Check minimum version (Numeric comparison)
        minVersions:
          minMuleVersion: "4.9.0"
          
        # Check exact values
        requiredFields:
          javaSpecificationVersions: "17"
          
        # Check simple existence
        requiredElements:
          - "secureProperties"
          - "classLoaderModelLoaderDescriptor"
```

### Example 2: Simple Config Existence

```yaml
- id: "RULE-101"
  name: "App Config Required Fields"
  enabled: true
  checks:
    - type: JSON_VALIDATION_REQUIRED
      params:
        filePattern: "app-config.json"
        requiredElements:
          - "appName"
          - "environment"
```

## Error Messages

```
Field 'minMuleVersion' version too low in mule-artifact.json: expected >= 4.9.0, got 4.4.0
Field 'javaSpecificationVersions' has wrong value in mule-artifact.json: expected '17', got '8'
Element 'secureProperties' missing in mule-artifact.json
```


## Best Practices

### When to Use This Rule
-  Validating mule-artifact.json for runtime compatibility
-  Ensuring minimum Mule/Java versions for security
-  Enforcing required configuration fields
-  Standardizing JSON configuration structure

### Common Patterns
```yaml
# Enforce minimum Mule runtime version
minVersions:
  minMuleVersion: "4.9.0"

# Require specific Java version
requiredFields:
  javaSpecificationVersions: "17"
```
## Related Rule Types

- **[JSON_VALIDATION_FORBIDDEN](JSON_VALIDATION_FORBIDDEN.md)** - Opposite: ensures elements do NOT exist
