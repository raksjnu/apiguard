# XML_ATTRIBUTE_NOT_EXISTS

**Rule Type:** `CODE` - **Applies To:** XML configuration files

## Overview

Validates that **forbidden XML attributes do NOT exist** on specified elements. This rule **fails** if any of the specified forbidden attributes are found on the target elements. It is commonly used for deprecation cleanup and blocking unsafe configuration flags.

## Use Cases

- Detect and remove deprecated attributes (e.g., legacy tracking flags).
- Enforce cleanup of temporary or debug configurations (e.g., `debug="true"`).
- Prevent usage of insecure or prohibited attribute settings.
- Standardize components by blocking non-standard configuration keys.

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePatterns` | List<String> | Glob patterns to match XML files |
| `elements` | List<String> | List of XML element names to scan |
| `forbiddenAttributes` | List<String> | List of attributes that must NOT be present |

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `shouldIgnorePath` | Internal | N/A | Automatically ignores build directories like `target/`, `bin/`, etc. |

## Configuration Examples

### Example 1: Remove Legacy Tracking
Ensure that no message processing elements contain legacy application tracking attributes.

```yaml
- id: "RULE-XML-ATTR-FORBIDDEN-LEGACY"
  name: "Remove Forbidden Legacy Attributes"
  checks:
    - type: XML_ATTRIBUTE_NOT_EXISTS
      params:
        filePatterns: ["src/**/*.xml"]
        elements:
          - "request-handler"
          - "transform-component"
        forbiddenAttributes:
          - "fromApplicationCode"
          - "toApplicationCode"
          - "legacyFlag"
```

### Example 2: Block Debug/Test Modes
Ensure that configuration elements do not have debug mode enabled in the committed code.

```yaml
- id: "RULE-XML-ATTR-FORBIDDEN-DEBUG"
  name: "No Debug Mode in XML"
  severity: HIGH
  checks:
    - type: XML_ATTRIBUTE_NOT_EXISTS
      params:
        filePatterns: ["src/main/resources/*.xml"]
        elements: ["app-config", "connector-config"]
        forbiddenAttributes: ["debug", "test-mode", "verbose-logging"]
```

## Error Messages

```
Forbidden attribute 'fromApplicationCode' found on element 'request-handler' in file: src/main/resources/config.xml
Forbidden attribute 'debug' found on element 'connector-config' in file: src/main/resources/connectors.xml
```

## Best Practices

- **Layered Cleanup**: Use this rule during platform migrations to systematically identify and remove old configuration keys that are no longer referenced.
- **Fail on Side-Loads**: Block attributes that might bypass standard security controls (e.g., `bypass-auth`, `ignore-ssl`).
- **Combine with XPATH**: For more complex conditions (e.g., "block attribute X only if element has parent Y"), use **[XML_XPATH_NOT_EXISTS](XML_XPATH_NOT_EXISTS.md)** instead.

## Related Rule Types

- **[XML_ATTRIBUTE_EXISTS](XML_ATTRIBUTE_EXISTS.md)** - Opposite: ensures attributes DO exist.
- **[XML_XPATH_NOT_EXISTS](XML_XPATH_NOT_EXISTS.md)** - Block complex structures using XPath.
