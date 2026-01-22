# QUICK REFERENCE

## 1. Global Parameters

These apply to almost ALL rule types and control file selection and logic.

| Parameter             | Default      | Description                                                   |
|-----------------------|--------------|---------------------------------------------------------------|
| `filePatterns`        | (Required)   | List of glob patterns for target files (e.g. `["**/*.xml"]`). |
| `matchMode`           | `ALL_FILES`  | Controls file-level quantifier (see table below).             |
| `logic`               | `AND`        | Combining logic for multiple checks or tokens.                |
| `environments`        | `[]`         | List of environments where this rule applies.                 |
| `resolveProperties`   | `true`       | Enable resolution of `${property}` placeholders.              |
| `ignoreComments`      | `true`       | Skip commented lines during selection.                        |
| `resolveLinkedConfig` | `false`      | Resolve properties from the **linked CONFIG project**.       |
| `includeLinkedConfig` | `false`      | Scan files in **both** CODE and linked CONFIG projects.       |
| `scope`               | `null`       | Rule metadata scope. Omitted from report by default.          |

#### File Match Quantifiers (`matchMode`)

| Value           | Description                                                        |
|-----------------|-------------------------------------------------------------------|
| `ALL_FILES`     | (Default) Every file must pass for the rule to pass.              |
| `ANY_FILE`      | Rule passes if at least one file passes.                          |
| `NONE_OF_FILES` | Rule passes only if zero files match/pass (useful for forbidden). |
| `AT_LEAST_N`    | Rule passes if N or more files pass.                              |

---

## 2. Project Type Filtering

**NEW FEATURE**: Eliminate report noise by only running applicable rules on each project type.

### Overview

Instead of running ALL rules on ALL projects, you can:

1. Define project types (CODE, CONFIG, API, etc.) ONCE in the `config` section
2. Rules specify which types they apply to using `appliesTo`
3. Non-applicable rules are automatically skipped

### Configuration

#### Step 1: Define Project Types

Add `projectTypes` to your `config` section:

```yaml
config:
  environments: [SBX, ITE, PREP, PROD]
  
  projectTypes:
    CODE:
      description: "Mule application projects with source code"
      detectionCriteria:
        markerFiles: ["pom.xml", "mule-artifact.json"]
        excludePatterns: [".*_config.*"]
    
    CONFIG:
      description: "Configuration projects with properties and policies"
      detectionCriteria:
        namePattern: ".*_config.*"
    
    API:
      description: "API layer projects"
      detectionCriteria:
        nameContains: "-api-"
        markerFiles: ["pom.xml"]
```

#### Step 2: Reference Types in Rules

```yaml
rules:
  # CODE-only rule
  - id: "RULE-001"
    name: "Validate Parent POM"
    appliesTo: ["CODE"]  # Only runs on CODE projects
    checks:
      - type: POM_VALIDATION_REQUIRED
        # ...
  
  # CONFIG-only rule
  - id: "RULE-006"
    name: "Header Injection Policy"
    appliesTo: ["CONFIG"]  # Only runs on CONFIG projects
    checks:
      - type: MANDATORY_PROPERTY_VALUE_CHECK
        # ...
  
  # Multi-type rule
  - id: "RULE-040"
    name: "Java 17 Migration"
    appliesTo: ["CODE", "API"]  # Runs on both CODE and API projects
    checks:
      - type: GENERIC_TOKEN_SEARCH_FORBIDDEN
        # ...
  
  # Universal rule (no appliesTo)
  - id: "RULE-100"
    name: "Mandatory Substring Check"
    # No appliesTo = applies to ALL projects
    checks:
      - type: GENERIC_TOKEN_SEARCH_REQUIRED
        # ...
```

### Detection Criteria Options

| Criterion         | Type        | Description                                     | Example                      |
|-------------------|-------------|-------------------------------------------------|------------------------------|
| `markerFiles`     | List        | Files that must exist (supports glob patterns)  | `["pom.xml", "**/*.policy"]` |
| `namePattern`     | String      | Regex pattern for project folder name           | `".*_config.*"`              |
| `nameContains`    | String/List | Substring(s) the project name must contain      | `"-api-"` or `["-api-"]`     |
| `excludePatterns` | List        | Regex patterns to exclude projects              | `[".*-test-.*"]`             |
| `logic`           | String      | AND/OR logic for criteria (default: OR)        | `"AND"`                      |

### Examples for Different Technologies

**Spring Boot Projects:**

```yaml
SPRING_BOOT:
  description: "Spring Boot applications"
  detectionCriteria:
    markerFiles: ["pom.xml", "src/main/resources/application.properties"]
    logic: AND
```

**TIBCO Projects:**

```yaml
TIBCO:
  description: "TIBCO BusinessWorks projects"
  detectionCriteria:
    markerFiles: ["**/*.bwp", "**/*.process"]
```

**Python Projects:**

```yaml
PYTHON:
  description: "Python applications"
  detectionCriteria:
    markerFiles: ["requirements.txt", "setup.py"]
```

### Benefits

- ✅ **Cleaner Reports**: Only show relevant rules for each project
- ✅ **No False Positives**: CONFIG projects won't fail on CODE-specific rules
- ✅ **Better Focus**: Teams see only rules that matter to their project type
- ✅ **Zero Performance Impact**: Classification cached (~1ms per project)
- ✅ **Backward Compatible**: No `projectTypes` = all rules apply to all projects

### Backward Compatibility

- **No `projectTypes` in config**: All rules apply to all projects (current behavior)
- **No `appliesTo` in rule**: Rule applies to all projects
- **Empty `appliesTo: []`**: Rule applies to NO projects (effectively disabled)

---

## 3. Logic Patterns (Positive vs. Negative)

Understanding when to use Positive Logic ("Ensure Good Exists") versus Negative Logic ("Ensure Bad Does Not Exist") is crucial for creating accurate rules.

### Positive Logic (REQUIRED)

- **Concept**: "Does at least ONE valid instance exist?"
- **Use Case**: Verifying that a mandatory component is present (e.g., "App MUST have a Global Exception Handler").
- **Behavior**: If the scanner finds **one** compliant item, the file PASSES.
- **⚠️ Pitfall**: If a file has one "Good" item and ten "Bad" items, it will still PASS.
    - *Example*: `tokens: ["secure=true"]`. File contains `secure=true` AND `secure=false`. Result: PASS.

### Negative Logic (FORBIDDEN)

- **Concept**: "Does ANY invalid instance exist?"
- **Use Case**: Security audit, finding deprecated code, ensuring specific configurations are NOT used.
- **Behavior**: If the scanner finds **one** non-compliant item, the file FAILS.
- **✅ Best Practice**: For enforcing strict configurations (like "All connections must be encrypted"), usage of **Negative Logic** is safer.

### Case Study: Detailed Configuration Checks

**Goal**: Enforce `ApplicationId` in DB2 URLs (`jdbc:db2://...;ApplicationId=MyApp`).

#### ❌ Approach A: Positive Logic (Weak)

- **Rule Type**: `GENERIC_TOKEN_SEARCH_REQUIRED`
- **Token**: `jdbc:db2.*ApplicationId=`
- **Scenario**: File contains:
    1. `db.bad=jdbc:db2://...` (Missing ID)
    2. `db.good=jdbc:db2://...;ApplicationId=MyApp` (Matches!)
- **Result**: **PASS** (False Negative).

#### ✅ Approach B: Negative Logic + Regex (Strong)

- **Rule Type**: `GENERIC_TOKEN_SEARCH_FORBIDDEN`
- **Token**: `jdbc:db2(?!.*ApplicationId=).*` (Regex Negative Lookahead)
    - *Meaning*: "Find any DB2 string that does **NOT** contain ApplicationId".
- **Scenario**: Same file.
- **Result**: **FAIL** (Correct).

---

## 4. Message Tokens

You can use the following tokens in `successMessage` and `errorMessage` to provide dynamic feedback in reports:

| Token                | Description                                                          | Example Output                |
|----------------------|----------------------------------------------------------------------|-------------------------------|
| `{RULE_ID}`          | The ID of the current rule.                                          | `BANK-021`                    |
| `{DEFAULT_MESSAGE}`  | Technical details of the check result (e.g., values).                | `Validation failed for...`    |
| `{CHECKED_FILES}`    | List of files that were actually processed/scanned.                  | `src/main/mule/api.xml`       |
| `{MATCHING_FILES}`   | List of files that *passed* the check condition.                    | `src/main/mule/valid.xml`     |
| `{FOUND_ITEMS}`      | Specific forbidden items found (e.g., tokens, attributes).           | `jce-encrypt`                 |
| `{CORE_DETAILS}`     | Core validation failure info (a cleaner subset of Default).          | `Missing 'encrypt=false'`     |
| `{FAILURES}`         | Specific failure reasons if multiple checks failed.                  | `XPath not found...`          |
| `{PROPERTY_RESOLVED}`| Details of any property placeholders resolved.                       | `Resolved ${port} -> 8081`    |

**Example Usage**:

```yaml
errorMessage: "✗ Security Violation in {RULE_ID}: Found forbidden tokens {FOUND_ITEMS}. \n Checked: {CHECKED_FILES}"
```

---

## 5. Logical Operators (`logic`)

The `logic` parameter determines how multiple tokens or checks within a single rule are combined.

### logic: OR (Default for Forbidden)

- **Concept**: "Fail if ANY of these match."
- **Behavior**: triggers failure if **EITHER** `A` is found **OR** `B` is found.

### logic: AND (Default for Required)

- **Concept**: "Fail ONLY if ALL of these match."
- **Behavior**: triggers failure **ONLY IF BOTH** `A` and `B` are present.

> [!IMPORTANT]
> **Issue Case (Hyphens)**:
> If you want to detect *any* version of a policy, `logic: OR` ensures that finding any one item is enough to flag the check.

---

## 6. Rule-Specific Parameters

#### **TOKEN_SEARCH** (Global Token Search)

| Parameter           | Type    | Default                  | Description                                            |
|---------------------|---------|--------------------------|--------------------------------------------------------|
| `tokens`            | List    | (Required)               | List of strings or regex patterns to search for.       |
| `isRegex`           | Boolean | `false`                  | Set to `true` if `tokens` are regular expressions.     |
| `caseSensitive`     | Boolean | `true`                   | Enable/disable case sensitivity.                       |
| `wholeWord`         | Boolean | `false`                  | Wraps tokens in `\b` for exact word matching.          |
| `wholeFile`         | Boolean | `false`                  | Reads entire file for multi-line regex.                |
| `ignoreComments`    | Boolean | `true`                   | Removes comments before searching.                     |
| `logic`             | String  | `AND` (Req) / `OR` (Forb) | Combining logic for multiple tokens.                   |

---

## 7. Comparison Matrix

| Operator             | Type    | Description                           | Supported Rules  |
|----------------------|---------|---------------------------------------|------------------|
| `GTE` / `minVersion` | SEMVER  | Greater than or equal (4.9.7 >= 4.9.0)| JSON, POM        |
| `LTE` / `maxVersion` | SEMVER  | Less than or equal                    | JSON, POM        |
| `EQ`                 | STRING  | Exact string match                    | ALL Value Checks |
| `CONTAINS`           | STRING  | Substring exists within target        | XPATH, TOKEN     |

---

## 8. Property Resolution Guide

### Supported Resolutions

| Rule Type                      | Target           | Example                              |
|--------------------------------|------------------|--------------------------------------|
| `XML_ATTRIBUTE_EXISTS`         | Attribute Values | `soapVersion="${soapversion}"`      |
| `XML_ELEMENT_CONTENT_REQUIRED` | Text Content     | `<version>${pom.version}</version>`  |
| `JSON_VALIDATION_REQUIRED`     | JSON Values      | `{"muleEnv": "${env}"}`              |
| `TOKEN_SEARCH`                 | Text Search      | `tokens: ["${db2.url}"]`             |

### Cross-Project Resolution (CODE + CONFIG)

When a rule applies to both `CODE` and `CONFIG`, you can enable cross-project resolution.

**Example**:

```yaml
- id: "DB2-URL-CHECK"
  appliesTo: ["CODE", "CONFIG"]
  checks:
    - type: GENERIC_TOKEN_SEARCH_REQUIRED
      params:
        tokens: ["${db2.url}"]
        resolveProperties: true
        resolveLinkedConfig: true
        includeLinkedConfig: true
```

---

## 9. Best Practices

- ✅ **Message Tokens**: Always use `{DEFAULT_MESSAGE}` for technical details.
- ✅ **Negative Logic**: Use `FORBIDDEN` rules for security audits.
- ✅ **Comments**: Use `ignoreComments: true` to avoid false positives in commented code.
- ✅ **Transparency**: Add `{PROPERTY_RESOLVED}` to see resolution details.
