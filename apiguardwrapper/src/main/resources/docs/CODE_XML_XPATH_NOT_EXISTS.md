# XML_XPATH_NOT_EXISTS

**Rule Type:** `CODE` - **Applies To:** XML configuration files

## Overview

Validates that **forbidden XPath expressions do NOT match** in XML files. This rule **fails** if any forbidden XPath expression **DOES** find matching nodes. It is used to block deprecated components, anti-patterns, or insecure configurations.

## Use Cases

- Prevent usage of deprecated XML elements or custom tags.
- Block specific insecure configurations (e.g., protocol="HTTP" instead of "HTTPS").
- Enforce architectural constraints (e.g., "No direct database calls from the presentation layer").
- Disallow anti-patterns or redundant configurations.

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePatterns` | List<String> | Glob patterns to match XML files |
| `xpathExpressions` | List<Map> | List of XPath expressions that should NOT match |

#### XPath Expression Map Structure

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `xpath` | String | Yes | The XPath expression to evaluate |
| `failureMessage` | String | No | Custom message if XPath matches (forbidden) |

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `propertyResolution` | Boolean | `false` | Enable `${property}` placeholder resolution |

## Configuration Examples

### Example 1: Block Deprecated Components
Prevents usage of legacy connectors or custom XML tags that are no longer supported.

```yaml
- id: "RULE-XML-XPATH-FORBIDDEN-01"
  name: "No Legacy Transports"
  description: "Prevent usage of deprecated transport elements"
  enabled: true
  severity: HIGH
  checks:
    - type: XML_XPATH_NOT_EXISTS
      params:
        filePatterns:
          - "**/*.xml"
        xpathExpressions:
          - xpath: "//*[local-name()='legacy-transport']"
            failureMessage: "Deprecated 'legacy-transport' found - please migrate to the new standard"
```

### Example 2: Prevent Hardcoded Environment Values
Ensures that environment-specific settings (like hostnames) are property-driven and not hardcoded to `localhost`.

```yaml
- id: "RULE-XML-XPATH-FORBIDDEN-02"
  name: "No Hardcoded Localhost"
  description: "Prevent hardcoded localhost in service configurations"
  enabled: true
  severity: MEDIUM
  checks:
    - type: XML_XPATH_NOT_EXISTS
      params:
        filePatterns:
          - "src/main/resources/**/*.xml"
        xpathExpressions:
          - xpath: "//service[@host='localhost']"
            failureMessage: "Hardcoded 'localhost' found - use property placeholders"
```

### Example 3: Enforce Layered Architecture
Prevents direct database access from high-level API or presentation layer configurations.

```yaml
- id: "RULE-XML-XPATH-FORBIDDEN-ARCH"
  name: "Layered Architecture Compliance"
  description: "API layers cannot contain direct database listeners or operations"
  enabled: true
  severity: HIGH
  checks:
    - type: XML_XPATH_NOT_EXISTS
      params:
        filePatterns:
          - "**/api-*.xml"
        xpathExpressions:
          - xpath: "//db:select | //db:insert | //db:update | //db:delete"
            failureMessage: "Direct database operations forbidden in API layer - use the persistence layer"
```

## Error Messages

```
config.xml: Deprecated 'legacy-transport' found - please migrate to the new standard (found 2 occurrence(s))
api-main.xml: Direct database operations forbidden in API layer - use the persistence layer (found 1 occurrence(s))
```

## Best Practices

- **Use predicates for precise blocking**: Instead of blocking an entire element, use predicates like `//config[@secure='false']` to block only the insecure variants.
- **Fail with clear instructions**: Always provide a `failureMessage` that explains the preferred alternative.
- **Architecture over Code**: Use path-based `filePatterns` to enforce different restrictions on different project layers (e.g., `src/web/*.xml` vs `src/service/*.xml`).

## Related Rule Types

- **[XML_XPATH_EXISTS](XML_XPATH_EXISTS.md)** - Opposite: ensures XPath DOES match.
- **[XML_ATTRIBUTE_NOT_EXISTS](XML_ATTRIBUTE_NOT_EXISTS.md)** - Simpler attribute validation.
- **[XML_ELEMENT_CONTENT_FORBIDDEN](XML_ELEMENT_CONTENT_FORBIDDEN.md)** - Validate forbidden text content inside an element.
