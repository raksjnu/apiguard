# Enhanced Reporting - Actual Values Display

## Overview

Aegis reports now show **both expected AND actual values** for better transparency and confidence in validation results.

---

## What's New

### Before
Reports only showed rule configuration (what was expected):
```
✅ PASS: Validate Parent POM
All required POM elements found
Files validated: pom.xml
```

### After
Reports show both expected AND actual values:
```
✅ PASS: Validate Parent POM (Minimum Version)

All required POM elements found
Files validated: pom.xml

Actual Values Found:
• Parent: com.truist.eapi:MuleParentPom:3.0.5 (in pom.xml)
• Property 'mule.maven.plugin.version': 4.6.1 (in pom.xml)
• Property 'app.runtime': 4.9.LTS (in pom.xml)
• Dependency: com.truist.eapi.crypto:eapimuleutilities:2.0.1 (in pom.xml)
```

---

## Benefits

1. **Better Confidence** - See exactly what was found vs expected
2. **Easier Debugging** - Quickly identify version mismatches
3. **Better Transparency** - No guessing about actual values
4. **Audit Trail** - Clear record of what was validated

---

## Supported Rule Types

Currently supported for:
- ✅ **POM_VALIDATION_REQUIRED**
  - Parent POM (groupId, artifactId, version)
  - Properties (name, value)
  - Dependencies (groupId, artifactId, version)
  - Plugins (groupId, artifactId, version)

Future support planned for:
- 🔄 XML_XPATH_EXISTS (matched elements, content)
- 🔄 XML_ATTRIBUTE_EXISTS (attribute values)
- 🔄 JSON_VALIDATION_REQUIRED (field values)
- 🔄 MANDATORY_PROPERTY_VALUE_CHECK (property values)

---

## Examples

### Parent POM Validation
```
Actual Values Found:
• Parent: com.truist.eapi:MuleParentPom:3.0.5 (in pom.xml)
```

### Property Validation
```
Actual Values Found:
• Property 'mule.maven.plugin.version': 4.6.1 (in pom.xml)
• Property 'app.runtime': 4.9.LTS (in pom.xml)
• Property 'cicd.mule.version': 4.9.LTS (in pom.xml)
```

### Dependency Validation
```
Actual Values Found:
• Dependency: com.truist.eapi.crypto:eapimuleutilities:2.0.1 (in pom.xml)
• Dependency: org.mule.connectors:mule-http-connector:1.7.3 (in pom.xml)
```

### Plugin Validation
```
Actual Values Found:
• Plugin: org.mule.tools.maven:mule-maven-plugin:4.6.1 (in pom.xml)
```

---

## Failure Messages

Failure messages also include actual values for clarity:

```
❌ FAIL: Validate POM Properties (Minimum Versions)

POM validation failures:
• Property 'mule.maven.plugin.version' version too low in pom.xml: expected >= '4.5.0', got '4.4.9'
• Property 'app.runtime' version too low in pom.xml: expected >= '4.9.0', got '4.8.5'
```

This clearly shows:
- What was expected (`>= 4.5.0`)
- What was actually found (`4.4.9`)
- Why it failed (too low)
