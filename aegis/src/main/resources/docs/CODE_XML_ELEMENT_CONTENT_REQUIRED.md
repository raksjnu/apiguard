# XML_ELEMENT_CONTENT_REQUIRED

**Rule Type:** `CODE` - **Applies To:** XML configuration files

## Overview

Validates that XML elements contain **required content strings or tokens**. This rule **fails** if the required tokens are **NOT found** within the element's text content. It is ideal for enforcing descriptive messages, documentation standards, or specific data formats inside XML tags.

## Use Cases

- Ensure log messages or display names contain descriptive keywords.
- Validate documentation or comment tags for specific sections (e.g., "Author", "Version").
- Check that configuration values follow a specific format (e.g., semantic versioning).
- Enforce organizational content standards in shared XML descriptors.

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePatterns` | List<String> | Glob patterns to match XML files |
| `elementContentPairs` | List<Map> | Element names paired with their required content tokens |

#### Element Content Pair Map Structure

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `element` | String | Yes | XML element name (can be local-name or prefixed) |
| `requiredTokens` | List<String> | Yes | Tokens that must be present in the element's body |

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `caseSensitive` | Boolean | `true` | Case sensitivity for content matching |
| `requireAll` | Boolean | `true` | If `true`, ALL tokens must be found. If `false`, at least ONE |
| `resolveProperties` | Boolean | `false` | Enable `${...}` resolution |
| `resolveLinkedConfig`| Boolean | `false` | Resolve from linked CONFIG project |
| `includeLinkedConfig`| Boolean | `false` | Scan files in linked CONFIG project |

## Configuration Examples

### Example 1: Validate Message Quality
Ensure that elements used for logging or reporting are descriptive enough.

```yaml
- id: "RULE-XML-CONTENT-REQ-01"
  name: "Descriptive Logger Messages"
  checks:
    - type: XML_ELEMENT_CONTENT_REQUIRED
      params:
        filePatterns: ["**/*.xml"]
        elementContentPairs:
          - element: "logger"
            requiredTokens: ["Flow", "Request", "Response", "Status"]
        requireAll: false  # Pass if at least one of these is mentioned
```

### Example 2: Enforce Documentation Standards
Check that a "description" or "note" tag contains required metadata headers.

```yaml
- id: "RULE-XML-DOC-POLICY"
  name: "Documentation Header Policy"
  checks:
    - type: XML_ELEMENT_CONTENT_REQUIRED
      params:
        filePatterns: ["src/main/resources/*.xml"]
        elementContentPairs:
          - element: "note"
            requiredTokens: ["Author:", "Last-Updated:", "Business-Unit:"]
        requireAll: true  # All headers must be present
```

### Example 3: Regex Format Validation
Verify that a version tag follows a specific semantic versioning pattern.

```yaml
- id: "RULE-XML-VERSION-FORMAT"
  name: "Version Pattern Validation"
  checks:
    - type: XML_ELEMENT_CONTENT_REQUIRED
      params:
        filePatterns: ["pom.xml", "manifest.xml"]
        matchMode: REGEX
        elementContentPairs:
          - element: "version"
            requiredTokens: ["^\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?$"]
```

## Error Messages

```
config.xml: Element 'logger' is missing required tokens: Flow, Request, Response
manifest.xml: Element 'version' content does not match pattern: ^\d+\.\d+\.\d+(-SNAPSHOT)?$
```

## Best Practices

- **Use caseSensitive: false for flexibility**: For documentation or log messages, case sensitivity often leads to false negatives.
- **REGEX for Strict Formats**: Use REGEX mode when the *order* or *format* matters (e.g., dates, versions, IDs).
- **Combine with existence**: This rule only checks content *on elements it finds*. If the element is missing entirely, this rule won't trigger. Combine with **[XML_XPATH_EXISTS](XML_XPATH_EXISTS.md)** to ensure the element exists first if needed.

## Related Rule Types

- **[XML_ELEMENT_CONTENT_FORBIDDEN](XML_ELEMENT_CONTENT_FORBIDDEN.md)** - Opposite: ensures tokens do NOT exist in the content.
- **[XML_XPATH_EXISTS](XML_XPATH_EXISTS.md)** - Check for existence and hierarchy using XPath.
