# POM_VALIDATION_REQUIRED

**Rule Type:** `CODE` - **Applies To:** Maven POM files (`pom.xml`)

## Overview

Validates that **required elements exist** in Maven POM files (`pom.xml`). This rule **fails** if required POM elements are **NOT found** or don't match expected values.

Supports validation of:
- **Parent POM** (groupId, artifactId, version)
- **Properties** (name and value pairs)
- **Dependencies** (groupId, artifactId, optional version)
- **Plugins** (groupId, artifactId, optional version)

## Use Cases

- Ensure required dependencies (like logging or security libs) are declared.
- Validate Maven plugin configurations for build consistency.
- Enforce parent POM standards across a microservices architecture.
- Check for mandatory project metadata and versioning schemes.
- Validate specific dependency versions for security compliance (e.g., blocking known vulnerable versions).

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `validationType` | String | Type of validation: `PARENT`, `PROPERTIES`, `DEPENDENCIES`, `PLUGINS`, or `COMBINED` |

### Validation-Specific Parameters

#### PARENT Validation

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `parent` | Map | Yes | Parent POM configuration |
| `parent.groupId` | String | Yes | Expected parent groupId |
| `parent.artifactId` | String | Yes | Expected parent artifactId |
| `parent.version` | String | Yes | Expected parent version |

#### PROPERTIES Validation

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `properties` | List<Map> | Yes | List of required properties |
| `properties[].name` | String | Yes | Property name |
| `properties[].expectedValue` | String | No | Expected property value (if specified, value must match) |

#### DEPENDENCIES Validation

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `dependencies` | List<Map> | Yes | List of required dependencies |
| `dependencies[].groupId` | String | Yes | Dependency groupId |
| `dependencies[].artifactId` | String | Yes | Dependency artifactId |
| `dependencies[].version` | String | **No** | **Optional**: If specified, validates exact version |

#### PLUGINS Validation

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `plugins` | List<Map> | Yes | List of required plugins |
| `plugins[].groupId` | String | Yes | Plugin groupId |
| `plugins[].artifactId` | String | Yes | Plugin artifactId |
| `plugins[].version` | String | **No** | **Optional**: If specified, validates exact version |

---

## Configuration Examples

### Example 1: Parent POM Validation (Corporate Standard)

Enforce a specific parent POM for all corporate Java projects.

```yaml
- id: "RULE-POM-PARENT"
  name: "Enforce Standard Parent POM"
  description: "All projects must use CorporateParent version 2.5.0"
  enabled: true
  severity: HIGH
  checks:
    - type: POM_VALIDATION_REQUIRED
      params:
        validationType: PARENT
        parent:
          groupId: "com.corporation.framework"
          artifactId: "CorporateParent"
          version: "2.5.0"
```

---

### Example 2: Properties Validation (Runtime/Compiler Version)

Validate compiler versions or runtime targets.

```yaml
- id: "RULE-POM-PROP"
  name: "Java Version Check"
  description: "Java source and target must be 17"
  enabled: true
  severity: HIGH
  checks:
    - type: POM_VALIDATION_REQUIRED
      params:
        validationType: PROPERTIES
        properties:
          - name: "maven.compiler.source"
            expectedValue: "17"
          - name: "maven.compiler.target"
            expectedValue: "17"
```

---

### Example 3: Dependency Validation (Security Libs)

Require a specific security library version.

```yaml
- id: "RULE-POM-DEP"
  name: "Security Library Requirement"
  description: "Standard security core must be present"
  enabled: true
  severity: CRITICAL
  checks:
    - type: POM_VALIDATION_REQUIRED
      params:
        validationType: DEPENDENCIES
        dependencies:
          - groupId: "com.corporation.security"
            artifactId: "security-core"
            version: "3.2.1"
```

---

### Example 4: Combined Validation (Aegis Comprehensive Check)

Validate multiple POM aspects in a single rule.

```yaml
- id: "RULE-POM-COMPREHENSIVE"
  name: "Full POM Compliance"
  description: "Comprehensive check for dependencies, plugins, and properties"
  enabled: true
  severity: HIGH
  checks:
    - type: POM_VALIDATION_REQUIRED
      params:
        validationType: COMBINED
        properties:
          - name: "project.build.sourceEncoding"
            expectedValue: "UTF-8"
        dependencies:
          - groupId: "org.slf4j"
            artifactId: "slf4j-api"
        plugins:
          - groupId: "org.apache.maven.plugins"
            artifactId: "maven-compiler-plugin"
            version: "3.11.0"
```

---

## Error Messages

```
Parent version mismatch in pom.xml: expected com.corp:Parent:2.5.0, got version '1.0.0'
Property 'maven.compiler.source' has wrong value in pom.xml: expected '17', got '11'
Dependency com.corporation.security:security-core not found in pom.xml
```

---

## Best Practices

- **Use version validation for security**: Lock critical security libraries to specific patched versions.
- **Flexible utilities**: For standard utilities (e.g., `commons-lang`), omit the `version` parameter to allow project-specific updates unless a specific version is required for compatibility.
- **Combined Rules**: Use `validationType: COMBINED` to reduce the number of rules defined when multiple checks are related to the same "standard" (e.g., "Corporate Standard v2").

---

## Solution Patterns and Technology References

| Technology | Best Practice Goal | Key Elements | Attributes Checked |
| :--- | :--- | :--- | :--- |
| **☕ Java/Maven** | Parent POM Usage | `parent` | `version` |
| **☕ Java/Maven** | Compiler Standard | `properties` | `maven.compiler.source` |
| **🐎 MuleSoft** | Mule Plugin Check | `mule-maven-plugin` | `version` |

### ☕ Java / Maven Patterns

**Scenario**: To ensure reproducible builds, it is critical that the `parent` POM is strictly versioned and that major build plugins are explicitly defined with their respective versions.

```yaml
id: "MAVEN-PARENT-CHECK"
name: "Parent POM Validation"
description: "Ensure specific corporate parent POM is used"
checks:
  - type: POM_VALIDATION_REQUIRED
    params:
      parent:
        groupId: "com.corporation.framework"
        artifactId: "CorporateParent"
        version: "2.5.0"
```

---

## Related Rule Types

- **[POM_VALIDATION_FORBIDDEN](POM_VALIDATION_FORBIDDEN.md)** - Opposite: ensures elements do NOT exist.
- **[XML_XPATH_EXISTS](XML_XPATH_EXISTS.md)** - For more granular POM validation using raw XPath.
- **[JSON_VALIDATION_REQUIRED](JSON_VALIDATION_REQUIRED.md)** - Similar validation for JSON-based projects (Node.js, Mule artifacts).

---


