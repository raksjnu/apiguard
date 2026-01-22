# GLOBAL CONFIGURATION

**Rule Type:** `GLOBAL` | **Applies To:** All Rule Types

## Overview

Aegis provides a powerful set of global configuration options that apply across various rule types. Understanding these operators, match modes, and scope controls allows you to create precise, efficient, and robust validation rules.

## Match Modes

Match modes define how Aegis evaluates rule compliance across multiple file sets.

### ALL_FILES (Default)

The rule passes ONLY if **EVERY** matching file satisfies the condition.

- **Use Case**: Consistency checks (e.g., "Every Java file must have a specific license header").
- **Failure**: Fails if even one file violates the rule.

### ANY_FILE

The rule passes if **AT LEAST ONE** matching file satisfies the condition.

- **Use Case**: Existence checks (e.g., "The project must have a `README.md` defined somewhere").
- **Failure**: Fails ONLY if zero files satisfy the condition.

### SUBSTRING

Simple text matching. Checks if a token exists as a literal substring within the file content.

- **Use Case**: Fast, simple checks for specific keywords or property names.

### REGEX

Content matching using Regular Expressions.

- **Use Case**: Complex pattern matching (e.g., credit card numbers, IP ranges).

### EXACT (XPath)

Strict matching for XPath values. The value extracted from the XML must match the expected value character-for-character.

---

## Logic Operators

Operators modify how the rule engine interprets the results of a search or evaluation.

### failIfFound

**Type**: `Boolean` | **Default**: `false`

- **true**: The rule FAILS if the specified pattern, token, or XPath IS FOUND.
- **false**: The rule FAILS if the specified pattern, token, or XPath IS NOT FOUND.

### caseSensitive

**Type**: `Boolean` | **Default**: `true`

- **true**: "Error" and "error" are treated as different strings.
- **false**: Case is ignored during matching ("Error" == "error").

### ignoreComments

**Type**: `Boolean` | **Default**: `true`

- **true**: Skip commented lines during selection. This is the recommended setting to avoid false positives.
- **false**: Include comments in the search. Required only for specialized rules (e.g., Munit detection).

### negativeMatch

**Type**: `Boolean` | **Default**: `false`

- Used in some specialized token or attribute searches to invert the match logic locally within a check.

---

## Scope Control

Control exactly which files and environments a rule targets.

### scope (Metadata)

**Type**: `String` | **Default**: `null`

Defines the organizational or architectural scope of the rule. By default, the `scope` is `null` and is not displayed in reports.

### environments

**Type**: `List` | **Default**: `[]`

Defines which environments the rule applies to.

- **Values**:
  - `["ALL"]`: Applies to all scans regardless of environment key.
  - `["DEV", "QA"]`: Applies only when the scan is triggered for these specific keys.
- **Best Practice**: Use this for environment-specific property checks.

### filePatterns

**Type**: `List` | **Default**: (Required)

Glob patterns to filter which files are scanned by the rule engine.

- **Examples**:
  - `src/main/resources/*.properties`
  - `src/main/mule/*.xml`

---

## Property Resolution

Aegis supports dynamic property resolution in rule parameters.

### `${property.name}` Placeholders

You can use placeholders in rule values which Aegis will resolve against:

1. Project build descriptors (`pom.xml`, `package.json`, etc.).
2. System environment variables.
3. Custom scan-time variables provided via CLI or UI.
4. **Linked Configuration Projects**: Resolve properties from a separate CONFIG project when using `resolveLinkedConfig: true`.

### Cross-Project Linking

Aegis automatically links projects when scanned together. If a project is identified as `CODE` and another as `CONFIG` within the same scan session, the `CODE` project can "see" properties defined in the `CONFIG` project.

---

## Cross-Project Resolution Flags

| Parameter             | Type    | Default | Description                                                   |
|-----------------------|---------|---------|---------------------------------------------------------------|
| `resolveProperties`   | Boolean | `true`  | Resolves `${...}` within the local project.                  |
| `ignoreComments`      | Boolean | `true`  | Skip commented lines during selection.                        |
| `resolveLinkedConfig` | Boolean | `false` | Resolves `${...}` from the linked configuration project.      |
| `includeLinkedConfig` | Boolean | `false` | Includes linked configuration files in the current search.    |

---

## Best Practices & Guidance

### 1. Efficient "Existence" Checks

If you want to ensure your project has a specific component, use `matchMode: ANY_FILE`.

```yaml
- type: CODE_FILE_EXISTS
  params:
    filePatterns: ["**/security-config.xml"]
    matchMode: ANY_FILE
```

### 2. Performance Optimization

- Prefer **SUBSTRING** over **REGEX** for static tokens.
- Scope your **filePatterns** as narrowly as possible. Avoid `**/*` if the rule only applies to a single file.

### 3. Precision with XPath

For XML validation, always use **XPath** rules instead of **Token Search**. XPath understands the underlying document structure.

---

## Message Customization

Aegis allows you to customize the success and error messages for each rule using placeholders.

### Supported Tokens

| Token                | Description                                                                 |
|:---------------------|:----------------------------------------------------------------------------|
| `{RULE_ID}`          | The ID of the rule being executed.                                          |
| `{DEFAULT_MESSAGE}`  | The technical details of the check result (Alias for `{CORE_DETAILS}`).    |
| `{CHECKED_FILES}`    | A comma-separated list of files that were scanned.                          |
| `{FOUND_ITEMS}`      | A list of forbidden items found, causing the rule to fail.                  |

### Newline Formatting

You can use `\n` in your messages to create line breaks.

**Example:**

```yaml
successMessage: "Validation passed!\n{DEFAULT_MESSAGE}"
errorMessage: "Validation failed.\n{DEFAULT_MESSAGE}"
```
