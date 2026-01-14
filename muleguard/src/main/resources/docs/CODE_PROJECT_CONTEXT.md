# PROJECT_CONTEXT

**Rule Type:** `CODE` | **Trigger Engine**

## Overview

A contextual trigger that evaluates the properties of the project itself (e.g., project name). It is typically used within a `CONDITIONAL_CHECK` to enable or disable rules based on project classification.

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

### Example 1: Target Experience APIs

Triggers only for projects containing `-exp-` in their name.

```yaml
preconditions:
  - type: PROJECT_CONTEXT
    params:
      nameContains: "-exp-"
      ignoreCase: true
```

### Example 2: Regex Mapping

Triggers for projects following a specific organizational naming convention.

```yaml
preconditions:
  - type: PROJECT_CONTEXT
    params:
      nameMatches: "^[a-z]{3}-[a-z]+-api$"
```

## Best Practices

- Use **PROJECT_CONTEXT** to differentiate between Experience (EAPI), Process (PAPI), and System (SAPI) layers if your naming convention supports it.
- Combine with `POM_VALIDATION` to verify that the project structure matches its declared context.

## Related Check Types

- **[CONDITIONAL_CHECK](CODE_CONDITIONAL_CHECK.md)**: The parent engine that uses these context triggers.
- **[FILE_EXISTS](CODE_FILE_EXISTS.md)**: Trigger based on physical file presence.
