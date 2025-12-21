# POM_VALIDATION_REQUIRED

**Rule Type:** `CODE` | **Applies To:** Maven POM files (`pom.xml`)

## Overview

Validates that **required elements exist** in Maven POM files (`pom.xml`). This rule **fails** if required POM elements are **NOT found** or don't match expected values.

Supports validation of:
- **Parent POM** (groupId, artifactId, version)
- **Properties** (name and value pairs)
- **Dependencies** (groupId, artifactId, optional version)
- **Plugins** (groupId, artifactId, optional version)

## Use Cases

- Ensure required dependencies are declared
- Validate Maven plugin configurations
- Enforce parent POM standards
- Check for mandatory project metadata
- Enforce dependency management standards
- Validate specific dependency versions (security compliance)

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

> **NEW**: Version validation is now optional! If `version` is specified, it will be validated. If omitted, any version is accepted.

#### PLUGINS Validation

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `plugins` | List<Map> | Yes | List of required plugins |
| `plugins[].groupId` | String | Yes | Plugin groupId |
| `plugins[].artifactId` | String | Yes | Plugin artifactId |
| `plugins[].version` | String | **No** | **Optional**: If specified, validates exact version |

> **NEW**: Version validation is now optional! If `version` is specified, it will be validated. If omitted, any version is accepted.

---

## Configuration Examples

### Example 1: Parent POM Validation (with Version)

Enforce a specific parent POM with exact version.

```yaml
- id: "RULE-001"
  name: "Enforce Standard Parent POM"
  description: "All projects must use MuleParentPom version LATEST"
  enabled: true
  severity: HIGH
  checks:
    - type: POM_VALIDATION_REQUIRED
      params:
        validationType: PARENT
        parent:
          groupId: "com.raks.eapi"
          artifactId: "MuleParentPom"
          version: "LATEST"  # Exact version required
```

**Behavior**:
- ✅ PASS if parent is `com.raks.eapi:MuleParentPom:LATEST`
- ❌ FAIL if parent version is different (e.g., "1.0.0")
- ❌ FAIL if parent is missing

---

### Example 2: Properties Validation (Single Property)

Validate a single property with exact value.

```yaml
- id: "RULE-002"
  name: "Mule Runtime Version Check"
  description: "Mule runtime must be 4.9.0"
  enabled: true
  severity: HIGH
  checks:
    - type: POM_VALIDATION_REQUIRED
      params:
        validationType: PROPERTIES
        properties:
          - name: "app.runtime"
            expectedValue: "4.9.0"
```

**Behavior**:
- ✅ PASS if `<app.runtime>4.9.0</app.runtime>` exists
- ❌ FAIL if value is different (e.g., "4.8.0")
- ❌ FAIL if property is missing

---

### Example 3: Properties Validation (Multiple Properties)

Validate multiple properties with different expected values.

```yaml
- id: "RULE-003"
  name: "Project Configuration Standards"
  description: "Validate multiple project properties"
  enabled: true
  severity: MEDIUM
  checks:
    - type: POM_VALIDATION_REQUIRED
      params:
        validationType: PROPERTIES
        properties:
          - name: "mule.maven.plugin.version"
            expectedValue: "4.5.0"
          - name: "app.runtime"
            expectedValue: "4.9.0"
          - name: "project.build.sourceEncoding"
            expectedValue: "UTF-8"
```

**Behavior**: Each property is validated independently. Rule fails if ANY property is missing or has wrong value.

---

### Example 4: Properties Validation (Existence Only)

Check that properties exist without validating their values.

```yaml
- id: "RULE-004"
  name: "Required Properties Exist"
  description: "Ensure required properties are defined"
  enabled: true
  severity: LOW
  checks:
    - type: POM_VALIDATION_REQUIRED
      params:
        validationType: PROPERTIES
        properties:
          - name: "app.name"
            # No expectedValue - just check existence
          - name: "app.version"
          - name: "app.description"
```

**Behavior**: Properties must exist but can have any value.

---

### Example 5: Dependency Validation (WITHOUT Version)

Require a dependency but allow any version.

```yaml
- id: "RULE-005"
  name: "HTTP Connector Required"
  description: "Mule HTTP connector must be present"
  enabled: true
  severity: HIGH
  checks:
    - type: POM_VALIDATION_REQUIRED
      params:
        validationType: DEPENDENCIES
        dependencies:
          - groupId: "org.mule.connectors"
            artifactId: "mule-http-connector"
            # No version specified - any version OK
```

**Behavior**:
- ✅ PASS if `org.mule.connectors:mule-http-connector:1.10.5` exists
- ✅ PASS if `org.mule.connectors:mule-http-connector:2.0.0` exists
- ❌ FAIL if dependency is missing

---

### Example 6: Dependency Validation (WITH Version)

Require a specific dependency version for security compliance.

```yaml
- id: "RULE-006"
  name: "Security Library Version"
  description: "Security library must be version 3.0.0"
  enabled: true
  severity: CRITICAL
  checks:
    - type: POM_VALIDATION_REQUIRED
      params:
        validationType: DEPENDENCIES
        dependencies:
          - groupId: "com.raks.security"
            artifactId: "security-core"
            version: "3.0.0"  # Exact version required
```

**Behavior**:
- ✅ PASS if `com.raks.security:security-core:3.0.0` exists
- ❌ FAIL if version is different (e.g., "2.9.0")
- ❌ FAIL if dependency is missing

---

### Example 7: Multiple Dependencies (Mixed Version Validation)

Validate multiple dependencies with different version requirements.

```yaml
- id: "RULE-007"
  name: "Required Dependencies"
  description: "Validate multiple required dependencies"
  enabled: true
  severity: HIGH
  checks:
    - type: POM_VALIDATION_REQUIRED
      params:
        validationType: DEPENDENCIES
        dependencies:
          # Require specific version for security library
          - groupId: "com.raks.eapi"
            artifactId: "security-framework"
            version: "2.0.0"
          
          # Allow any version for utility library
          - groupId: "com.raks.eapi"
            artifactId: "common-utils"
          
          # Require specific version for compliance
          - groupId: "org.mule.connectors"
            artifactId: "mule-http-connector"
            version: "1.10.5"
```

**Behavior**: Each dependency is validated according to its configuration.

---

### Example 8: Plugin Validation (WITHOUT Version)

Require a plugin but allow any version.

```yaml
- id: "RULE-008"
  name: "Mule Maven Plugin Required"
  description: "Mule Maven plugin must be present"
  enabled: true
  severity: HIGH
  checks:
    - type: POM_VALIDATION_REQUIRED
      params:
        validationType: PLUGINS
        plugins:
          - groupId: "org.mule.tools.maven"
            artifactId: "mule-maven-plugin"
            # No version - any version OK
```

**Behavior**: Plugin must exist with any version.

---

### Example 9: Plugin Validation (WITH Version)

Enforce a specific plugin version for build consistency.

```yaml
- id: "RULE-009"
  name: "Mule Maven Plugin Version"
  description: "Mule Maven plugin must be version 4.5.0"
  enabled: true
  severity: MEDIUM
  checks:
    - type: POM_VALIDATION_REQUIRED
      params:
        validationType: PLUGINS
        plugins:
          - groupId: "org.mule.tools.maven"
            artifactId: "mule-maven-plugin"
            version: "4.5.0"  # Exact version required
```

**Behavior**:
- ✅ PASS if plugin exists with version 4.5.0
- ❌ FAIL if version is different
- ❌ FAIL if plugin is missing

---

### Example 10: Combined Validation

Validate multiple POM aspects in a single rule.

```yaml
- id: "RULE-010"
  name: "Complete POM Validation"
  description: "Comprehensive POM validation"
  enabled: true
  severity: HIGH
  checks:
    - type: POM_VALIDATION_REQUIRED
      params:
        validationType: COMBINED
        parent:
          groupId: "com.raks.eapi"
          artifactId: "MuleParentPom"
          version: "LATEST"
        properties:
          - name: "app.runtime"
            expectedValue: "4.9.0"
          - name: "mule.maven.plugin.version"
            expectedValue: "4.5.0"
        dependencies:
          - groupId: "com.raks.eapi"
            artifactId: "apimuleutilities"
        plugins:
          - groupId: "org.mule.tools.maven"
            artifactId: "mule-maven-plugin"
            version: "4.5.0"
```

**Behavior**: All validations must pass for the rule to pass.

---

## Error Messages

When validation fails, you'll see detailed messages:

```
Parent version mismatch in pom.xml: expected com.raks.eapi:MuleParentPom:LATEST, got version '1.0.0'
Property 'app.runtime' has wrong value in pom.xml: expected '4.9.0', got '4.8.0'
Property 'mule.maven.plugin.version' missing in pom.xml
Dependency com.raks.security:security-core not found in pom.xml
Plugin org.mule.tools.maven:mule-maven-plugin not found in pom.xml
```

---

## Best Practices

### When to Use Version Validation

✅ **Use version validation when**:
- Security compliance requires specific versions
- Breaking changes exist between versions
- Standardization across projects is critical
- Vulnerable versions must be blocked

❌ **Don't use version validation when**:
- Version flexibility is desired
- Automatic updates are preferred
- Minor version differences are acceptable

### Recommended Patterns

#### Pattern 1: Critical Dependencies
```yaml
# Lock critical security libraries to specific versions
dependencies:
  - groupId: "com.raks.security"
    artifactId: "authentication"
    version: "2.0.0"
```

#### Pattern 2: Utility Libraries
```yaml
# Allow flexibility for utility libraries
dependencies:
  - groupId: "org.apache.commons"
    artifactId: "commons-lang3"
    # No version - any version acceptable
```

#### Pattern 3: Parent POM Standardization
```yaml
# Always validate parent POM with exact version
parent:
  groupId: "com.company"
  artifactId: "corporate-parent"
  version: "1.5.0"
```

---

## Related Rule Types

- **[POM_VALIDATION_FORBIDDEN](POM_VALIDATION_FORBIDDEN.md)** - Opposite: ensures elements do NOT exist
- **[XML_XPATH_EXISTS](XML_XPATH_EXISTS.md)** - More complex XPath-based POM validation
- **[JSON_VALIDATION_REQUIRED](JSON_VALIDATION_REQUIRED.md)** - Similar validation for JSON files

---

## Version History

- **v1.1.0**: Added optional version validation for dependencies and plugins
- **v1.0.0**: Initial release with parent, properties, dependencies, and plugins validation
