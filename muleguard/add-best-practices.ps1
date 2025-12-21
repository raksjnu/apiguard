# Add Best Practices to remaining files

$files = @{
    "CODE_XML_ELEMENT_CONTENT_FORBIDDEN.md" = @"

## Best Practices

### When to Use This Rule
- ✅ Blocking deprecated or forbidden content in XML elements
- ✅ Preventing usage of insecure configuration values
- ✅ Enforcing cleanup of temporary/debug content
- ✅ Standardizing element content by removing non-standard values

### Common Patterns
``````yaml
# Block debug/test content
elementContentPairs:
  - element: "logger"
    forbiddenTokens: ["DEBUG", "TEST", "TEMP"]

# Prevent insecure values
elementContentPairs:
  - element: "tls:context"
    forbiddenTokens: ["SSLv3", "TLSv1.0"]
``````
"@;

    "CODE_XML_XPATH_EXISTS.md" = @"

## Best Practices

### When to Use This Rule
- ✅ Complex XML structure validation requiring XPath expressions
- ✅ Validating nested element relationships
- ✅ Checking conditional element presence
- ✅ Advanced attribute and element combinations

### XPath Pattern Examples
``````yaml
# Check nested elements
xpathExpressions:
  - "//flow[@name='main']/http:listener"
  
# Validate attribute combinations
xpathExpressions:
  - "//tls:context[@enabledProtocols='TLSv1.2']"
``````

### When to Use XPath vs Other Rules
- Use **XML_ATTRIBUTE_EXISTS** for simple attribute checks
- Use **XML_XPATH_EXISTS** for complex nested validations
- Use **XML_ELEMENT_CONTENT** for element text validation
"@;

    "CODE_XML_XPATH_NOT_EXISTS.md" = @"

## Best Practices

### When to Use This Rule
- ✅ Blocking deprecated XML structures
- ✅ Preventing forbidden element combinations
- ✅ Enforcing removal of legacy configurations
- ✅ Complex forbidden pattern detection

### Common Patterns
``````yaml
# Block deprecated elements
xpathExpressions:
  - "//deprecated-connector"
  - "//legacy:config"

# Prevent insecure combinations
xpathExpressions:
  - "//http:listener[@protocol='HTTP']"
``````
"@;

    "CODE_GENERIC_TOKEN_SEARCH.md" = @"

## Best Practices

### When to Use This Rule
- ✅ Searching for specific keywords or patterns across all files
- ✅ Validating presence of required code patterns
- ✅ Ensuring specific imports or dependencies are used
- ✅ Finding configuration references

### Search Pattern Tips
``````yaml
# Case-insensitive search
searchTokens: ["TODO", "FIXME"]
caseSensitive: false

# Regex patterns for complex searches
searchTokens: ["import.*Logger"]
isRegex: true
``````
"@;

    "CODE_GENERIC_TOKEN_SEARCH_FORBIDDEN.md" = @"

## Best Practices

### When to Use This Rule
- ✅ Blocking hardcoded credentials or secrets
- ✅ Preventing usage of deprecated APIs
- ✅ Detecting forbidden imports or dependencies
- ✅ Finding and removing debug/test code

### Security Patterns
``````yaml
# Block hardcoded secrets
forbiddenTokens: ["password=", "apiKey=", "secret="]

# Prevent deprecated APIs
forbiddenTokens: ["@Deprecated", "LegacyClass"]
``````
"@;

    "CODE_GENERIC_TOKEN_SEARCH_REQUIRED.md" = @"

## Best Practices

### When to Use This Rule
- ✅ Ensuring required imports are present
- ✅ Validating presence of security annotations
- ✅ Checking for required code patterns
- ✅ Enforcing coding standards

### Common Patterns
``````yaml
# Require security annotations
requiredTokens: ["@Secured", "@PreAuthorize"]

# Ensure logging framework
requiredTokens: ["import.*slf4j"]
isRegex: true
``````
"@;

    "CONFIG_GENERIC_PROPERTY_FILE_CHECK.md" = @"

## Best Practices

### When to Use This Rule
- ✅ Validating property file structure and format
- ✅ Ensuring required properties exist across environments
- ✅ Checking for environment-specific configurations
- ✅ Enforcing property naming conventions

### Environment Validation
``````yaml
# Validate all environments have required properties
environments: ["DEV", "QA", "PROD"]
requiredProperties: ["app.name", "app.version", "environment"]
``````
"@;

    "CONFIG_MANDATORY_PROPERTY_VALUE_CHECK.md" = @"

## Best Practices

### When to Use This Rule
- ✅ Enforcing specific property values per environment
- ✅ Validating security settings (SSL, encryption)
- ✅ Ensuring correct log levels for production
- ✅ Standardizing configuration values

### Production Safety
``````yaml
# Enforce production log levels
environments: ["PROD"]
properties:
  - name: "log.level"
    values: ["INFO", "WARN", "ERROR"]
  - name: "ssl.enabled"
    values: ["true"]
``````
"@;

    "CONFIG_MANDATORY_SUBSTRING_CHECK.md" = @"

## Best Practices

### When to Use This Rule
- ✅ Validating property values contain required substrings
- ✅ Checking URL formats and patterns
- ✅ Ensuring property values follow naming conventions
- ✅ Validating configuration patterns

### Pattern Validation
``````yaml
# Ensure URLs use HTTPS
properties:
  - name: "api.url"
    requiredSubstrings: ["https://"]
    
# Validate naming conventions
properties:
  - name: "app.name"
    requiredSubstrings: ["api-", "-service"]
``````
"@;

    "CONFIG_OPTIONAL_PROPERTY_VALUE_CHECK.md" = @"

## Best Practices

### When to Use This Rule
- ✅ Validating optional properties when they exist
- ✅ Ensuring correct values for non-mandatory configs
- ✅ Checking optional feature flags
- ✅ Validating conditional configurations

### Optional Feature Validation
``````yaml
# Validate optional feature flags if present
properties:
  - name: "feature.experimental"
    values: ["true", "false"]
  - name: "cache.enabled"
    values: ["true", "false"]
``````
"@;
};

foreach ($file in $files.Keys) {
    $path = ".\src\main\resources\docs\$file";
    if (Test-Path $path) {
        $content = Get-Content $path -Raw;
        if ($content -notmatch '## Best Practices') {
            $content = $content -replace '(## Related Rule Types)', ($files[$file] + "`r`n`r`n`$1");
            Set-Content $path $content -NoNewline;
            Write-Host "Added Best Practices to: $file";
        } else {
            Write-Host "Skipped (already has Best Practices): $file";
        }
    }
}
Write-Host "`nBest Practices addition complete!"
