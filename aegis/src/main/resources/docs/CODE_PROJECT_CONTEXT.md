# PROJECT_CONTEXT

**Rule Type:** `CODE` | **Trigger Engine**

## Overview

A contextual trigger that evaluates the properties of the project itself (primarily the project folder name). It is typically used within a `CONDITIONAL_CHECK` to enable or disable rules based on project classification, technology tiers, or organizational layers.

## Parameters

### Required Parameters

Choose at least one of the following to match the project name:

| Parameter | Type | Description |
|-----------|------|-------------|
| `nameEquals` | String | Exact match of the project folder name |
| `nameContains` | String | Matches if the project name contains this substring |
| `nameMatches` | String | Regex pattern to match the project name |

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `ignoreCase` | Boolean | `true` | Perform case-insensitive matching |
| `caseSensitive` | Boolean | `false` | Perform case-sensitive matching (overrides `ignoreCase`) |

## Configuration Examples

### Example 1: Target Specific API Layers (MuleSoft/generic)

Triggers only for projects classified as "Experience" layers (containing `-exp-` in their name).

```yaml
preconditions:
  - type: PROJECT_CONTEXT
    params:
      nameContains: "-exp-"
      ignoreCase: true
```

### Example 2: Target Microservices (Generic)

Triggers for projects following a specific organizational naming convention for microservices.

```yaml
preconditions:
  - type: PROJECT_CONTEXT
    params:
      nameMatches: "^svc-[a-z0-9-]+$"
```

### Example 3: Tiered Validation (Frontend vs Backend)

Apply different security rules for frontend projects.

```yaml
- id: "FRONTEND-SECURITY-POLICY"
  checks:
    - type: CONDITIONAL_CHECK
      params:
        preconditions:
          - type: PROJECT_CONTEXT
            params: { nameContains: "-ui" }
        onSuccess:
          # Run frontend-specific checks here (e.g., package.json for unapproved libs)
```

## Best Practices

- **Naming Conventions**: Use **PROJECT_CONTEXT** to leverage established naming conventions (e.g., prefixing services with `svc-`, or including environment tags like `-prod-`).
- **Layered Security**: Use it to differentiate security requirements between customer-facing layers (Experience/Frontend) and protected internal layers (System/Data).
- **Cleanup Migration**: Identify legacy projects by name (e.g., `v1-`) to apply "Sunset" rules that encourage migration.

## Related Check Types

- **[CONDITIONAL_CHECK](CONDITIONAL_CHECK.md)**: The parent engine that uses these context triggers.
- **[FILE_EXISTS](CODE_FILE_EXISTS.md)**: Trigger based on physical file presence (e.g., existence of `pom.xml` vs `package.json`).
