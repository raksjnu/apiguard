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
| `parent.version` | String | No | **Exact match**: Expected parent version |
| `parent.minVersion` | String | No | **NEW**: Minimum version (>= comparison) |
| `parent.maxVersion` | String | No | **NEW**: Maximum version (<= comparison) |

#### PROPERTIES Validation

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `properties` | List<Map> | Yes | List of required properties |
| `properties[].name` | String | Yes | Property name |
| `properties[].expectedValue` | String | No | **Exact match**: Expected property value |
| `properties[].minVersion` | String | No | **NEW**: Minimum version (>= comparison) |
| `properties[].maxVersion` | String | No | **NEW**: Maximum version (<= comparison) |
| `properties[].greaterThan` | String | No | **NEW**: Greater than (> comparison) |
| `properties[].lessThan` | String | No | **NEW**: Less than (< comparison) |

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
#### GLOBAL Parameters (Optional)

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `resolveProperties` | Boolean | `false` | Enable `${...}` resolution |
| `resolveLinkedConfig`| Boolean | `false` | Resolve from linked CONFIG project |
| `includeLinkedConfig`| Boolean | `false` | Scan files in linked CONFIG project |

---

## Configuration Examples

### Example 1: Parent POM Validation - Minimum Version (NEW)

Enforce a minimum parent POM version instead of exact match.

```yaml
- id: "RULE-POM-PARENT"
  name: "Enforce Standard Parent POM (Minimum Version)"
  description: "All projects must use CorporateParent version >= 2.5.0"
  enabled: true
  severity: HIGH
  checks:
    - type: POM_VALIDATION_REQUIRED
      params:
        validationType: PARENT
        parent:
          groupId: "com.corporation.framework"
          artifactId: "CorporateParent"
          minVersion: "2.5.0"  # NEW: Allows 2.5.0, 2.5.1, 2.6.0, 3.0.0, etc.
```

**Benefits**: More flexible - allows patch/minor updates without rule changes.

**Test Results**:
- Parent version `2.5.0` → ✅ PASS
- Parent version `2.5.5` → ✅ PASS  
- Parent version `3.0.0` → ✅ PASS
- Parent version `2.4.9` → ❌ FAIL

---

### Example 2: Properties Validation - Minimum Versions (NEW)

Validate minimum versions for build tools and runtime.

```yaml
- id: "RULE-POM-PROP"
  name: "Build Tool Minimum Versions"
  description: "Ensure mule.maven.plugin.version >= 4.5.0 and app.runtime >= 4.9.0"
  enabled: true
  severity: HIGH
  checks:
    - type: POM_VALIDATION_REQUIRED
      params:
        validationType: PROPERTIES
        properties:
          - name: "mule.maven.plugin.version"
            minVersion: "4.5.0"  # NEW: Allows 4.5.0, 4.6.1, 5.0.0, etc.
          - name: "app.runtime"
            minVersion: "4.9.0"  # NEW: Works with 4.9.0, 4.9.LTS, 4.10.0, etc.
          - name: "cicd.mule.version"
            expectedValue: "4.9.LTS"  # Exact match still supported
```

**Alphanumeric Version Support**: Versions like `4.9.LTS` are supported. LTS qualifier is treated as HIGHER than no qualifier.

**Test Results**:
- `mule.maven.plugin.version: 4.6.1` → ✅ PASS
- `mule.maven.plugin.version: 4.4.9` → ❌ FAIL
- `app.runtime: 4.9.LTS` → ✅ PASS (LTS >= 4.9.0)
- `app.runtime: 4.8.5` → ❌ FAIL

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

### Example 4: Version Range Validation (NEW)

Combine minVersion and maxVersion for range validation.

```yaml
- id: "RULE-POM-VERSION-RANGE"
  name: "Plugin Version Range"
  description: "Plugin version must be between 4.5.0 and 5.0.0"
  enabled: true
  severity: MEDIUM
  checks:
    - type: POM_VALIDATION_REQUIRED
      params:
        validationType: PROPERTIES
        properties:
          - name: "plugin.version"
            minVersion: "4.5.0"
            maxVersion: "5.0.0"  # Must be between 4.5.0 and 5.0.0
```

**Test Results**:
- `plugin.version: 4.6.1` → ✅ PASS (within range)
- `plugin.version: 5.0.0` → ✅ PASS (at max)
- `plugin.version: 5.0.1` → ❌ FAIL (exceeds max)
- `plugin.version: 4.4.9` → ❌ FAIL (below min)

---

## Enhanced Reporting (NEW)

Success messages now include **actual values found** for better transparency:

```
✅ PASS: Validate Parent POM (Minimum Version)

All required POM elements found
Files validated: pom.xml

Actual Values Found:
• Parent: com.corporation.framework:CorporateParent:2.5.5 (in pom.xml)
• Property 'mule.maven.plugin.version': 4.6.1 (in pom.xml)
• Property 'app.runtime': 4.9.LTS (in pom.xml)
```

This shows:
- ✅ What was expected (rule configuration)
- ✅ What was actually found (actual values)
- ✅ Builds confidence in validation results

---

## Error Messages

**Version Comparison Errors** (NEW):
```
Parent version too low in pom.xml: com.corp:Parent expected >= '2.5.0', got '2.4.9'
Property 'mule.maven.plugin.version' version too low in pom.xml: expected >= '4.5.0', got '4.4.9'
Property 'app.runtime' version too high in pom.xml: expected <= '5.0.0', got '5.1.0'
```

**Exact Match Errors** (Existing):
```
Parent version mismatch in pom.xml: expected com.corp:Parent:2.5.0, got version '1.0.0'
Property 'maven.compiler.source' has wrong value in pom.xml: expected '17', got '11'
Dependency com.corporation.security:security-core not found in pom.xml
```

---

## Version Comparison Logic (NEW)

### Numeric Versions
Versions are compared numerically by major.minor.patch:
```
4.9.0 < 4.9.1 < 4.10.0 < 5.0.0
```

### Alphanumeric Versions (e.g., LTS)
Versions with qualifiers like `LTS` are supported:
```
Version: "4.9.LTS"
  ├── major: 4
  ├── minor: 9
  ├── patch: 0
  └── qualifier: "LTS"
```

**Comparison Rules**:
1. Compare major.minor.patch numerically first
2. If equal, compare qualifiers
3. **LTS is treated as HIGHER than no qualifier**

**Examples**:
- `4.9.LTS >= 4.9.0` → ✅ TRUE (LTS > no qualifier)
- `4.9.0 >= 4.9.0` → ✅ TRUE (equal)
- `4.10.0 >= 4.9.LTS` → ✅ TRUE (4.10 > 4.9)
- `4.8.5 >= 4.9.0` → ❌ FALSE (4.8 < 4.9)

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


