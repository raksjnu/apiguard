# QUICK REFERENCE

**Comprehensive Feature Matrix & Configuration Guide**

This section provides tabular references for all Aegis features, tokens, operators, and technology-specific configurations.

---

## Message Tokens

Tokens that can be used in `successMessage` and `errorMessage` fields to customize rule output.

| Token | Description | Supported Rule Types | Example Usage |
|-------|-------------|---------------------|---------------|
| `{RULE_ID}` | The ID of the current rule | ALL | `"Rule {RULE_ID} passed"` |
| `{DEFAULT_MESSAGE}` | Technical details of check result (recommended) | ALL | `"✓ Success! {DEFAULT_MESSAGE}"` |
| `{CORE_DETAILS}` | Alias for `{DEFAULT_MESSAGE}` | ALL | `"Details: {CORE_DETAILS}"` |
| `{CHECKED_FILES}` | Comma-separated list of files scanned | XML_*, JSON_*, TOKEN_SEARCH_*, POM_* | `"Checked: {CHECKED_FILES}"` |
| `{FOUND_ITEMS}` | Specific forbidden items found (NEW) | XML_ATTRIBUTE_NOT_EXISTS, JSON_VALIDATION_FORBIDDEN, GENERIC_TOKEN_SEARCH_FORBIDDEN | `"Found: {FOUND_ITEMS}"` |
| `{FAILURES}` | List of specific failure details | XML_*, JSON_*, POM_* | `"Failures:\n{FAILURES}"` |

**Best Practice**: Always use `{DEFAULT_MESSAGE}` to include technical details. Use `{FOUND_ITEMS}` for forbidden checks to show exactly what was detected.

---

## Validation Operators & Parameters

### Version Comparison Operators

| Operator | Type | Description | Supported Rule Types | Example |
|----------|------|-------------|---------------------|---------|
| `minVersion` | String/Number | Minimum required version (inclusive) | POM_VALIDATION_REQUIRED, JSON_VALIDATION_REQUIRED | `minVersion: "4.9.0"` |
| `maxVersion` | String/Number | Maximum allowed version (inclusive) | POM_VALIDATION_REQUIRED, JSON_VALIDATION_REQUIRED | `maxVersion: "5.0.0"` |
| `exactVersion` | String/Number | Exact version match required | POM_VALIDATION_REQUIRED | `exactVersion: "4.9.7"` |

**Version Comparison Logic**:
- Supports semantic versioning (e.g., `4.9.7` > `4.9.0`)
- Handles special suffixes: `4.9.LTS`, `1.0.0-SNAPSHOT`
- Numeric comparison for simple versions

### Match Modes

| Mode | Description | Use Case | Supported Rule Types |
|------|-------------|----------|---------------------|
| `ALL_FILES` | Rule passes ONLY if EVERY file satisfies condition | Consistency checks | XML_*, JSON_*, TOKEN_SEARCH_* |
| `ANY_FILE` | Rule passes if AT LEAST ONE file satisfies condition | Existence checks | XML_*, JSON_*, TOKEN_SEARCH_*, FILE_EXISTS |
| `NONE_OF_FILES` | Rule passes ONLY if NO files satisfy condition | Forbidden pattern checks | TOKEN_SEARCH_FORBIDDEN |
| `SUBSTRING` | Simple text matching (fast) | Literal token search | GENERIC_TOKEN_SEARCH_* |
| `REGEX` | Regular expression matching (powerful) | Pattern matching, exact word boundaries | GENERIC_TOKEN_SEARCH_*, MANDATORY_PROPERTY_VALUE_CHECK |

### Logic Operators

| Operator | Type | Default | Description | Example |
|----------|------|---------|-------------|---------|
| `failIfFound` | Boolean | `false` | Inverts match logic (fail if pattern IS found) | `failIfFound: true` |
| `caseSensitive` | Boolean | `true` | Enable/disable case-sensitive matching | `caseSensitive: false` |
| `negativeMatch` | Boolean | `false` | Inverts match result locally | `negativeMatch: true` |

### Property Resolution (NEW)

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `resolveProperties` | Boolean | Enable property placeholder resolution | `resolveProperties: true` |
| `propertySyntax` | List<String> | Regex patterns for property placeholders | See table below |

---

## Property Resolution Patterns by Technology

Configure property resolution syntax in `config.propertyResolution.syntaxPatterns`:

| Technology | Syntax Pattern | Regex Pattern | Property Files | Example |
|------------|----------------|---------------|----------------|---------|
| **Mule** | `${property}` | `\$\{([^}]+)\}` | `*.properties`, `*.yaml` | `${mule.env}` |
| **Mule DataWeave** | `p('property')` | `p\(['"]([^'"]+)['"]\)` | `*.properties` | `p('api.key')` |
| **Spring Boot** | `${property}` | `\$\{([^}]+)\}` | `application.yml`, `*.properties` | `${server.port}` |
| **Spring** | `@Value` | `@Value\("([^"]+)"\)` | `*.properties` | `@Value("${db.url}")` |
| **TIBCO** | `%%property%%` | `%%([^%]+)%%` | `*.substvar`, `*.properties` | `%%ENV_NAME%%` |
| **TIBCO BW** | `$_property` | `\$_([A-Za-z0-9_]+)` | `*.substvar` | `$_DATABASE_URL` |
| **Python** | `${VAR}` | `\$\{([^}]+)\}` | `.env` | `${DATABASE_URL}` |
| **Node.js** | `process.env.VAR` | `process\.env\.([A-Za-z0-9_]+)` | `.env` | `process.env.PORT` |
| **Kubernetes** | `$(VAR)` | `\$\(([^)]+)\)` | ConfigMaps, Secrets | `$(DATABASE_HOST)` |

**Configuration Example**:

```yaml
config:
  propertyResolution:
    enabled: true
    syntaxPatterns:
      - '\$\{([^}]+)\}'           # Mule/Spring: ${prop}
      - 'p\([''"]([^''"]+)[''"]\)' # Mule DataWeave: p('prop')
      - '%%([^%]+)%%'              # TIBCO: %%prop%%
    propertyFiles:
      patterns:
        - "src/main/resources/**/*.properties"
        - "src/main/resources/**/*.yaml"
        - "**/*.substvar"  # TIBCO
```

**When Property Resolution Applies**:
- ✅ `XML_ATTRIBUTE_EXISTS` - Resolves attribute values before comparison
- ✅ `XML_ELEMENT_CONTENT_REQUIRED` - Resolves text content
- ✅ `JSON_VALIDATION_REQUIRED` - Resolves JSON values
- ❌ `GENERIC_TOKEN_SEARCH_FORBIDDEN` - Searches for literal tokens (no resolution)
- ❌ `XML_XPATH_NOT_EXISTS` - Structural checks (no value resolution)

---

## Rule Type Compatibility Matrix

### Feature Support by Rule Type

| Feature | XML_ATTRIBUTE_EXISTS | XML_XPATH_EXISTS | JSON_VALIDATION_REQUIRED | TOKEN_SEARCH_* | POM_VALIDATION_* |
|---------|---------------------|------------------|-------------------------|----------------|------------------|
| `{CHECKED_FILES}` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `{FOUND_ITEMS}` | ✅ (NOT_EXISTS) | ❌ | ✅ (FORBIDDEN) | ✅ (FORBIDDEN) | ❌ |
| `minVersion` | ❌ | ❌ | ✅ | ❌ | ✅ |
| `matchMode` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `resolveProperties` | ✅ | ❌ | ✅ | ❌ | ❌ |
| `filePatterns` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `environments` | ✅ | ✅ | ✅ | ✅ | ✅ |

### Parameter Reference by Rule Type

#### XML_ATTRIBUTE_EXISTS / XML_ATTRIBUTE_NOT_EXISTS

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `filePatterns` | List<String> | ✅ | Glob patterns for XML files |
| `elements` | List<String> | ✅ | Element names to check |
| `forbiddenAttributes` | List<String> | ✅ (NOT_EXISTS) | Attributes that must NOT exist |
| `elementAttributeSets` | List<Map> | ✅ (EXISTS) | Element-attribute-value combinations |
| `matchMode` | String | ❌ | `ALL_FILES`, `ANY_FILE` (default: `ALL_FILES`) |
| `resolveProperties` | Boolean | ❌ | Enable property resolution (default: `false`) |

#### GENERIC_TOKEN_SEARCH_REQUIRED / FORBIDDEN

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `filePatterns` | List<String> | ✅ | Glob patterns for files to search |
| `tokens` | List<String> | ✅ | Tokens/patterns to search for |
| `matchMode` | String | ❌ | `SUBSTRING` (default), `REGEX` |
| `caseSensitive` | Boolean | ❌ | Case-sensitive search (default: `true`) |

#### JSON_VALIDATION_REQUIRED / FORBIDDEN

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `filePattern` | String | ✅ | Single file to validate (e.g., `mule-artifact.json`) |
| `minVersions` | Map<String, String> | ❌ | Min version requirements (semantic versioning) |
| `requiredFields` | Map<String, String> | ❌ | Required key-value pairs (exact match) |
| `requiredElements` | List<String> | ❌ | Required keys (existence only) |
| `forbiddenElements` | List<String> | ❌ (FORBIDDEN) | Keys that must NOT exist |
| `resolveProperties` | Boolean | ❌ | Enable property resolution (default: `false`) |

#### POM_VALIDATION_REQUIRED / FORBIDDEN

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `validationType` | String | ✅ | `PARENT`, `DEPENDENCIES`, `PLUGINS`, `PROPERTIES` |
| `parent` | Map | ✅ (PARENT) | Parent POM configuration |
| `dependencies` | List<Map> | ✅ (DEPENDENCIES) | Required/forbidden dependencies |
| `plugins` | List<Map> | ✅ (PLUGINS) | Required/forbidden plugins |
| `properties` | List<Map> | ✅ (PROPERTIES) | Required properties with min/max versions |

---

## Configuration Examples

### Example 1: Property Resolution for Mule

```yaml
rules:
  - id: "SOAP-VERSION-CHECK"
    name: "Validate SOAP Version with Property Resolution"
    checks:
    - type: XML_ATTRIBUTE_EXISTS
      params:
        filePatterns: ["src/main/mule/*-config.xml"]
        elementAttributeSets:
        - element: apikit-soap:config
          attributes: {soapVersion: "SOAP_11"}
        - element: apikit-soap:config
          attributes: {soapVersion: "SOAP_12"}
        resolveProperties: true  # Resolves ${soapVersion} from properties files
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
| Pattern matching | Use `matchMode: REGEX` (more powerful) |
| Exact word match | Use `REGEX` with `\b` boundaries |
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
