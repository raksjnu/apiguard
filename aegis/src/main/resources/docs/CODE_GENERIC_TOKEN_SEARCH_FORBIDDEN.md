# GENERIC_TOKEN_SEARCH_FORBIDDEN

**Rule Type:** `CODE` - **Applies To:** Any text-based source files

## Overview

Validates that **forbidden tokens or patterns do NOT exist** in files matching specified patterns. This rule **fails** if any of the specified forbidden tokens are **found** within the file content. It is a powerful tool for blocking deprecated functions, hardcoded credentials, and unsafe coding patterns.

## Use Cases

- Prevent usage of deprecated APIs, legacy functions, or insecure libraries.
- Block hardcoded credentials (passwords, API keys, secrets) in source and config files.
- Disallow specific imports or dependencies that violate architectural standards.
- Enforce cleanup of debug code, temporary comments, or test-only artifacts in production.

## Parameters

### Required Parameters

| Parameter | Type | Description |
| :--- | :--- | :--- |
| `filePatterns` | List | Glob patterns to match source or configuration files |
| `tokens` | List | List of tokens that must NOT be found in the files |

### Optional Parameters

| Parameter | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `excludePatterns` | List | `[]` | Glob patterns to exclude specific files |
| `matchMode` | String | `SUBSTRING` | Choose `SUBSTRING` or `REGEX`. Setting to `REGEX` automatically enables regex matching. |
| `caseSensitive` | Boolean | `true` | Whether token matching is case-sensitive |
| `wholeWord` | Boolean | `false` | If `true`, ensures exact word matching (wraps tokens in `\b`). Ignored if `matchMode: REGEX`. |
| `resolveProperties` | Boolean | `true` | Enable `${...}` resolution |
| `resolveLinkedConfig` | Boolean | `false` | Resolve from linked CONFIG project |
| `includeLinkedConfig` | Boolean | `false` | Scan files in linked CONFIG project |
| `ignoreComments` | Boolean | `true` | Removes comments before searching. Recommended to avoid false positives. |
| **`wholeFile`** | **Boolean** | **`false`** | **If `true`, reads the entire file as a single string. Recommended for multi-line regex validation.** |

### 🎯 Comment Handling (`ignoreComments`)

**NEW FEATURE:** The `ignoreComments` parameter allows you to choose between **strict mode** (comments ignored) and **normal mode** (comments included).

#### Why This Matters

By default, token searches include commented code, which can cause **false positives** for FORBIDDEN rules:

```xml
<!-- <logger level="DEBUG" /> -->  ❌ FALSE POSITIVE without ignoreComments
<logger level="INFO" />             ✅ This should be checked
```

#### Supported File Types

| File Type | Extensions | Comment Syntax | Single-Line | Multi-Line Block |
| :--- | :--- | :--- | :--- | :--- |
| **XML** | `.xml`, `.process`, `.bwp` | `<!-- -->` | ✅ | ✅ |
| **Java** | `.java` | `//`, `/* */` | ✅ | ✅ |
| **Groovy** | `.groovy` | `//`, `/* */` | ✅ | ✅ |
| **DataWeave** | `.dwl` | `//`, `/* */` | ✅ | ✅ |
| **JavaScript** | `.js` | `//`, `/* */` | ✅ | ✅ |
| **JSON** | `.json` | `//`, `/* */` (non-standard) | ✅ | ✅ |
| **Properties** | `.properties`, `.substvar` | `#`, `!` (line start only) | ✅ | ❌ |
| **YAML** | `.yaml`, `.yml` | `#` (inline supported) | ✅ | ❌ |
| **SQL** | `.sql` | `--`, `#`, `/* */` | ✅ | ✅ |
| **Shell** | `.sh`, `.bash` | `#` | ✅ | ❌ |

#### When to Use

- **FORBIDDEN Rules:** ✅ **Always use `ignoreComments: true`** to avoid false positives
- **REQUIRED Rules:** ⚠️ Use `ignoreComments: false` (default) to check documentation
- **Documentation Files:** ⚠️ Use `ignoreComments: false` to ensure content exists

#### Example: Strict Mode (Recommended)

```yaml
- id: "BANK-002"
  name: "Forbid Legacy Logger Attributes"
  type: "CODE_GENERIC_TOKEN_SEARCH_FORBIDDEN"
  severity: "HIGH"
  checks:
    - type: "TOKEN_SEARCH"
      params:
        filePatterns: ["**/*.xml"]
        tokens: ["toApplicationCode", "DEBUG"]
        ignoreComments: true  # ✅ Strict mode - ignore commented code
```

**Test Case:**
```xml
<!-- <logger level="DEBUG" /> -->  ✅ IGNORED (in comment)
<logger level="INFO" />             ✅ CHECKED (active code)
```
**Result:** PASS ✅ (no forbidden tokens in active code)

#### Example: Normal Mode
... (existing content) ...

### 📄 Whole File Search (`wholeFile`)

**NEW FEATURE:** When `wholeFile: true` is enabled, the check processes the entire file content as a single block of text rather than line-by-line.

#### Why Use This?
Standard line-by-line regex cannot see relationships between different lines. With `wholeFile`, you can use **DOTALL** regex `(?s)` to validate that if a property exists on line 1, its value must be correct even if it's defined on line 10.

#### Example: Per-File Conditional Validation
Fails if a policy is `applied=true` but its `version` is not `3.0.0` within the same file.

```yaml
- id: "BANK-109"
  name: "Enforce Policy Versions"
  checks:
    - type: GENERIC_TOKEN_SEARCH_FORBIDDEN
      params:
        filePatterns: ["**/Policies/**/*.policy"]
        wholeFile: true # ✅ Read entire file for cross-line checking
        isRegex: true
        tokens:
          # Fails if 'applied=true' is found, but 'version=3.0.0' is NOT found
          - '(?s)^(?=.*policy\.applied\s*=\s*true)(?!.*policy\.version\s*=\s*"3\.0\.0").*$'
```

## Configuration Examples

### Example 1: Block Deprecated Global Functions

Prevent the usage of a deprecated transformation function across all scripts.

```yaml
- id: "RULE-DEPRECATED-TRANSFORM"
  name: "No Legacy Transformations"
  severity: HIGH
  checks:
    - type: GENERIC_TOKEN_SEARCH_FORBIDDEN
      params:
        filePatterns: ["**/*.dwl", "**/*.js"]
        tokens: ["toBase64Legacy()", "oldEncrypt()"]
```

### Example 2: Block Hardcoded Credentials

Identify potential hardcoded secrets in properties or XML configuration files.

```yaml
- id: "RULE-NO-HARDCODED-SECRETS"
  name: "No Hardcoded Credentials"
  severity: CRITICAL
  errorMessage: "Security Violation: Hardcoded credentials found.\n{DEFAULT_MESSAGE}"
  checks:
    - type: GENERIC_TOKEN_SEARCH_FORBIDDEN
      params:
        filePatterns: ["**/*.xml", "**/*.properties", "**/*.yaml"]
        tokens: ["password=", "pwd=", "aws_secret="]
        caseSensitive: false

### Example 3: Exact Word Match (Whole Word)
Prevent false positives by ensuring only exact words are matched (e.g., match "admin" but not "administrator").

```yaml
- id: "RULE-EXACT-WORD"
  name: "Forbid 'admin' User"
  checks:
    - type: GENERIC_TOKEN_SEARCH_FORBIDDEN
      params:
        filePatterns: ["**/*.properties"]
        tokens: ["admin"]
        wholeWord: true  # Matches 'admin' but not 'administrator'
```

### Example 3: Detect IP Addresses (Regex)
Block hardcoded IP addresses in source code directory to enforce hostname usage.

```yaml
- id: "RULE-HOSTNAME-ENFORCEMENT"
  name: "No Hardcoded IP Addresses"
  severity: HIGH
  checks:
    - type: GENERIC_TOKEN_SEARCH_FORBIDDEN
      params:
        filePatterns: ["src/**/*.java", "src/**/*.py"]
        excludePatterns: ["**/test/**"]
        tokens: ["\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"]
        matchMode: REGEX
```

## Error Messages

```text
AuthService.java: Forbidden token found: password=
legacy-script.js: Forbidden token found: toBase64Legacy()
DatabaseConfig.xml: Forbidden IP pattern found: 192.168.1.1
```

## Best Practices

- **Case Sensitivity**: Set `caseSensitive: false` when blocking configuration keys (like `PWD=`) to catch variants like `pwd=` or `Pwd=`.
- **Targeted Exclusions**: Use `excludePatterns` for third-party libraries or internal test suites where forbidden patterns might be legitimate for testing purposes.
- **Whole Word Matching**: Use `wholeWord: true` instead of complex Regex `\b` patterns for simple exact word matching.
- **Regex Guardrails**: When using REGEX, ensure patterns are specific to avoid false positives (e.g., use word boundaries `\\b`).

## Related Rule Types

- **[GENERIC_TOKEN_SEARCH_REQUIRED](GENERIC_TOKEN_SEARCH_REQUIRED.md)** - Opposite: ensures tokens DO exist.
- **[XML_XPATH_NOT_EXISTS](XML_XPATH_NOT_EXISTS.md)** - Precise blocking of XML structures.

## Solution Patterns and Technology Reference

Standard configurations for blocking anti-patterns.

| Technology | Scenario | Mode | Target File |
| :--- | :--- | :--- | :--- |
| **☕ Java** | Block `System.out` | `FORBIDDEN` | `**/*.java` |
| **🐍 Python** | Block Debugger | `FORBIDDEN` | `**/*.py` |
| **📦 Node.js** | Block `eval()` | `FORBIDDEN` | `**/*.js` |
| **🐎 MuleSoft** | Block legacy MEL | `FORBIDDEN` | `**/*.xml` |

### ☕ Java / Spring Boot Patterns

Prevent developers from using standard output streams for logging.

```yaml
- id: "JAVA-LOGGING-STANDARDS"
  name: "No System.out"
  checks:
    - type: GENERIC_TOKEN_SEARCH_FORBIDDEN
      params:
        filePatterns: ["**/*.java"]
        tokens: ["System.out.println", "System.err.println"]
```

### 🐎 MuleSoft Patterns

Block legacy Mule Expression Language (MEL) patterns in modern projects.

```yaml
- id: "MULE-MODERNIZATION"
  name: "No Legacy MEL"
  checks:
    - type: GENERIC_TOKEN_SEARCH_FORBIDDEN
      params:
        filePatterns: ["**/*.xml"]
        tokens: ["message.inboundProperties", "message.outboundProperties"]
```
