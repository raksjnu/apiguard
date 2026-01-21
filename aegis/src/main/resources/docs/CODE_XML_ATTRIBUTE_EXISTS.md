# XML_ATTRIBUTE_EXISTS

**Rule Type:** `CODE` - **Applies To:** XML configuration files

## Overview

Validates that **required XML attributes exist** on specified elements, optionally checking for specific values. This is a highly flexible rule supporting simple existence checks, specific value matching, and multi-attribute set validation.

## Use Cases

- Ensure configuration elements have required identifiers (name, ID).
- Validate security settings (cipher suites, protocols, encrypted="true").
- Enforce naming conventions for specific components.
- Validate infrastructure settings (hosts, ports, timeouts).
- Ensure required metadata or schema references are present.

## Validation Modes

This rule supports **three distinct modes**:

1. **Simple Attribute Existence**: Check if attributes exist (any value).
2. **Attribute Value Pairs**: Validate specific attribute values on elements.
3. **Element Attribute Sets**: Validate multiple attributes on the same element instance.

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePatterns` | List<String> | Glob patterns to match XML files (e.g., `src/**/*.xml`) |

### Mode-Specific Parameters

You must configure **at least one** of these modes:

#### Mode 1: Simple Attribute Existence

| Parameter | Type | Description |
|-----------|------|-------------|
| `elements` | List<String> | XML element names to check |
| `attributes` | List<String> | Attributes that MUST exist on those elements |

#### Mode 2: Attribute Value Pairs

| Parameter | Type | Description |
|-----------|------|-------------|
| `attributeValuePairs` | List<Map> | List of element-attribute-value triplets |

#### Mode 3: Element Attribute Sets

| Parameter | Type | Description |
|-----------|------|-------------|
| `elementAttributeSets` | List<Map> | List of element configurations with multiple required attributes |

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `caseSensitive` | Boolean | `true` | Case sensitivity for value matching |
| `resolveProperties` | Boolean | `false` | Enable `${...}` resolution |
| `resolveLinkedConfig`| Boolean | `false` | Resolve from linked CONFIG project |
| `includeLinkedConfig`| Boolean | `false` | Scan files in linked CONFIG project |

## Configuration Examples

### Example 1: Simple Existence
Check that a "connector" element has both a `name` and a `type` attribute (any values).

```yaml
- id: "RULE-XML-ATTR-01"
  name: "Connector Metadata Required"
  checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: ["**/config/*.xml"]
        elements: ["connector"]
        attributes: ["name", "type"]
```

### Example 2: Attribute Value Validation
Ensure that any `security-manager` element has the `enabled` attribute set to `true`.

```yaml
- id: "RULE-XML-ATTR-VAL-01"
  name: "Security Manager Integration"
  checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: ["src/main/resources/*.xml"]
        attributeValuePairs:
          - element: "security-manager"
            attribute: "enabled"
            expectedValue: "true"
```

### Example 3: Multi-Attribute Set (Security)
Validate that a `connection` element uses the correct port AND protocol.

```yaml
- id: "RULE-XML-ATTR-SET-SEC"
  name: "Secure Connection Standard"
  errorMessage: "Insecure connection access point found.\n{DEFAULT_MESSAGE}"
  checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: ["**/*.xml"]
        elementAttributeSets:
          - element: "connection"
            attributes:
              protocol: "HTTPS"
              port: "443"
              secure: "true"
```

### Example 4: Property Resolution Support
Allow property placeholders (e.g., `${env.port}`) to count as a valid match for a required port.

```yaml
- id: "RULE-XML-ATTR-PROP"
  name: "Resolved Port Configuration"
  checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: ["**/*.xml"]
        propertyResolution: true
        attributeValuePairs:
          - element: "server-config"
            attribute: "port"
            expectedValue: "8080"
```

## Error Messages

```
Element 'connector' missing attribute 'type' in file: config/main.xml
Element 'security-manager' with attribute 'enabled'='true' not found in file: auth.xml
```

## Best Practices

- **Use Mode 3 for Dependencies**: If an element's validity depends on multiple attributes (e.g., host + port), use `elementAttributeSets` to ensure both are correct on the *same* element instance.
- **Enable propertyResolution for Environments**: Most enterprise projects use property placeholders; enabling this prevents false negatives for required values.
- **Path Specificity**: Use `filePatterns` to target only the relevant configuration files to improve performance and reduce noise.

## Solution Patterns and Technology References

| Technology | Best Practice Goal | Key Elements | Attributes Checked |
| :--- | :--- | :--- | :--- |
| **🐎 MuleSoft 4.X** | Flow Identification | `flow`, `sub-flow` | `name`, `doc:id` |
| **⚡ TIBCO BW 6.X** | Process Configuration | `bpws:process` | `name`, `targetNamespace` |
| **⚡ TIBCO BW 5.X** | Activity Naming | `pd:startType` | `name` |
| **☕ Java/Maven** | Dependency Locking | `dependency` | `version` |

### 🐎 MuleSoft 4.X Patterns

```yaml
id: "MULE-FLOW-ATTRS"
name: "Mule Flow Identification"
description: "Flows must have name and doc:id attributes"
checks:
  - type: XML_ATTRIBUTE_EXISTS
    params:
      filePatterns: ["src/main/mule/**/*.xml"]
      elements: ["flow", "sub-flow", "error-handler"]
      attributes: ["name", "doc:id"]
```

### ☕ Java / Maven Patterns

```yaml
id: "JAVA-MAVEN-DEP-VERSION"
name: "Maven Dependency Version Enforced"
description: "Dependencies in this project must explicitly state versions"
checks:
  - type: XML_ATTRIBUTE_EXISTS
    params:
      filePatterns: ["pom.xml"]
      elements: ["dependency"]
      attributes: ["version"]
```

## Related Rule Types

- **[XML_ATTRIBUTE_NOT_EXISTS](XML_ATTRIBUTE_NOT_EXISTS.md)** - Opposite: ensures attributes do NOT exist.
- **[XML_XPATH_EXISTS](XML_XPATH_EXISTS.md)** - For advanced structure or nested path validation.
- **[XML_ELEMENT_CONTENT_REQUIRED](XML_ELEMENT_CONTENT_REQUIRED.md)** - Validates tag body text instead of attributes.
