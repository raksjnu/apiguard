# Aegis Quick Reference Guide

This document is the **single source of truth** for all Aegis configuration parameters, rule types, and best practices.

## 1. Global Parameters
These parameters apply to almost **ALL** rule types and control file selection, property resolution, and evaluation logic.

| Parameter | Default | Description |
|:---|:---|:---|
| `filePatterns` | (Required) | List of glob patterns for target files (e.g. `["**/*.xml"]`). |
| `matchMode` | `ALL_FILES` | Controls file-level quantifier: `ALL_FILES`, `ANY_FILE`, `NONE_OF_FILES`, `AT_LEAST_N`. |
| `logic` | `AND` / `OR` | Combining logic for multiple tokens/checks. Defaults to `OR` for forbidden, `AND` for required. |
| `resolveProperties` | `true` | **Master Switch**. Enables `${property}` resolution within the check. |
| `includeLinkedConfig` | `false` | Search properties in the **linked CONFIG project** with **Priority** over the local project. |
| `resolveLinkedConfig` | `false` | Alias for `includeLinkedConfig` (supported for backward compatibility). |
| `ignoreComments` | `true` | Skip commented lines or XML comments during evaluation. |
| `environments` | `[]` | List of environments (e.g., `["PROD", "QA"]`) where this rule applies. |
| `appliesTo` | `[]` | List of project types (e.g., `["CODE", "CONFIG"]`) this rule targets. |

---

## 2. Property Resolution & Project Context
Aegis interprets the context of a project (Mule app, Java project, or configuration project) and enables sharing properties between them.

### Resolution Priority
1. **Linked CONFIG Project**: If `includeLinkedConfig: true`, Aegis searches the linked configuration project **FIRST**.
2. **Local Project**: If not found in CONFIG, it searches the project's own `.properties` or YAML files.
3. **Environment/System**: Finally, it searches system environment variables.

### Project Context & Analytics
When running rules, Aegis automatically detects and validates:
- **Environment Context**: Distinguishes between PROD, QA, and SIT profiles.
- **Dependency Integrity**: Validation of `pom.xml` versions and dependency scopes.
- **Resource Placement**: Ensuring mandatory properties exist in the correct project sub-folders.

> [!TIP]
> **Property Resolution Detail:** Use the `{PROPERTY_RESOLVED}` token in your messages to see exactly which value was used and from which source (labeled as `CONFIG - ` in reports).

---

## 3. Rule Types & Parameters

### XML Checks (`XmlGenericCheck`)
Used for validating XML structure and values using XPath. Supports the following types:
- `XML_XPATH_EXISTS`: Fails if XPath is missing.
- `XML_XPATH_OPTIONAL`: **NEW**. Passes if XPath is missing; validates only if found.
- `XML_XPATH_NOT_EXISTS`: Fails if XPath is found.
- `XML_ATTRIBUTE_EXISTS` / `NOT_EXISTS`.
- `XML_ELEMENT_CONTENT_REQUIRED` / `FORBIDDEN`.

| Parameter | Mandatory | Description |
|:---|:---|:---|
| `xpath` | No* | The primary XPath expression to evaluate. |
| `xpathExpressions` | No* | List of XPaths (legacy support). |
| `forbiddenValue` | No | Value that triggers a failure if found at the XPath. |
| `expectedValue` | No | Value that must be present at the XPath. |
| `operator` | No | Match operator: `EQ` (default), `CONTAINS`, `MATCHES`, `GTE`, `LTE`. |
| `valueType` | No | `STRING` (default), `NUMBER`, `SEMVER`. |

> [!NOTE]
> At least one of `xpath`, `requiredFields`, `minVersions`, or `exactVersions` must be provided.


### JSON Checks (`JsonGenericCheck`)
Used for validating JSON structure and values. Supports the following types:
- `JSON_VALIDATION_REQUIRED`: Fails if JSONPath is missing.
- `JSON_VALIDATION_OPTIONAL`: **NEW**. Passes if JSONPath is missing; validates only if found.
- `JSON_VALIDATION_FORBIDDEN`: Fails if JSONPath is found.

| Parameter | Mandatory | Description |
|:---|:---|:---|
| `jsonPath` | No | JsonPath expression (defaults to `$`). |
| `expectedValue` | No | Value to match (triggers `VALUE_MATCH` mode). |
| `requiredFields` | No | Map of field names to expected values. |

### Token Search (`TokenSearchCheck`)
Fast text-based searching for keywords or regex patterns.

| Parameter | Mandatory | Description |
|:---|:---|:---|
| `tokens` | Yes | List of strings or regex patterns to search for. |
| `isRegex` | No | Set to `true` if `tokens` are regular expressions. |
| `wholeWord` | No | Ensure the token matches a complete word (`\b` boundaries). |
| `wholeFile` | No | Read the entire file as a single string (useful for multi-line regex). |

---

## 4. Avoiding False Positives
The most common cause of "noise" in reports is rules running on files where they don't apply. Follow these patterns to create "silent" and precise rules.

### Pattern A: Specific File Scoping
Instead of scanning all XMLs, target only the relevant configuration files.
```yaml
# ❌ Bad: Scans everything and fails if xpath not found
filePatterns: ["src/main/mule/*.xml"]
# ✅ Good: Only scans specific config files
filePatterns: ["src/main/mule/global-config.xml", "src/main/mule/*-config.xml"]
```

### Pattern B: Using CONDITIONAL_CHECK
Use a precondition to ensure the rule ONLY executes on projects or files that actually contain the feature.
```yaml
# Only validate SOAP Version if the project actually uses the apikit-soap router
type: CONDITIONAL_CHECK
params:
  preconditions:
    - type: XML_XPATH_EXISTS
      params:
        matchMode: ANY_FILE
        filePatterns: ["src/main/mule/*.xml"]
        xpath: "//*[local-name()='config' and contains(namespace-uri(), '/mule/apikit-soap')]"
  onSuccess:
    - type: XML_XPATH_NOT_EXISTS
      params:
        # Now we can safely check for forbidden values
        forbiddenValue: "SOAP11"
        # ...
```

### Pattern C: Negative Logic for Deprecation
When forbidding an attribute, use `NOT_EXISTS` mode. This ensures that if the attribute is missing entirely, the rule **PASSES** (since the forbidden item is not there).
```yaml
type: XML_ATTRIBUTE_NOT_EXISTS
params:
  elements: ["http:listener-config"]
  forbiddenAttributes: ["deprecated-attr"]
# Result: If file has no <http:listener-config>, it PASSES silently.
```

### Pattern D: Optional Validation (New & Recommended)
Use `XML_XPATH_OPTIONAL` or `JSON_VALIDATION_OPTIONAL` for "Validate only if present" logic. This is more efficient than `CONDITIONAL_CHECK` and prevents report noise.
```yaml
# Only validate JKS if jce-config is present
type: XML_XPATH_OPTIONAL
params:
  xpath: "//*[local-name()='jce-config']/@type"
  expectedValue: "JKS"
  filePatterns: ["**/*.xml"]
# Result: Files without <jce-config> pass silently.
```

### Pattern E: Conditional Existence Checks
Aegis allows you to precisely control rule execution based on the existence of specific elements. This is commonly used in `CONDITIONAL_CHECK` to skip rules when certain "feature flags" (like a batch job) are found.

Using `XML_XPATH_NOT_EXISTS` without a `forbiddenValue` will fail if the element is found in the XML, effectively allowing you to say "Run this check ONLY IF this other element is NOT present."

**Example (Excluding Batch Projects):**
```yaml
type: CONDITIONAL_CHECK
params:
  preconditions:
    - type: XML_XPATH_NOT_EXISTS
      params:
        xpath: "//*[local-name()='job']" # Skips if <batch:job> exists
  # ...
```

### Pattern F: Enhanced Reporting Context
Aegis can be configured to provide deep context in failure reports (like showing the exact hardcoded version string) entirely through YAML configuration:

1.  **Direct Pointing**: Set the `xpath` to point directly to the data of interest (e.g., the `<version>` text).
2.  **Smart Filtering**: Use an XPath predicate to select only the invalid nodes (e.g., `not(contains(., '${'))`).
3.  **Capture Mode**: Use `XML_ELEMENT_CONTENT_FORBIDDEN` with `forbiddenValue: ".+"`.
4.  **Automatic Detail**: Because every match is an intentional failure, Aegis captures the matched text and displays it in the **"Items Found"** section, making the issue immediately obvious to the developer.

**Example:**
```yaml
type: XML_ELEMENT_CONTENT_FORBIDDEN
params:
  xpath: "//*[local-name()='version'][not(contains(., '${'))]"
  forbiddenValue: ".+"
  operator: "MATCHES"
  isRegex: true
```


---

## 5. Message Tokens Reference
Use these tokens in `successMessage` and `errorMessage` for dynamic reporting.

- `{RULE_ID}`: The ID of the rule.
- `{CHECKED_FILES}`: List of files scanned.
- `{MATCHING_FILES}`: List of files that passed the criteria.
- `{FOUND_ITEMS}`: Specific forbidden items found (e.g., tokens, XPath results).
- `{DEFAULT_MESSAGE}`: Full technical details of the result.
- `{PROPERTY_RESOLVED}`: Detailed trail of resolved property placeholders.
