# CONDITIONAL_CHECK

## Overview

The **Universal "If-Then" Engine**. This is the most powerful check type in Aegis. It allows you to create complex, multi-step validation logic by combining any other check type as a trigger (`preconditions`) and an action (`onSuccess`).

It is not limited to code analysis! You can combine configuration checks, XML/JSON checks, file existence checks, and project metadata checks in any combination.

## Use Cases

* **Context-Specific Rules**: "If this is a frontend project (`-ui-`), THEN it must have a `LICENSE` file."
* **Cross-File Dependencies**: "If `package.json` contains `react`, THEN `src/App.tsx` must exist."
* **Technology Standards**: "If an `IBM MQ` connector is found (XML), THEN the project must have the corresponding client library dependency (POM)."
* **Environment Validation**: "If the `env` property is set to `PROD` (Config), THEN debug logging must be disabled (Log configuration)."

## How It Works

This check executes in two phases:
1. **Preconditions (IF)**: The engine runs these checks first.
    * If ALL preconditions match (pass), the engine proceeds to step 2.
    * If ANY precondition fails (doesn't match), the rule is **SKIPPED** (it does not fail, it just implies this rule doesn't apply to this project).

2. **OnSuccess (THEN)**: The engine runs these checks ONLY if preconditions were met.
    * If these checks pass, the Rule PASSES.
    * If any of these checks fail, the Rule FAILS.

### Supported Triggers & Actions
**ANY** check type can be used in either `preconditions` or `onSuccess`. Common triggers include:

* **Project Identity**: `PROJECT_CONTEXT` (Name matching)
* **File Presence**: `FILE_EXISTS` (e.g., specific config files)
* **XML/JSON Content**: `XML_XPATH_EXISTS` or `JSON_VALIDATION_REQUIRED`
* **Properties**: `MANDATORY_PROPERTY_VALUE_CHECK` (e.g., environment checks)
* **Dependencies**: `POM_VALIDATION_REQUIRED` (Check for Maven libraries)

## Parameters

### Required Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `preconditions` | List<Map> | A list of standard check definitions that define **WHEN** this rule applies. |
| `onSuccess` | List<Map> | A list of standard check definitions that define **WHAT** must be validated if the preconditions are met. |
| `disableFiltering` | Boolean | (Optional) Defaults to `false`. If `true`, the `onSuccess` checks will run against the **entire project scope** instead of being restricted to files that matched the `preconditions`. |

## Advanced: Scope Control & Filtering

By default, `CONDITIONAL_CHECK` performs **Scope Shrinking**:
1.  Preconditions match a subset of files (e.g., only files where `feature.enabled=true`).
2.  `onSuccess` checks are then ONLY executed against that specific subset.

### The "Silent Skip" Problem (False Positives)
In consistency checks (e.g., "If enabled in one file, it must be enabled in ALL files"), this default behavior can cause a "silent skip". If a file has `feature.enabled=false`, it doesn't match the precondition and is excluded from the `onSuccess` check. Therefore, an "All Files" consistency check will never see the inconsistency!

### The Solution: `disableFiltering: true`

Set this to `true` when you need the `onSuccess` check to evaluate the **Full Project** regardless of which specific files triggered the precondition. This is essential for **Global Consistency Rules**.

## Configuration Examples

### Example 1: Frontend Resource Security
**Scenario**: "If this is a Web UI project (Name) AND it uses a private repository (JSON), THEN it must have a security policy file."

```yaml
- id: "RULE-COND-FE-01"
  name: "Web UI Security Policy"
  checks:
    - type: CONDITIONAL_CHECK
      params:
        preconditions:
          - type: PROJECT_CONTEXT
            params:
              nameContains: "-ui-"
          - type: JSON_VALIDATION_REQUIRED
            params:
              filePattern: "package.json"
              requiredElements:
                - "publishConfig.registry"
        onSuccess:
          - type: FILE_EXISTS
            params:
              filePatterns: ["SECURITY.md"]
```

### Example 2: Unused Dependency Check (POM + XML)
**Scenario**: "If the Messaging library is in the POM, you MUST actually use it in the XML (prevent bloat)."

```yaml
- id: "RULE-UNUSED-DEP"
  name: "Messaging Library Usage"
  checks:
    - type: CONDITIONAL_CHECK
      params:
        preconditions:
          - type: POM_VALIDATION_REQUIRED
            params:
              validationType: DEPENDENCIES
              dependencies:
                - artifactId: messaging-client
        onSuccess:
          - type: XML_XPATH_EXISTS
            params:
              filePatterns: ["src/main/resources/*.xml"]
              xpathExpressions:
                - xpath: "//*[local-name()='messaging-config']"
              failureMessage: "Messaging dependency found in POM but no usage found in XML."
```

### Example 3: Environment-Specific Enforcement
Only require production metadata if the `PROD` environment is active in the configuration.

```yaml
- id: "RULE-ENV-PROD"
  name: "Production Metadata Check"
  checks:
    - type: CONDITIONAL_CHECK
      params:
        preconditions:
          - type: MANDATORY_PROPERTY_VALUE_CHECK
            params: { properties: [{ name: "env", values: ["PROD"] }] }
        onSuccess:
          - type: JSON_VALIDATION_REQUIRED
            params:
              filePattern: "metadata.json"
              requiredElements: ["ops_contact", "sla_tier"]

### Example 4: Global Policy Consistency (Advanced)
**Scenario**: "If a policy is enabled in ANY file (e.g. `valid_test.policy`), it must be enabled in ALL policy files within the project."

> [!IMPORTANT]
> This requires `disableFiltering: true` to ensure the Success check evaluates EVERY file in the project, not just those that matched the precondition.

```yaml
- id: "BANK-POLICY-CONSISTENCY"
  name: "Enforce Global Policy Consistency"
  checks:
    - type: CONDITIONAL_CHECK
      params:
        disableFiltering: true
        preconditions:
          - type: MANDATORY_PROPERTY_VALUE_CHECK
            params:
              filePatterns: ["Policies/*.policy"]
              matchMode: ANY_FILE
              properties:
                - name: "ratelimit.policy.applied"
                  values: ["true"]
        onSuccess:
          - type: MANDATORY_PROPERTY_VALUE_CHECK
            params:
              filePatterns: ["Policies/*.policy"]
              matchMode: ALL_FILES
              properties:
                - name: "ratelimit.policy.applied"
                  values: ["true"]
              errorMessage: "Consistency Error: ratelimit.policy.applied is true in some files but false in others."
```

## Related Check Types

* **[PROJECT_CONTEXT](CODE_PROJECT_CONTEXT.md)**: Trigger based on project metadata.
* **[FILE_EXISTS](CODE_FILE_EXISTS.md)**: Trigger based on file presence.
* Any standard XML, POM, JSON, or Property check.
