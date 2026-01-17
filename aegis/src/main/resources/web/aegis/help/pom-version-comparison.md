# POM Validation - Version Comparison Support

## Overview

Aegis POM validation now supports **version comparison operators** for more flexible version checking. Instead of requiring exact version matches, you can specify minimum/maximum version constraints.

---

## Supported Operators

| Operator | Syntax | Description | Example |
|----------|--------|-------------|---------|
| **Minimum Version** | `minVersion` | Version must be >= specified value | `minVersion: 4.5.0` |
| **Maximum Version** | `maxVersion` | Version must be <= specified value | `maxVersion: 5.0.0` |
| **Greater Than** | `greaterThan` | Version must be > specified value | `greaterThan: 4.0.0` |
| **Less Than** | `lessThan` | Version must be < specified value | `lessThan: 5.0.0` |
| **Exact Match** | `expectedValue` | Version must equal exactly | `expectedValue: 4.9.LTS` |

---

## Version Comparison Logic

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
- `4.9.LTS >= 4.9.0` → ✅ **TRUE** (LTS > no qualifier)
- `4.9.0 >= 4.9.0` → ✅ **TRUE** (equal)
- `4.8.5 >= 4.9.0` → ❌ **FALSE** (4.8 < 4.9)
- `4.10.0 >= 4.9.LTS` → ✅ **TRUE** (4.10 > 4.9)

---

## Usage Examples

### Example 1: Parent POM Minimum Version

**Before (Exact Match)**:
```yaml
- id: "BANK-023"
  checks:
  - type: POM_VALIDATION_REQUIRED
    params:
      parent:
        groupId: com.truist.eapi
        artifactId: MuleParentPom
        version: 3.0.0  # Must be exactly 3.0.0
```

**After (Minimum Version)**:
```yaml
- id: "BANK-023"
  checks:
  - type: POM_VALIDATION_REQUIRED
    params:
      parent:
        groupId: com.truist.eapi
        artifactId: MuleParentPom
        minVersion: 3.0.0  # Can be 3.0.0, 3.0.5, 3.1.0, etc.
```

**Test Results**:
- Parent version `3.0.0` → ✅ PASS
- Parent version `3.0.5` → ✅ PASS
- Parent version `2.9.9` → ❌ FAIL

---

### Example 2: Property Minimum Versions

**Before (Complex Regex)**:
```yaml
- id: "BANK-024"
  checks:
  - type: GENERIC_TOKEN_SEARCH_FORBIDDEN
    params:
      tokens:
      - "<mule\\.maven\\.plugin\\.version>4\\.[0-4]\\."  # Hard to understand!
      matchMode: REGEX
```

**After (Simple minVersion)**:
```yaml
- id: "BANK-024"
  checks:
  - type: POM_VALIDATION_REQUIRED
    params:
      validationType: PROPERTIES
      properties:
      - name: mule.maven.plugin.version
        minVersion: 4.5.0  # Simple and clear!
      - name: app.runtime
        minVersion: 4.9.0  # Works with 4.9.0, 4.9.LTS, etc.
```

**Test Results**:
- `mule.maven.plugin.version: 4.6.1` → ✅ PASS
- `mule.maven.plugin.version: 4.4.9` → ❌ FAIL
- `app.runtime: 4.9.LTS` → ✅ PASS
- `app.runtime: 4.8.5` → ❌ FAIL

---

### Example 3: Version Range

Combine `minVersion` and `maxVersion` for range validation:

```yaml
properties:
- name: plugin.version
  minVersion: 4.5.0
  maxVersion: 5.0.0  # Must be between 4.5.0 and 5.0.0
```

**Test Results**:
- `plugin.version: 4.6.1` → ✅ PASS (within range)
- `plugin.version: 5.0.0` → ✅ PASS (at max)
- `plugin.version: 5.0.1` → ❌ FAIL (exceeds max)
- `plugin.version: 4.4.9` → ❌ FAIL (below min)

---

### Example 4: Mixed Validation

Combine version comparisons with exact matches:

```yaml
properties:
- name: mule.version
  minVersion: 4.9.0  # At least 4.9.0
- name: env.type
  expectedValue: production  # Exact match
```

---

## Enhanced Reporting

Success messages now include **actual values found** for better transparency:

**Report Output**:
```
✅ PASS: Validate Parent POM (Minimum Version)

All required POM elements found
Files validated: pom.xml

Actual Values Found:
• Parent: com.truist.eapi:MuleParentPom:3.0.5 (in pom.xml)
• Property 'mule.maven.plugin.version': 4.6.1 (in pom.xml)
• Property 'app.runtime': 4.9.LTS (in pom.xml)
```

This shows:
- ✅ What was expected (rule configuration)
- ✅ What was actually found (actual values)
- ✅ Builds confidence in validation results

---

## Backward Compatibility

✅ **100% Compatible** - All existing rules continue to work:

```yaml
# Old syntax still works
properties:
- name: version
  expectedValue: 4.9.LTS  # Exact match (unchanged)

# New syntax is optional
properties:
- name: version
  minVersion: 4.9.0  # Minimum version (new)
```

---

## Benefits

1. **Simpler Rules** - No complex regex needed
2. **Better Readability** - Clear intent (`minVersion` vs regex)
3. **More Flexible** - Support version ranges
4. **Better Transparency** - See actual vs expected values
5. **Easier Maintenance** - Simple to update version requirements

---

## Applies To

Currently supported for:
- ✅ **POM_VALIDATION_REQUIRED** (Parent, Properties, Dependencies, Plugins)

Future support planned for:
- 🔄 XML_ATTRIBUTE_EXISTS
- 🔄 JSON_VALIDATION_REQUIRED
- 🔄 MANDATORY_PROPERTY_VALUE_CHECK
