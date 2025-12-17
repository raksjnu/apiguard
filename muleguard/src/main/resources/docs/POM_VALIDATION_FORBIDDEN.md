# POM_VALIDATION_FORBIDDEN

## Overview

Validates that **forbidden elements do NOT exist** in Maven POM files. This rule **fails** if forbidden POM elements **ARE found**.

## Use Cases

- Prevent deprecated dependencies
- Block specific Maven plugins
- Disallow snapshot versions in production
- Enforce dependency restrictions

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `forbiddenElements` | List<Map> | List of forbidden POM elements with optional value patterns |

#### Forbidden Element Map Structure

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `element` | String | Yes | POM element path |
| `values` | List<String> | No | If specified, only fail if element has one of these values |

## Configuration Examples

### Example 1: Block Deprecated Dependencies

```yaml
- id: "RULE-090"
  name: "No Deprecated Mule Transports"
  description: "Prevent usage of deprecated Mule 3 transports"
  enabled: true
  severity: HIGH
  checks:
    - type: POM_VALIDATION_FORBIDDEN
      params:
        forbiddenElements:
          - element: "dependencies/dependency/artifactId"
            values: ["mule-transport-http", "mule-transport-vm", "mule-transport-jms"]
```

### Example 2: Block Snapshot Versions

```yaml
- id: "RULE-091"
  name: "No Snapshot Dependencies"
  description: "Production builds must not use SNAPSHOT versions"
  enabled: true
  severity: CRITICAL
  checks:
    - type: POM_VALIDATION_FORBIDDEN
      params:
        forbiddenElements:
          - element: "dependencies/dependency/version"
            values: [".*-SNAPSHOT"]  # Regex pattern
```

### Example 3: Prevent Specific Plugins

```yaml
- id: "RULE-092"
  name: "No Exec Maven Plugin"
  description: "Prevent usage of exec-maven-plugin for security reasons"
  enabled: true
  severity: HIGH
  checks:
    - type: POM_VALIDATION_FORBIDDEN
      params:
        forbiddenElements:
          - element: "build/plugins/plugin/artifactId"
            values: ["exec-maven-plugin"]
```

## Error Messages

```
pom.xml has forbidden element: dependencies/dependency/artifactId with value: mule-transport-http
pom.xml has forbidden element: dependencies/dependency/version with value: 1.0.0-SNAPSHOT
```

## Related Rule Types

- **[POM_VALIDATION_REQUIRED](POM_VALIDATION_REQUIRED.md)** - Opposite: ensures elements DO exist
- **[XML_XPATH_NOT_EXISTS](XML_XPATH_NOT_EXISTS.md)** - More complex XPath-based POM validation
