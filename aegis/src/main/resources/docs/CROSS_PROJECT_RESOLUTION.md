# Cross-Project Property Resolution

Aegis supports resolving property placeholders (`${property}`) across different project types. This is essential when your source code (`CODE`) references properties defined in a separate configuration project (`CONFIG`).

## Key Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `resolveProperties` | Boolean | `false` | Enables `${...}` resolution within the local project. |
| `resolveLinkedConfig`| Boolean | `false` | Fallback to searching in the **linked CONFIG project**. |
| `includeLinkedConfig`| Boolean | `false` | Also scan files within the linked project for the current check. |

## How it Works

1. **Detection**: Aegis identifies projects based on your `config` section (e.g., folder names with `_config` are identified as `CONFIG`).
2. **Linking**: When multiple projects are scanned together, Aegis automatically links them if they form a CODE-CONFIG pair.
3. **Resolution**:
   - If `resolveProperties: true`, Aegis looks for values in the project's own property files.
   - If `resolveLinkedConfig: true` and the property isn't found locally, Aegis queries the linked `CONFIG` project.

## Examples

### 1. Database Connection String Security
Ensure that SQL Server connection strings in your Mule application reference properties defined in your global configuration.

```yaml
- id: "BANK-021"
  name: "Enforce SQL Server DB connection String Security"
  appliesTo: ["CODE", "CONFIG"]
  checks:
    - type: GENERIC_TOKEN_SEARCH_REQUIRED
      params:
        filePatterns: ["src/main/mule/*.xml"]
        tokens: ["${sql.db.url}"]
        resolveProperties: true
        resolveLinkedConfig: true
        includeLinkedConfig: true
```

### 2. Secure Property Format
Validate that secure properties (prefixed with `secure::`) are correctly defined in your configuration files while being referenced in your source code.

```yaml
- id: "Secure::Property-001"
  name: "Validate Secure Property Format"
  appliesTo: ["CODE", "CONFIG"]
  checks:
    - type: GENERIC_TOKEN_SEARCH_REQUIRED
      params:
        filePatterns: ["src/main/resources/*.properties", "src/main/mule/*.xml"]
        tokens: ["\\$\\{secure::.*\\}"]
        isRegex: true
        resolveProperties: true
        resolveLinkedConfig: true
        includeLinkedConfig: true
```

## Report Visualization

When a property is resolved from a linked configuration project, it is highlighted in the Aegis report with a **CONFIG -** label, making it easy to identify the source of the value.

> [!TIP]
> Use the `{PROPERTY_RESOLVED}` token in your success/error messages to display the resolution path.
