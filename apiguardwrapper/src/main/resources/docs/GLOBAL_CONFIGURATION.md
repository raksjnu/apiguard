# GLOBAL_CONFIGURATION

**Rule Type:** `GLOBAL` | **Applies To:** All Rule Types

## Overview

Aegis provides a powerful set of global configuration options that apply across various rule types. Understanding these operators, match modes, and scope controls allows you to create precise, efficient, and robust validation rules across disparate technologies.

## Match Modes

Match modes define how Aegis evaluates rule compliance across multiple file sets.

### ALL_FILES (Default)

The rule passes ONLY if **EVERY** matching file satisfies the condition.

- **Use Case**: Consistency checks (e.g., "Every Java file must have a specific license header", "No configuration file should contain hardcoded IPs").
- **Failure**: Fails if even one file violates the rule.

### ANY_FILE

The rule passes if **AT LEAST ONE** matching file satisfies the condition.

- **Use Case**: Existence checks (e.g., "The project must have a `README.md` defined somewhere", "At least one security configuration file must exist").
- **Failure**: Fails ONLY if zero files satisfy the condition.

### SUBSTRING

Simple text matching. Checks if a token exists as a literal substring within the file content.

- **Use Case**: Fast, simple checks for specific keywords, property names, or clear-text strings.

### REGEX

Content matching using Regular Expressions.

- **Use Case**: Complex pattern matching (e.g., credit card numbers, specific IP ranges, variable naming conventions with wildcards).

### EXACT (XPath)

Strict matching for XPath values. The value extracted from the XML must match the expected value character-for-character.

---

## Logic Operators

Operators modify how the rule engine interprets the results of a search or evaluation.

### failIfFound

**Type**: `Boolean` | **Default**: `false`

- **true**: The rule FAILS if the specified pattern, token, or XPath IS FOUND.
  - *Example*: "Fail if a hardcoded secret is found."
- **false**: The rule FAILS if the specified pattern, token, or XPath IS NOT FOUND.
  - *Example*: "Fail if a required framework module is missing."

### caseSensitive

**Type**: `Boolean` | **Default**: `true`

- **true**: "Error" and "error" are treated as different strings.
- **false**: Case is ignored during matching ("Error" == "error").

### negativeMatch

**Type**: `Boolean` | **Default**: `false`

- Used in some specialized token or attribute searches to invert the match logic locally within a check.

---

## Scope Control

Control exactly which files and environments a rule targets.

### environments

**Type**: `List`

Defines which environments the rule applies to.

- **Values**:
  - `["ALL"]`: Applies to all scans regardless of environment key.
  - `["DEV", "QA"]`: Applies only when the scan is triggered for these specific environment keys.
- **Best Practice**: Use this for environment-specific property checks (e.g., ensuring debug mode is only allowed in `DEV`).

### filePatterns

**Type**: `List`

Glob patterns to filter which files are scanned by the rule engine.

- **Examples**:
  - `src/main/resources/*.properties` (Java / Spring)
  - `src/main/mule/*.xml` (MuleSoft)
  - `**/*.py` (Python)
  - `package.json` (Node.js)

---

## Property Resolution

Aegis supports dynamic property resolution in rule parameters to make rules portable.

### `${property.name}` Placeholders

You can use placeholders in rule values which Aegis will attempt to resolve against:

1. Project build descriptors (`pom.xml`, `package.json`, etc.).
2. System environment variables.
3. Custom scan-time variables provided via CLI or UI.

---

## Best Practices & Guidance

### 1. Efficient "Existence" Checks

If you want to ensure your project has a specific component (like a global configuration file), don't check every file and fail if it's missing from any of them. Instead, use `matchMode: ANY_FILE`.

```yaml
- type: CODE_FILE_EXISTS
  params:
    filePatterns: ["**/security-config.xml"]
    matchMode: ANY_FILE  # Essential for existence checks!
```

### 2. Combining Validations

When checking both the presence of a key and its specific value in property files, prefer specialized rules like `MANDATORY_PROPERTY_VALUE_CHECK` which handle both logic paths more efficiently than standard substring searches.

### 3. Performance Optimization

- Prefer **SUBSTRING** over **REGEX** for static tokens. Regex engines are significantly more resource-intensive.
- Scope your **filePatterns** as narrowly as possible. Avoid `**/*` if the rule only applies to a single configuration file.

### 4. Precision with XPath

For XML validation, always use **XPath** rules instead of **Token Search**. Token searches can be fooled by comments or complex formatting, whereas XPath understands the underlying document structure.
