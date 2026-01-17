# POM_VALIDATION_FORBIDDEN

**Rule Type:** `CODE` - **Applies To:** Maven POM files (`pom.xml`)

## Overview

Validates that **forbidden elements do NOT exist** in Maven POM files (`pom.xml`). This rule **fails** if forbidden POM elements **ARE found**. It is commonly used to block vulnerable libraries, deprecated plugins, or insecure configuration properties.

Supports validation of:
- **Forbidden Properties** (property names)
- **Forbidden Dependencies** (groupId, artifactId, optional version)
- **Forbidden Plugins** (groupId, artifactId, optional version)

## Use Cases

- Block deprecated or vulnerable dependencies (e.g., Log4j 1.x, old Struts).
- Prevent usage of forbidden or untrusted Maven plugins.
- Disallow specific internal properties from being hardcoded or overridden in production.
- Enforce dependency exclusion policies by banning restricted group/artifact IDs.
- Block specific vulnerable versions while allowing safe, patched versions.

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

#### PLUGINS Validation

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `forbiddenPlugins` | List<Map> | Yes | List of forbidden plugins |
| `forbiddenPlugins[].groupId` | String | Yes | Plugin groupId |
| `forbiddenPlugins[].artifactId` | String | Yes | Plugin artifactId |
| `forbiddenPlugins[].version` | String | **No** | **Optional**: If specified, only that specific version is forbidden |

---

## Configuration Examples

### Example 1: Forbidden Properties (Environment/Security)

Block properties that should only be handled via environment variables or CI/CD injection.

```yaml
- id: "RULE-POM-FORBIDDEN-PROP"
  name: "No Hardcoded Credentials"
  description: "Credentials must not be stored as POM properties"
  enabled: true
  severity: CRITICAL
  checks:
    - type: POM_VALIDATION_FORBIDDEN
      params:
        validationType: PROPERTIES
        forbiddenProperties:
          - "db.password"
          - "api.key"
          - "secret.token"
```

---

### Example 2: Forbidden Dependency (Security Vulnerability)

Block a specific version of Log4j known for critical vulnerabilities.

```yaml
- id: "RULE-POM-FORBIDDEN-LOG4J"
  name: "Block Vulnerable Log4j"
  description: "Ensure vulnerable versions of log4j-core are not used"
  enabled: true
  severity: CRITICAL
  checks:
    - type: POM_VALIDATION_FORBIDDEN
      params:
        validationType: DEPENDENCIES
        forbiddenDependencies:
          - groupId: "org.apache.logging.log4j"
            artifactId: "log4j-core"
            version: "2.14.1"
```

---

### Example 3: Forbidden Plugins (Standardization)

Prevent the use of problematic or redundant build plugins.

```yaml
- id: "RULE-POM-FORBIDDEN-PLUGIN"
  name: "Plugin Restriction"
  description: "Block forbidden or deprecated build plugins"
  enabled: true
  severity: MEDIUM
  checks:
    - type: POM_VALIDATION_FORBIDDEN
      params:
        validationType: PLUGINS
        forbiddenPlugins:
          - groupId: "org.codehaus.mojo"
            artifactId: "findbugs-maven-plugin" # Replaced by SpotBugs
```

---

### Example 4: Combined Validation (Policy Blocklist)

Block multiple forbidden elements in one cohesive policy.

```yaml
- id: "RULE-POM-BLOCKLIST"
  name: "Corporate Policy Blocklist"
  description: "Block all known deprecated and insecure elements"
  enabled: true
  severity: HIGH
  checks:
    - type: POM_VALIDATION_FORBIDDEN
      params:
        validationType: COMBINED
        forbiddenProperties:
          - "legacy.mode.enabled"
        forbiddenDependencies:
          - groupId: "log4j"
            artifactId: "log4j" # 1.x version
        forbiddenPlugins:
          - groupId: "org.apache.maven.plugins"
            artifactId: "maven-antrun-plugin"
```

---

## Error Messages

```
Forbidden property 'db.password' found in pom.xml
Forbidden dependency org.apache.logging.log4j:log4j-core:2.14.1 found in pom.xml
Forbidden plugin org.codehaus.mojo:findbugs-maven-plugin found in pom.xml
```

---

## Best Practices

- **Block all versions for deprecation**: If a library is completely replaced, don't specify a version so that ALL versions are forbidden.
- **Specific versions for security**: If only one version is vulnerable, specify that exact version to allow projects to use safe, newer releases.
- **Exclusion Lists**: Use combined rules for enterprise-wide policy enforcement (e.g., "Standard Blocklist 2024").

---

## Solution Patterns and Technology References

| Technology | Best Practice Goal | Forbidden Item | Reason |
| :--- | :--- | :--- | :--- |
| **☕ Java/Maven** | Security | `log4j` (1.x) | Critical vulnerabilities |
| **☕ Java/Maven** | Build Safety | `maven-antrun-plugin` | Unstructured build logic |
| **🐎 MuleSoft** | Migration Safety | `mule-module-http` | Deprecated in Mule 4 |

### ☕ Java / Maven Patterns

**Scenario**: Enterprise security teams often maintain a "banned library" list. Aegis enforces this automatically during the build or code review phase.

```yaml
id: "SECURITY-BANNED-LIBS"
name: "Banned Security Libraries"
description: "Block known insecure libraries"
checks:
  - type: POM_VALIDATION_FORBIDDEN
    params:
      forbiddenDependencies:
        - groupId: "log4j"
          artifactId: "log4j"
        - groupId: "org.apache.struts"
          artifactId: "struts2-core"
          version: "2.3.20"
```

---

## Related Rule Types

- **[POM_VALIDATION_REQUIRED](POM_VALIDATION_REQUIRED.md)** - Opposite: ensures elements DO exist.
- **[XML_XPATH_NOT_EXISTS](XML_XPATH_NOT_EXISTS.md)** - For complex nested forbidden structures in POM/XML.
- **[JSON_VALIDATION_FORBIDDEN](JSON_VALIDATION_FORBIDDEN.md)** - Similar validation for JSON-based projects.

---


