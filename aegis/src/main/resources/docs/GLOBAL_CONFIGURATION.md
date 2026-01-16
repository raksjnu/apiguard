# GLOBAL_CONFIGURATION

**Rule Type:** `GLOBAL` - **Applies To:** All Rule Types

## Overview

Aegis provides a powerful set of global configuration options that apply across various rule types. Understanding these operators, match modes, and scope controls allows you to create precise, efficient, and robust validation rules.

This guide details the universal parameters available for fine-tuning your rules.

## Match Modes

Match modes define *how* MuleGuard evaluates rule compliance across multiple files.

### <span style="color:#0078d4">ALL_FILES</span> (Default)
**Behavior:** The rule passes ONLY if **EVERY** matching file satisfies the condition.
- **Use Case:** Consistency checks (e.g., "Every Log component must have a specific category", "No file should contain hardcoded IPs").
- **Failure:** Fails if *even one* file violates the rule.

### <span style="color:#0078d4">ANY_FILE</span>
**Behavior:** The rule passes if **AT LEAST ONE** matching file satisfies the condition.
- **Use Case:** implementation checks (e.g., "The project must have an `ErrorHandler` defined *somewhere*", "Global Error Handler must exist").
- **Failure:** Fails ONLY if *zero* files satisfy the condition.

### <span style="color:#0078d4">SUBSTRING</span>
**Behavior:** Simple text matching. Checks if a token exists as a literal substring within the file content.
- **Use Case:** Fast, simple checks for specific keywords or tokens.

### <span style="color:#0078d4">REGEX</span>
**Behavior:** content matching using Regular Expressions.
- **Use Case:** Complex pattern matching (e.g., credit card numbers, specific IP ranges, variable naming conventions).

### <span style="color:#0078d4">EXACT (XPath)</span>
**Behavior:** Strict matching for XPath values. The value in the XML must match the expected value character-for-character.

---

## Logic Operators

Operators modify how the rule interprets the search results.

### <span style="color:#0078d4">failIfFound</span>
**Type:** `Boolean` | **Default:** `false` (usually)
- **true**: The rule **FAILS** if the specified pattern/token/XPath **IS FOUND**.
    - *Example:* "Fail if `jce-encrypt` token is found."
- **false**: The rule **FAILS** if the specified pattern/token/XPath IS **NOT FOUND**.
    - *Example:* "Fail if `http:listener` is NOT found."

### <span style="color:#0078d4">caseSensitive</span>
**Type:** `Boolean` | **Default:** `true`
- **true**: 'Error' and 'error' are treated as different strings.
- **false**: Case is ignored during matching ('Error' == 'error').

### <span style="color:#0078d4">negativeMatch</span>
**Type:** `Boolean` | **Default:** `false`
- Used in some token searches to invert the match logic locally.

---

## Scope Control

Control *where* and *when* the rule executes.

### <span style="color:#0078d4">environments</span>
**Type:** `List<String>`
- Defines which environments the rule applies to.
- **Values:**
    - `["ALL"]`: Applies to all scans.
    - `["DEV", "QA"]`: Applies only when scanning specific environment configurations.
- **Best Practice:** Use this for environment-specific property checks (e.g., ensuring `mock.endpoints=true` only in DEV).

### <span style="color:#0078d4">filePatterns</span>
**Type:** `List<String>`
- Glob patterns to filter which files are scanned.
- **Examples:**
    - `src/main/mule/*.xml` (All Mule XMLs)
    - `**/*-config.xml` (Recursive search for config XMLs)
    - `pom.xml` (Project Object Model)

---

## Property Resolution

MuleGuard supports dynamic property resolution in rule parameters.

### <span style="color:#0078d4">${property.name}</span>
- **Usage:** You can use placeholders in rule values.
- **Resolution:** MuleGuard attempts to resolve these against:
    1.  `mule-artifact.json` properties
    2.  `pom.xml` properties
    3.  Scan-time environment variables

---

## Best Practices & Guidance

### 1. Efficient "Existence" Checks
**Scenario:** You want to ensure your project has a `GlobalErrorHandler`.
**Inefficient:** Checking every file and failing if it's missing (will fail on every file except the one that has it).
**Efficient:** Use <span style="color:#0078d4">matchMode: ANY_FILE</span>.
```yaml
- type: XML_XPATH_EXISTS
  params:
    filePatterns: ["src/main/mule/*.xml"]
    matchMode: ANY_FILE  <-- Critical!
    xpathExpressions:
      - xpath: "//error-handler[@name='Global_Error_Handler']"
```

### 2. Combining Validations
**Scenario:** You need to check if a property exists AND has a specific value.
**Guidance:** Use **MANDATORY_PROPERTY_VALUE_CHECK** which handles both implicitly.

### 3. Performance
- Prefer **SUBSTRING** over **REGEX** whenever possible. Regex is powerful but slower.
- Scope your **filePatterns** narrowly. Don't scan `**/*.xml` if you only care about `pom.xml`.

### 4. Zero-False-Positives
- Use **XPath** for XML instead of Token Search.
    - Token search for `<logger` might match a comment `<!-- <logger ... -->`.
    - XPath `//logger` guarantees it is a real XML element.
