# POM_VALIDATION_FORBIDDEN

**Rule Type:** `CODE` - **Applies To:** pom.xmlMaven POM files (`pom.xml`)

## Overview

Validates that **forbidden elements do NOT exist** in Maven POM files (`pom.xml`). This rule **fails** if forbidden POM elements **ARE found**.

Supports validation of:
- **Forbidden Properties** (property names)
- **Forbidden Dependencies** (groupId, artifactId, optional version)
- **Forbidden Plugins** (groupId, artifactId, optional version)

## Use Cases

- Block deprecated or vulnerable dependencies
- Prevent usage of forbidden Maven plugins
- Disallow specific properties in production
- Enforce dependency exclusion policies
- Block specific versions of libraries (security vulnerabilities)

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `validationType` | String | Type of validation: `PROPERTIES`, `DEPENDENCIES`, `PLUGINS`, or `COMBINED` |

### Validation-Specific Parameters

#### PROPERTIES Validation

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `forbiddenProperties` | List<String> | Yes | List of property names that must NOT exist |

#### DEPENDENCIES Validation

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `forbiddenDependencies` | List<Map> | Yes | List of forbidden dependencies |
| `forbiddenDependencies[].groupId` | String | Yes | Dependency groupId |
| `forbiddenDependencies[].artifactId` | String | Yes | Dependency artifactId |
| `forbiddenDependencies[].version` | String | **No** | **Optional**: If specified, only that specific version is forbidden |

> **NEW**: Version validation is now optional! If `version` is specified, only that exact version is forbidden. If omitted, ALL versions are forbidden.

#### PLUGINS Validation

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `forbiddenPlugins` | List<Map> | Yes | List of forbidden plugins |
| `forbiddenPlugins[].groupId` | String | Yes | Plugin groupId |
| `forbiddenPlugins[].artifactId` | String | Yes | Plugin artifactId |
| `forbiddenPlugins[].version` | String | **No** | **Optional**: If specified, only that specific version is forbidden |

> **NEW**: Version validation is now optional! If `version` is specified, only that exact version is forbidden. If omitted, ALL versions are forbidden.

---

## Configuration Examples

### Example 1: Forbidden Properties (Single Property)

Block a single forbidden property.

```yaml
- id: "RULE-020"
  name: "No Debug Mode in Production"
  description: "Debug mode property must not exist"
  enabled: true
  severity: HIGH
  checks:
    - type: POM_VALIDATION_FORBIDDEN
      params:
        validationType: PROPERTIES
        forbiddenProperties:
          - "debug.enabled"
```

**Behavior**:
- ✅ PASS if `<debug.enabled>` does NOT exist
- ❌ FAIL if `<debug.enabled>` exists (regardless of value)

---

### Example 2: Forbidden Properties (Multiple Properties)

Block multiple forbidden properties.

```yaml
- id: "RULE-021"
  name: "No Deprecated Properties"
  description: "Block usage of deprecated property names"
  enabled: true
  severity: MEDIUM
  checks:
    - type: POM_VALIDATION_FORBIDDEN
      params:
        validationType: PROPERTIES
        forbiddenProperties:
          - "old.api.version"
          - "deprecated.config"
          - "legacy.mode"
```

**Behavior**: Rule fails if ANY forbidden property exists.

---

### Example 3: Forbidden Dependency (ALL Versions)

Block a dependency completely (all versions).

```yaml
- id: "RULE-022"
  name: "No Spring Security LDAP"
  description: "Spring Security LDAP is deprecated"
  enabled: true
  severity: HIGH
  checks:
    - type: POM_VALIDATION_FORBIDDEN
      params:
        validationType: DEPENDENCIES
        forbiddenDependencies:
          - groupId: "org.springframework.security"
            artifactId: "spring-security-ldap"
            # No version - ALL versions forbidden
```

**Behavior**:
- ❌ FAIL if `org.springframework.security:spring-security-ldap:1.8.0` exists
- ❌ FAIL if `org.springframework.security:spring-security-ldap:2.0.0` exists
- ❌ FAIL if ANY version of this dependency exists
- ✅ PASS if dependency is not present

---

### Example 4: Forbidden Dependency (Specific Version Only)

Block only a specific vulnerable version.

```yaml
- id: "RULE-023"
  name: "Block Vulnerable Log4j Version"
  description: "Log4j 2.14.1 has critical vulnerability"
  enabled: true
  severity: CRITICAL
  checks:
    - type: POM_VALIDATION_FORBIDDEN
      params:
        validationType: DEPENDENCIES
        forbiddenDependencies:
          - groupId: "org.apache.logging.log4j"
            artifactId: "log4j-core"
            version: "2.14.1"  # Only this specific version is forbidden
```

**Behavior**:
- ❌ FAIL if `org.apache.logging.log4j:log4j-core:2.14.1` exists (vulnerable)
- ✅ PASS if `org.apache.logging.log4j:log4j-core:2.17.0` exists (safe version)
- ✅ PASS if dependency is not present

---

### Example 5: Multiple Forbidden Dependencies (Mixed)

Block multiple dependencies with different version restrictions.

```yaml
- id: "RULE-024"
  name: "Dependency Blocklist"
  description: "Block deprecated and vulnerable dependencies"
  enabled: true
  severity: HIGH
  checks:
    - type: POM_VALIDATION_FORBIDDEN
      params:
        validationType: DEPENDENCIES
        forbiddenDependencies:
          # Block ALL versions of deprecated library
          - groupId: "org.springframework.security"
            artifactId: "spring-security-ldap"
          
          # Block specific vulnerable version
          - groupId: "org.apache.struts"
            artifactId: "struts2-core"
            version: "2.3.20"
          
          # Block ALL versions of another deprecated library
          - groupId: "com.ibm.db2"
            artifactId: "db2jcc_license_cu"
```

**Behavior**: Each dependency is validated according to its configuration.

---

### Example 6: Forbidden Plugin (ALL Versions)

Block a plugin completely.

```yaml
- id: "RULE-025"
  name: "No Maven Surefire Plugin"
  description: "Maven Surefire plugin is not allowed"
  enabled: true
  severity: MEDIUM
  checks:
    - type: POM_VALIDATION_FORBIDDEN
      params:
        validationType: PLUGINS
        forbiddenPlugins:
          - groupId: "org.apache.maven.plugins"
            artifactId: "maven-surefire-plugin"
            # No version - ALL versions forbidden
```

**Behavior**: Any version of the plugin will cause failure.

---

### Example 7: Forbidden Plugin (Specific Version Only)

Block only a specific version of a plugin.

```yaml
- id: "RULE-026"
  name: "Block Old Compiler Plugin"
  description: "Maven compiler plugin 3.8.0 has known issues"
  enabled: true
  severity: LOW
  checks:
    - type: POM_VALIDATION_FORBIDDEN
      params:
        validationType: PLUGINS
        forbiddenPlugins:
          - groupId: "org.apache.maven.plugins"
            artifactId: "maven-compiler-plugin"
            version: "3.8.0"  # Only this version is forbidden
```

**Behavior**:
- ❌ FAIL if plugin version is 3.8.0
- ✅ PASS if plugin version is 3.11.0 (newer version OK)
- ✅ PASS if plugin is not present

---

### Example 8: Multiple Forbidden Plugins

Block multiple plugins.

```yaml
- id: "RULE-027"
  name: "Plugin Blocklist"
  description: "Block deprecated and problematic plugins"
  enabled: true
  severity: MEDIUM
  checks:
    - type: POM_VALIDATION_FORBIDDEN
      params:
        validationType: PLUGINS
        forbiddenPlugins:
          - groupId: "org.apache.maven.plugins"
            artifactId: "maven-clean-plugin"
          - groupId: "org.apache.maven.plugins"
            artifactId: "maven-surefire-plugin"
          - groupId: "org.codehaus.mojo"
            artifactId: "findbugs-maven-plugin"
```

**Behavior**: Rule fails if ANY forbidden plugin exists.

---

### Example 9: Combined Validation

Validate multiple POM aspects in a single rule.

```yaml
- id: "RULE-028"
  name: "Complete Forbidden Elements Check"
  description: "Block all deprecated and vulnerable elements"
  enabled: true
  severity: HIGH
  checks:
    - type: POM_VALIDATION_FORBIDDEN
      params:
        validationType: COMBINED
        forbiddenProperties:
          - "debug.enabled"
          - "legacy.mode"
        forbiddenDependencies:
          - groupId: "org.springframework.security"
            artifactId: "spring-security-ldap"
          - groupId: "org.apache.struts"
            artifactId: "struts2-core"
            version: "2.3.20"
        forbiddenPlugins:
          - groupId: "org.apache.maven.plugins"
            artifactId: "maven-clean-plugin"
```

**Behavior**: Rule fails if ANY forbidden element exists.

---

## Error Messages

When validation fails, you'll see detailed messages:

```
Forbidden property 'debug.enabled' found in pom.xml
Forbidden dependency org.springframework.security:spring-security-ldap found in pom.xml
Forbidden plugin org.apache.maven.plugins:maven-clean-plugin found in pom.xml
```

---

## Best Practices

### When to Use Version-Specific Blocking

✅ **Block specific versions when**:
- A particular version has a known vulnerability
- A specific version has bugs or compatibility issues
- You want to allow newer versions but block old ones

✅ **Block all versions when**:
- The entire library/plugin is deprecated
- The dependency should never be used in any version
- You're migrating away from a technology

### Security Use Cases

#### Pattern 1: Block Vulnerable Versions
```yaml
# Block specific vulnerable versions while allowing safe ones
forbiddenDependencies:
  - groupId: "org.apache.logging.log4j"
    artifactId: "log4j-core"
    version: "2.14.1"  # CVE-2021-44228
```

#### Pattern 2: Block Deprecated Libraries
```yaml
# Block entire deprecated library (all versions)
forbiddenDependencies:
  - groupId: "org.springframework.security"
    artifactId: "spring-security-ldap"
    # No version - completely forbidden
```

#### Pattern 3: Migration Enforcement
```yaml
# Block old libraries during migration
forbiddenDependencies:
  - groupId: "com.mulesoft.mule.core"
    artifactId: "mule-core-ee"  # Mule 3 artifact
  - groupId: "org.mule.modules"
    artifactId: "mule-module-spring-config"  # Deprecated
```

---

## Related Rule Types

- **[POM_VALIDATION_REQUIRED](POM_VALIDATION_REQUIRED.md)** - Opposite: ensures elements DO exist
- **[XML_XPATH_NOT_EXISTS](XML_XPATH_NOT_EXISTS.md)** - More complex XPath-based POM validation
- **[JSON_VALIDATION_FORBIDDEN](JSON_VALIDATION_FORBIDDEN.md)** - Similar validation for JSON files

---

## Version History

- **v1.1.0**: Added optional version validation for dependencies and plugins
- **v1.0.0**: Initial release with properties, dependencies, and plugins validation
