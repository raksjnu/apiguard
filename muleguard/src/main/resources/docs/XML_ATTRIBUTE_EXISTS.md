# XML_ATTRIBUTE_EXISTS

## Overview

Validates that **required XML attributes exist** on specified elements. This rule is highly flexible and **fails** if required attributes or values are **NOT found**.

## Use Cases

- Ensure flow elements have required attributes (name, doc:id)
- Validate configuration completeness (cipher suites, protocols)
- Enforce naming conventions (e.g., specific values for specific attributes)
- Check that elements contain specific attribute combinations

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePatterns` | List<String> | Glob patterns to match XML files (e.g., `src/main/mule/*.xml`) |

### Modes (Must configure at least one)

This rule supports three distinct validation modes. You can combine them in a single check.

#### Mode 1: Simple Attribute Existence
Checks if a list of `elements` each contain all the specified `attributes`.

| Parameter | Type | Description |
|-----------|------|-------------|
| `elements` | List<String> | XML element names to check |
| `attributes` | List<String> | List of attributes that MUST exist on the elements |

#### Mode 2: Attribute Value Pairs
Checks if elements contain specific attributes with specific **values**.

| Parameter | Type | Description |
|-----------|------|-------------|
| `attributeValuePairs` | List<Map> | List of value checks |

**Map Structure:**
- `element`: Element name
- `attribute`: Attribute name
- `expectedValue`: The required value

#### Mode 3: Element Attribute Sets
Checks if an element contains a specific **combination** of attributes and values.

| Parameter | Type | Description |
|-----------|------|-------------|
| `elementAttributeSets` | List<Map> | List of complex attribute set checks |

**Map Structure:**
- `element`: Element name
- `attributes`: Map of `attributeName` -> `expectedValue`

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `caseSensitive` | Boolean | `true` | Case sensitivity for values |
| `propertyResolution` | Boolean | `false` | If `true`, allows `${property}` placeholders as valid matches for expected values |
| `shouldIgnorePath` | Internal | N/A | Automatically ignores target, bin, .git, etc. |

## Configuration Examples

### Example 1: Simple Existence (Mode 1)
Ensure `crypto:jce-config` has a `type` attribute.

```yaml
- id: "RULE-013"
  name: "Check for 'type' attribute in crypto"
  enabled: true
  severity: HIGH
  checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: ["src/main/mule/*.xml"]
        elements: ["crypto:jce-config"]
        attributes: ["type"]
```

### Example 2: Attribute Value Validation (Mode 3)
Ensure IBM MQ Connector uses a specific Cipher Suite. Note strict value checking.

```yaml
- id: "RULE-005" 
  name: "Validate IBM MQ Connector Cipher Suite"
  enabled: true
  severity: HIGH
  checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: ["src/main/mule/*.xml"]
        propertyResolution: true
        elementAttributeSets:
          - element: "ibm:connection"
            attributes:
              cipherSuite: "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"
```

### Example 3: Property Placeholder Support
Allow attributes to be set via property placeholders (e.g., `${http.port}`).

```yaml
- id: "RULE-015" 
  name: "Validate HTTP Listener Path"
  enabled: true
  severity: HIGH
  checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: ["src/main/mule/*.xml"]
        propertyResolution: true
        elementAttributeSets:
          - element: "http:listener"
            attributes:
              path: "/test"
```

## Error Messages

```
Element 'crypto:jce-config' missing attribute 'type' in file: src/main/mule/global.xml
Element 'ibm:connection' with required attributes {cipherSuite=TLS...} not found in file: src/main/mule/mq.xml
```

## Related Rule Types

- **[XML_ATTRIBUTE_NOT_EXISTS](XML_ATTRIBUTE_NOT_EXISTS.md)** - Opposite: ensures attributes do NOT exist
