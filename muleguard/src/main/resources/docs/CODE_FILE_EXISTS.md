# FILE_EXISTS

**Rule Type:** `CODE` | **Trigger Engine**

## Overview

A trigger that checks for the presence of one or more files in the project. It is typically used within a `CONDITIONAL_CHECK` to ensure specific templates, security files, or configuration assets exist.

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `filePatterns` | List<String> | Glob patterns to search for files (e.g., `src/main/resources/secure-*.properties`) |

### Optional Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `failureMessage` | String | (Default) | Custom message if expected files are not found |

## Configuration Examples

### Example 1: Mandatory Secure Properties

Ensures that any project must have a secure properties file if triggered.

```yaml
onSuccess:
  - type: FILE_EXISTS
    params:
      filePatterns:
        - "src/main/resources/secure-*.properties"
      failureMessage: "Project missing mandatory secure properties file in src/main/resources"
```

### Example 2: API Specification Trigger

Ensure `api.raml` exists for Experience APIs.

```yaml
- id: "RULE-SPEC-01"
  checks:
    - type: CONDITIONAL_CHECK
      params:
        preconditions:
          - type: PROJECT_CONTEXT
            params: { nameContains: "-exp-" }
        onSuccess:
          - type: FILE_EXISTS
            params:
              filePatterns: ["src/main/resources/api/*.raml"]
```

## Best Practices

- Use **FILE_EXISTS** as an `onSuccess` check to enforce documentation or security file presence.
- Use it as a `precondition` to detect "legacy" projects (e.g., presence of `mule-project.xml`) and apply migration rules.

## Related Check Types

- **[CONDITIONAL_CHECK](CODE_CONDITIONAL_CHECK.md)**: The parent engine that uses these triggers.
- **[PROJECT_CONTEXT](CODE_PROJECT_CONTEXT.md)**: Trigger based on project metadata.
