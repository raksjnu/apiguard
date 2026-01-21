# QUICK REFERENCE

## 1. Global Parameters

These apply to almost ALL rule types and control file selection and logic.

| Parameter      | Default      | Description                                          |
|----------------|--------------|------------------------------------------------------|
      - node_modules
      prefixes:       # Folder names starting with...
      - .
      - temp_

    # Global File Exclusion
    ignoredFiles:
      exactNames:     # Specific files to ignore
      - log4j2-test.xml
      - .DS_Store
      prefixes:       # Filenames starting with...
      - debug_
| `filePatterns` | (Required)   | List of glob patterns for target files (e.g. `["**/*.xml"]`). |
| `matchMode`    | `ALL_FILES`  | Controls file-level quantifier (see table below).    |
| `logic`        | `AND`        | Combining logic for multiple checks or tokens.      |
| `environments` | `[]`         | List of environments where this rule applies.        |
| `resolveProperties` | `false`  | Enable resolution of `${property}` placeholders. |
| `resolveLinkedConfig`| `false` | Resolve properties from the **linked CONFIG project**. |
| `includeLinkedConfig`| `false` | Scan files in **both** CODE and linked CONFIG projects. |

#### File Match Quantifiers (`matchMode`)

| Value           | Description                                                        |
|-----------------|-------------------------------------------------------------------|
| `ALL_FILES`     | (Default) Every file must pass for the rule to pass.              |
| `ANY_FILE`      | Rule passes if at least one file passes.                          |
| `NONE_OF_FILES` | Rule passes only if zero files match/pass (useful for forbidden). |
| `AT_LEAST_N`    | Rule passes if N or more files pass.                              |

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

| Criterion | Type | Description | Example |
|-----------|------|-------------|---------|
| `markerFiles` | List | Files that must exist (supports glob patterns) | `["pom.xml", "**/*.policy"]` |
| `namePattern` | String | Regex pattern for project folder name | `".*_config.*"` |
| `nameContains` | String/List | Substring(s) the project name must contain | `"-api-"` or `["-api-", "-svc-"]` |
| `excludePatterns` | List | Regex patterns to exclude projects | `[".*_config.*", ".*-test-.*"]` |
| `logic` | String | AND/OR logic for criteria (default: OR) | `"AND"` |

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

✅ **Cleaner Reports**: Only show relevant rules for each project
✅ **No False Positives**: CONFIG projects won't fail on CODE-specific rules
✅ **Better Focus**: Teams see only rules that matter to their project type
✅ **Zero Performance Impact**: Classification cached (~1ms per project)
✅ **Backward Compatible**: No `projectTypes` = all rules apply to all projects

### Backward Compatibility

- **No `projectTypes` in config**: All rules apply to all projects (current behavior)
- **No `appliesTo` in rule**: Rule applies to all projects
- **Empty `appliesTo: []`**: Rule applies to NO projects (effectively disabled)

---

## 3. Logic Patterns (Positive vs. Negative)

Understanding when to use Positive Logic ("Ensure Good Exists") versus Negative Logic ("Ensure Bad Does Not Exist") is crucial for creating accurate rules.

### Positive Logic (REQUIRED)
*   **Concept**: "Does at least ONE valid instance exist?"
*   **Use Case**: Verifying that a mandatory component is present (e.g., "App MUST have a Global Exception Handler").
*   **Behavior**: If the scanner finds **one** compliant item, the file PASSES.
*   **⚠️ Pitfall**: If a file has one "Good" item and ten "Bad" items, it will still PASS.
    *   *Example*: `tokens: ["secure=true"]`. File contains `secure=true` AND `secure=false`. Result: PASS (because valid token was found).

### Negative Logic (FORBIDDEN)
*   **Concept**: "Does ANY invalid instance exist?"
*   **Use Case**: Security audit, finding deprecated code, ensuring specific configurations are NOT used.
*   **Behavior**: If the scanner finds **one** non-compliant item, the file FAILS.
*   **✅ Best Practice**: For enforcing strict configurations (like "All connections must be encrypted"), usage of **Negative Logic** is safer.

### Case Study: Detailed Configuration Checks
**Goal**: Enforce `ApplicationId` in DB2 URLs (`jdbc:db2://...;ApplicationId=MyApp`).

#### ❌ Approach A: Positive Logic (Weak)
*   **Rule Type**: `GENERIC_TOKEN_SEARCH_REQUIRED`
*   **Token**: `jdbc:db2.*ApplicationId=`
*   **Scenario**: File contains:
    1.  `db.bad=jdbc:db2://...` (Missing ID)
    2.  `db.good=jdbc:db2://...;ApplicationId=MyApp` (Matches!)
*   **Result**: **PASS** (False Negative). The rule found the "Good" line and was satisfied, ignoring the "Bad" line.

#### ✅ Approach B: Negative Logic + Regex (Strong)
*   **Rule Type**: `GENERIC_TOKEN_SEARCH_FORBIDDEN`
*   **Token**: `jdbc:db2(?!.*ApplicationId=).*` (Regex Negative Lookahead)
    *   *Meaning*: "Find any DB2 string that does **NOT** contain ApplicationId".
*   **Scenario**: Same file.
*   **Result**: **FAIL** (Correct). The rule explicitly searches for the broken line. The "Good" line does not match the "Bad" regex, but the "Bad" line does.

---

## 2. Message Tokens

You can use the following tokens in `successMessage` and `errorMessage` to provide dynamic feedback in reports:

| Token | Description | Availability | Example Output |
|-------|-------------|--------------|----------------|
| `{RULE_ID}` | The ID of the current rule. | Always | `BANK-021` |
| `{DEFAULT_MESSAGE}` | Technical details of the check result (e.g., valid/invalid values). | Always | `Validation failed for...` |
| `{CHECKED_FILES}` | List of files that were actually processed/scanned. | Always (Defaults to "None") | `src/main/mule/api.xml` |
| `{MATCHING_FILES}`| List of files that *passed* the check condition. | Success Messages | `src/main/mule/valid.xml` |
| `{FOUND_ITEMS}` | Specific forbidden items found (e.g., tokens, attributes). | `*_FORBIDDEN`, `*_NOT_EXISTS` | `jce-encrypt, toApplicationCode` |
| `{CORE_DETAILS}` | Core validation failure info (a cleaner subset of Default). | Always | `Missing 'encrypt=false'` |
| `{FAILURES}` | Specific failure reasons if multiple checks failed. | Compound Checks | `XPath not found...` |
| `{PROPERTY_RESOLVED}`| Details of any property placeholders resolved. | Validated Properties | `Resolved ${port} -> 8081` |

**Example Usage**:
```yaml
errorMessage: "✗ Security Violation in {RULE_ID}: Found forbidden tokens {FOUND_ITEMS}. \n Checked: {CHECKED_FILES}"
```

---

## Logical Operators (`logic`)

The `logic` parameter determines how multiple tokens or checks within a single rule are combined. This is critical for rules with multiple search items.

### logic: OR (Default for Forbidden)
- **Concept**: "Fail if ANY of these match."
- **Behavior**: If you have `tokens: ["A", "B"]`, the rule triggers a failure if **EITHER** `A` is found **OR** `B` is found.
- **Hyphen Handling**: Useful for tokens like `RAKS-AAA-SOAP`. If you want to detect *any* version of RAKS AAA policies (SOAP or HTTP), `logic: OR` ensures that finding just the SOAP one is enough to fail the check.

### logic: AND (Default for Required)
- **Concept**: "Fail ONLY if ALL of these match."
- **Behavior**: If you have `tokens: ["A", "B"]`, the rule triggers a failure **ONLY IF BOTH** `A` and `B` are present in the same file.
- **Example**: If you want to forbid a specific *combination* of security headers, use `AND`.

> [!IMPORTANT]
> **Issue Case (Hyphens)**:
> Earlier, a check used `logic: AND`. This caused it to "Pass" (incorrectly) because it was waiting for *all* forbidden tokens to appear in a single file. By switching to `OR`, it now correctly flags a file as soon as it sees `RAKS-AAA-SOAP`.

---

## 2. Rule-Specific Parameters
Each rule type has its own required parameters. Using the wrong parameter name will result in the rule being ignored or failing silently.

#### **TOKEN_SEARCH** (Global Token Search)
Used for scanning text files for literal strings or regex patterns. Use `GENERIC_TOKEN_SEARCH_REQUIRED` or `GENERIC_TOKEN_SEARCH_FORBIDDEN`.

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `tokens` | List | (Required) | List of strings or regex patterns to search for. |
| `isRegex` | Boolean | `false` | Set to `true` if `tokens` are regular expressions. |
| `caseSensitive`| Boolean | `true` | Enable/disable case sensitivity. |
| `wholeWord` | Boolean | `false` | If `true`, ensures exact word matching (wraps tokens in `\b`). |
| **`ignoreComments`** | **Boolean** | **`false`** | **If `true`, removes comments before searching (strict mode). Recommended for FORBIDDEN rules to avoid false positives. Supports XML, Java, Groovy, DataWeave, JSON, Properties, YAML, SQL, and Shell files.** |
| `logic` | String | `AND` (Req) / `OR` (Forb) | Use `OR` to fail if ANY token is found in FORBIDDEN mode. |

> [!TIP]
> **Comment Handling**: Use `ignoreComments: true` for FORBIDDEN rules to avoid false positives from commented code.
> Supports both single-line (`//`, `#`, `--`) and multi-line block comments (`/* */`, `<!-- -->`).
> See [CODE_GENERIC_TOKEN_SEARCH_FORBIDDEN.md](CODE_GENERIC_TOKEN_SEARCH_FORBIDDEN.md) for detailed examples.


> [!IMPORTANT]
> **Token Matching vs. File Matching**:
> For token search, `isRegex` controls how tokens are matched. `matchMode` controls how MANY files must satisfy the condition.
> **DO NOT** set `matchMode: SUBSTRING` - it will fall back to `ALL_FILES`.

**Example (Regex for exact word match)**:
```yaml
- type: GENERIC_TOKEN_SEARCH_FORBIDDEN
  params:
    filePatterns: ["**/*.xml"]
    tokens: ["\\bRAKS-AAA-SOAP\\b"] # \b ensures exact word match
    isRegex: true
```

#### **XML_ATTRIBUTE_EXISTS** / **NOT_EXISTS**
Used for validating specific element attributes in XML files.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `elements` | List | (Required) | Element names to check. |
| `elementAttributeSets` | List<Map>| ✅ (EXISTS) | Pairs of `{element: "...", attributes: {key: "val"}}`. |
| `forbiddenAttributes`| List | ✅ (NOT_EXISTS) | List of attributes that must not exist on the element. |
| `resolveProperties` | Boolean | `false` | **Property Resolution**: Resolves `${...}` before checking. |

#### **XML_ELEMENT_CONTENT_REQUIRED** / **FORBIDDEN**
Used for validating the text content of XML elements.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `element` | String | (Required) | The XML element to check (e.g. `version`). |
| `expectedValue`| String | ✅ (REQUIRED) | The text content the element must have. |
| `forbiddenValue`| String | ✅ (FORBIDDEN)| Content that must not exist in any matching element. |
| `resolveProperties` | Boolean | `false` | **Property Resolution**: Resolves placeholders in content. |

#### **JSON_VALIDATION_REQUIRED** / **FORBIDDEN**
Used for validating JSON structure and values.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `filePattern` | String | ✅ | Single file to validate (e.g. `mule-artifact.json`). |
| `requiredFields` | Map | ❌ | Key-Value pairs that must match exactly. |
| `minVersions` | Map | ❌ | Semantic version comparison (e.g. `mule: "4.9.0"`). |
| `forbiddenElements` | List | ✅ (FORB) | Keys that must not exist in the JSON. |
| `resolveProperties` | Boolean | `false` | **Property Resolution**: Resolves placeholders in JSON values. |

---

## Validation Operators & Comparison Matrix

### Operator Support by Technology

| Operator | Type | Description | Supported Rules |
|----------|------|-------------|-----------------|
| `GTE` / `minVersion` | SEMVER | Greater than or equal (4.9.7 >= 4.9.0) | JSON, POM |
| `LTE` / `maxVersion` | SEMVER | Less than or equal | JSON, POM |
| `EQ` | STRING | Exact string match | ALL Value Checks |
| `CONTAINS` | STRING | Substring exists within target | XML_XPATH, TOKEN |

### Parameter Support Matrix

| Parameter | XML_ATTR | XML_XPATH | JSON_VAL | TOKEN_SEARCH | POM_VAL |
|-----------|----------|-----------|----------|--------------|---------|
| `required` / `forbidden` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `resolveProperties` | ✅ | ✅ | ✅ | ❌ | ❌ |
| `isRegex` | ❌ | ❌ | ❌ | ✅ | ❌ |
| `logic (AND/OR)`| ✅ | ✅ | ✅ | ✅ | ✅ |
| `valueType (SEMVER)` | ✅ | ❌ | ✅ | ❌ | ✅ |
| `elementContent` | ✅ | ❌ | ❌ | ❌ | ❌ |

---

## Property Resolution Guide (Flagship Feature)

### Supported Resolutions

| `XML_ATTRIBUTE_EXISTS` | Attribute Values | `soapVersion="${soapversion}"` |
| `XML_ELEMENT_CONTENT_REQUIRED` | Text Content | `<version>${pom.version}</version>` |
| `JSON_VALIDATION_REQUIRED` | JSON Values | `{"muleEnv": "${env}"}` |
| `TOKEN_SEARCH` | Text Search | `tokens: ["${db2.url}"]` |

### Cross-Project Resolution (CODE + CONFIG)

When a rule applies to both `CODE` and `CONFIG` (using `appliesTo: ["CODE", "CONFIG"]`), you can enable cross-project resolution to validate that properties referenced in your code exist in your configuration files.

**Example**:
```yaml
- id: "DB2-URL-CHECK"
  name: "Validate DB2 URL from Config"
  appliesTo: ["CODE", "CONFIG"]
  checks:
    - type: GENERIC_TOKEN_SEARCH_REQUIRED
      params:
        filePatterns: ["src/main/mule/*.xml"]
        tokens: ["${db2.url}"]
        resolveProperties: true
        resolveLinkedConfig: true
        includeLinkedConfig: true
```

*   **Behavior**:
    1.  Aegis finds `${db2.url}` in a Mule XML file.
    2.  `resolveProperties: true` triggers the search for `db2.url`.
    3.  `resolveLinkedConfig: true` allows searching in the linked property files (e.g., `dev.properties` in the CONFIG project).
    4.  `includeLinkedConfig: true` ensures the property files themselves are also validated if needed.


201: > [!TIP]
202: > **Transparency**: Always add `{PROPERTY_RESOLVED}` to your messages to see resolution details in reports.



---

## Configuration Examples

### Example 1: Property Resolution (XML Attributes)

```yaml
rules:
  - id: "SOAP-VERSION-CHECK"
    name: "Validate SOAP Version with Property Resolution"
    checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: ["src/main/mule/*-config.xml"]
        elementAttributeSets:
        - element: "apikit-soap:config"
          attributes: {soapVersion: "SOAP_11"}
        - element: "apikit-soap:config"
          attributes: {soapVersion: "SOAP_12"}
        resolveProperties: true  # Resolves ${soapVersion} from properties
```

**XML File**:
```xml
<apikit-soap:config soapVersion="${soapVersion}" />
```

**Property File** (`mule-app.properties`):
```properties
soapVersion=SOAP_11
```

**Result**: ✅ PASS (resolved `${soapVersion}` → `SOAP_11`)

### Example 2: Property Resolution (XML Content)

```yaml
rules:
  - id: "APP-VERSION-CHECK"
    name: "Validate App Version in XML"
    successMessage: "✓ Version validated: {PROPERTY_RESOLVED}"
    checks:
    - type: XML_ELEMENT_CONTENT_REQUIRED
      params:
        filePatterns: ["**/version-info.xml"]
        element: "version"
        expectedValue: "1.2.3"
        resolveProperties: true
```

**XML File**:
```xml
<version>${app.version}</version>
```

**Property File**: `app.version=1.2.3`

**Result**: ✅ PASS (resolved `${app.version}` → `1.2.3`)

### Example 2: Using {FOUND_ITEMS} Token

```yaml
rules:
  - id: "FORBID-LEGACY-ATTRS"
    name: "Forbid Legacy Logger Attributes"
    errorMessage: "✗ Found forbidden attributes: {FOUND_ITEMS}. {DEFAULT_MESSAGE}"
    checks:
    - type: XML_ATTRIBUTE_NOT_EXISTS
      params:
        filePatterns: ["src/main/mule/*.xml"]
        elements: ["logger"]
        forbiddenAttributes: ["toApplicationCode", "fromApplicationCode"]
```

**Result**: `✗ Found forbidden attributes: toApplicationCode, fromApplicationCode. ...`

### Example 3: Exact Token Matching with REGEX

```yaml
rules:
  - id: "FORBID-JCE-ENCRYPT"
    name: "Forbid jce-encrypt Token"
    errorMessage: "✗ Found forbidden token: {FOUND_ITEMS}. Use EncUtil instead."
    checks:
    - type: GENERIC_TOKEN_SEARCH_FORBIDDEN
      params:
        filePatterns: ["src/main/mule/*.xml"]
        tokens: ["\\bjce-encrypt\\b"]  # \b = word boundary for exact match
        matchMode: REGEX
```

**Matches**: `jce-encrypt` (exact word)  
**Does NOT Match**: `my-jce-encrypt-util`, `jce-encryption`

---

## Best Practices

### 1. Message Token Usage

✅ **DO**:
```yaml
errorMessage: "✗ Validation failed: {FOUND_ITEMS}. {DEFAULT_MESSAGE}"
successMessage: "✓ All checks passed. {CHECKED_FILES}"
```

❌ **DON'T**:
```yaml
errorMessage: "Error"  # Too vague, no technical details
```

### 2. Property Resolution

✅ **DO**: Enable for configuration value checks
```yaml
- type: XML_ATTRIBUTE_EXISTS
  params:
    resolveProperties: true  # Resolve ${...} placeholders
```

❌ **DON'T**: Enable for literal token searches
```yaml
- type: GENERIC_TOKEN_SEARCH_FORBIDDEN
  # Property resolution not supported - searches for literal text
```

### 3. Match Mode Selection

| Scenario | Recommended Mode |
|----------|------------------|
| "Every file must have X" | `matchMode: ALL_FILES` |
| "At least one file must have X" | `matchMode: ANY_FILE` |
| "No file should have X" | Use `*_FORBIDDEN` rule type |

### 4. Performance Optimization

| Scenario | Recommendation |
|----------|----------------|
| Simple keyword search | Use `matchMode: SUBSTRING` (faster) |
| Exact Word Match | Use `wholeWord: true` (Simple & Fast) |
| Pattern matching | Use `matchMode: REGEX` (more powerful) |
| Complex Boundaries | Use `REGEX` with `\b` or other regex markers |
| Large file sets | Narrow `filePatterns` as much as possible |

---

## Technology-Specific Guides

### Mule Projects

**Common Property Files**:
- `src/main/resources/mule-app.properties`
- `src/main/resources/config-${env}.properties`

**Property Syntax**:
- `${property.name}` - Standard placeholder
- `p('property.name')` - DataWeave function

**Recommended Rules**:
- `XML_ATTRIBUTE_EXISTS` with `resolveProperties: true`
- `JSON_VALIDATION_REQUIRED` for `mule-artifact.json`
- `POM_VALIDATION_REQUIRED` for Maven dependencies

### Spring Boot Projects

**Common Property Files**:
- `src/main/resources/application.yml`
- `src/main/resources/application-${profile}.properties`

**Property Syntax**:
- `${property.name}` - Standard placeholder
- `@Value("${property}")` - Annotation-based

### TIBCO Projects

**Common Property Files**:
- `*.substvar` files
- `*.properties` files

**Property Syntax**:
- `%%PROPERTY_NAME%%` - Global variable
- `$_PROPERTY_NAME` - Process variable

---

## Troubleshooting

### Property Resolution Not Working

**Problem**: Properties not being resolved in XML attributes

**Solution**:
1. Verify `resolveProperties: true` is set in rule params
2. Check property files are in expected locations
3. Verify syntax pattern matches your technology (see table above)
4. Check property file paths in `config.propertyResolution.propertyFiles.patterns`

### {FOUND_ITEMS} Token Empty

**Problem**: `{FOUND_ITEMS}` shows nothing in error message

**Solution**:
1. `{FOUND_ITEMS}` only works with `*_FORBIDDEN` and `*_NOT_EXISTS` rule types
2. Verify you're using a supported rule type (see compatibility matrix above)
3. For other rule types, use `{DEFAULT_MESSAGE}` instead

### Version Comparison Failing

**Problem**: `minVersion: "4.9.0"` not matching `4.9.7`

**Solution**:
1. Ensure version format is consistent (use quotes for string versions)
2. Check for special suffixes (`LTS`, `SNAPSHOT`) - these are handled specially
3. Use `{DEFAULT_MESSAGE}` in error to see actual vs expected versions
