# GENERIC_TOKEN_SEARCH

**Rule Type:** `CODE` - **Applies To:** Any text-based source files

## Overview

Advanced token search with **environment filtering** and **regex support**. This is the config-specific version of GENERIC_TOKEN_SEARCH_REQUIRED/FORBIDDEN with additional environment awareness.

## Use Cases

- Complex token validation with environment filtering
- Regex-based pattern matching in config files
- Environment-specific validation rules
- Advanced configuration compliance checks

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePatterns` | List<String> | Glob patterns to match files |
| `tokens` | List<String> | List of tokens to search for |

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `environments` | List<String> | `null` | Filter files by environment names |
| `searchMode` | String | `FORBIDDEN` | `REQUIRED` or `FORBIDDEN` |
| `matchMode` | String | `SUBSTRING` | `SUBSTRING`, `REGEX`, or `ELEMENT_ATTRIBUTE` |
| `elementName` | String | `null` | For XML element-specific searches |

## Configuration Examples

### Example 1: Environment-Specific Token Search

```yaml
- id: "RULE-150"
  name: "No Hardcoded Localhost in Production"
  description: "Production configs must not contain localhost"
  enabled: true
  severity: CRITICAL
  checks:
    - type: GENERIC_TOKEN_SEARCH
      params:
        filePatterns: ["*.properties"]
        environments: ["PROD"]
        tokens: ["localhost", "127.0.0.1"]
        searchMode: FORBIDDEN
```

### Example 2: Regex Pattern with Environment Filter

```yaml
- id: "RULE-151"
  name: "IP Address Detection in Config"
  description: "Detect hardcoded IP addresses in QA and PROD configs"
  enabled: true
  severity: HIGH
  checks:
    - type: GENERIC_TOKEN_SEARCH
      params:
        filePatterns: ["*.yaml", "*.properties"]
        environments: ["QA", "PROD"]
        tokens: ["\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"]
        matchMode: REGEX
        searchMode: FORBIDDEN
```

### Example 3: Required Token in All Environments

```yaml
- id: "RULE-152"
  name: "API Key Configuration Required"
  description: "All environment configs must reference API key property"
  enabled: true
  severity: HIGH
  checks:
    - type: GENERIC_TOKEN_SEARCH
      params:
        filePatterns: ["*.properties"]
        environments: ["ALL"]
        tokens: ["${api.key}", "${secure::api.key}"]
        searchMode: REQUIRED
```

## Error Messages

```
PROD.properties: Forbidden token 'localhost' found in file
QA.yaml: Required token(s) not found in files matching: *.yaml
```


## Best Practices

### When to Use This Rule
- ✅ Searching for specific keywords or patterns across all files
- ✅ Validating presence of required code patterns
- ✅ Ensuring specific imports or dependencies are used
- ✅ Finding configuration references

### Search Pattern Tips
```yaml
# Case-insensitive search
searchTokens: ["TODO", "FIXME"]

# Regex patterns for complex searches
searchTokens: ["import.*Logger"]
```

- **[MANDATORY_SUBSTRING_CHECK](MANDATORY_SUBSTRING_CHECK.md)** - Simpler config-specific validation

## 📚 Application Implementation References

This section provides standard examples for validating various technologies using `GENERIC_TOKEN_SEARCH`. Use these as a baseline for your organization's specific coding standards.

### 🐎 MuleSoft 4: Object Store Persistence
**Best Practice:** Persistent object stores should be preferred over in-memory for reliability.
```yaml
- id: "MULE-OS-01"
  name: "Persistent Object Store"
  description: "Ensure Object Stores are configured to be persistent"
  enabled: true
  severity: MEDIUM
  checks:
    - type: GENERIC_TOKEN_SEARCH
      params:
        filePatterns: ["*.xml"]
        tokens: ["persistent=\"false\""]
        searchMode: FORBIDDEN
```

### 🐍 Python: Logging vs. Print
**Best Practice:** Use the `logging` module for production applications. `print()` statements are unbuffered, lack timestamps/severity levels, and can clutter standard output.

```yaml
- id: "PYTHON-001"
  name: "No Print Statements (Use Logging)"
  description: "Detects usage of print() which should be replaced by logging module"
  enabled: true
  severity: MEDIUM
  checks:
    - type: GENERIC_TOKEN_SEARCH
      params:
        filePatterns: ["*.py"]
        tokens: ["print("]
        searchMode: FORBIDDEN
```

### ☕ Java / Spring Boot: Standard Output
**Best Practice:** Avoid `System.out` and `System.err` in enterprise applications. Use SLF4J or Log4j for proper log management and rotation.

```yaml
- id: "JAVA-001"
  name: "No System.out Usage"
  description: "Use SLF4J/Log4j instead of System.out"
  enabled: true
  severity: MEDIUM
  checks:
    - type: GENERIC_TOKEN_SEARCH
      params:
        filePatterns: ["*.java"]
        tokens: ["System.out.print", "System.err.print"]
        searchMode: FORBIDDEN
```

### ⚡ TIBCO BW 6.x: Module Configuration
**Best Practice:** Ensure critical module properties for AppNodes are defined. Naming conventions often use suffixes like `_Min` or `_Max`.

```yaml
- id: "TIBCO-BW6-001"
  name: "TIBCO Module Property Check"
  description: "Ensure specific AppNode property exists in manifest or module file"
  enabled: true
  severity: HIGH
  checks:
    - type: GENERIC_TOKEN_SEARCH
      params:
        filePatterns: ["META-INF/default.substvar", "*.module"]
        tokens: ["BW.APPNODE.NAME"]
        searchMode: REQUIRED
```

### 🔷 TIBCO BW 5.x: Hardcoding Checks
**Best Practice:** Avoid hardcoded IP addresses in process definitions (`.process`) or archives (`.archive`). Use Global Variables (GVs) which can be overridden at deployment.

```yaml
- id: "TIBCO-BW5-001"
  name: "No Hardcoded IPs in BW5"
  description: "Detect IPs in TIBCO BW 5.x process files"
  enabled: true
  severity: CRITICAL
  checks:
    - type: GENERIC_TOKEN_SEARCH
      params:
        filePatterns: ["*.archive", "*.process"]
        tokens: ["\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"]
        matchMode: REGEX
        searchMode: FORBIDDEN
```

### ⚛️ JavaScript / TypeScript: Console Usage
**Best Practice:** Remove `console.log` from production code to prevent performance issues and leaking sensitive information in the browser console.

```yaml
- id: "WEB-001"
  name: "No Console Logs"
  description: "Ensure no console.log debugging is left in production code"
  enabled: true
  severity: LOW
  checks:
    - type: GENERIC_TOKEN_SEARCH
      params:
        filePatterns: ["*.js", "*.ts", "*.jsx", "*.tsx"]
        tokens: ["console.log("]
        searchMode: FORBIDDEN
```
