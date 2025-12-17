# POM_VALIDATION_REQUIRED

## Overview

Validates that **required elements exist** in Maven POM files (`pom.xml`). This rule **fails** if required POM elements are **NOT found**.

## Use Cases

- Ensure required dependencies are declared
- Validate Maven plugin configurations
- Check for mandatory project metadata
- Enforce dependency management standards

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `requiredElements` | List<Map> | List of required POM elements with optional value validation |

#### Required Element Map Structure

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `element` | String | Yes | POM element path (e.g., `dependencies/dependency/groupId`) |
| `values` | List<String> | No | If specified, element value must match one of these |

## Configuration Examples

### Example 1: Ensure Required Dependency

```yaml
- id: "RULE-080"
  name: "Mule HTTP Connector Required"
  description: "All projects must include Mule HTTP connector"
  enabled: true
  severity: HIGH
  checks:
    - type: POM_VALIDATION_REQUIRED
      params:
        requiredElements:
          - element: "dependencies/dependency/artifactId"
            values: ["mule-http-connector"]
```

### Example 2: Validate Maven Compiler Plugin

```yaml
- id: "RULE-081"
  name: "Maven Compiler Plugin Configuration"
  description: "Ensure Maven compiler plugin is configured with Java 17"
  enabled: true
  severity: MEDIUM
  checks:
    - type: POM_VALIDATION_REQUIRED
      params:
        requiredElements:
          - element: "build/plugins/plugin/artifactId"
            values: ["maven-compiler-plugin"]
          - element: "build/plugins/plugin/configuration/source"
            values: ["17"]
          - element: "build/plugins/plugin/configuration/target"
            values: ["17"]
```

### Example 3: Check Project Metadata

```yaml
- id: "RULE-082"
  name: "Project Metadata Complete"
  description: "POM must have required project metadata"
  enabled: true
  severity: LOW
  checks:
    - type: POM_VALIDATION_REQUIRED
      params:
        requiredElements:
          - element: "groupId"
          - element: "artifactId"
          - element: "version"
          - element: "name"
          - element: "description"
```

## Error Messages

```
pom.xml is missing required element: dependencies/dependency/artifactId with value: mule-http-connector
pom.xml is missing required element: build/plugins/plugin/configuration/source
```

## Related Rule Types

- **[POM_VALIDATION_FORBIDDEN](POM_VALIDATION_FORBIDDEN.md)** - Opposite: ensures elements do NOT exist
- **[XML_XPATH_EXISTS](XML_XPATH_EXISTS.md)** - More complex XPath-based POM validation
