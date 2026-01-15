# CONDITIONAL_CHECK

## Overview

The **Universal "If-Then" Engine**. This is the most powerful check type in MuleGuard. It allows you to create complex, multi-step validation logic by combining any other check type as a trigger (`preconditions`) and an action (`onSuccess`).

IT IS NOT LIMITED TO CODE! You can combine Configuration checks, XML checks, File checks, and Project Name checks in any combination.

## Use Cases

*   **Context-Specific Rules**: "If this is an Experience API (`-exp-`), THEN it must have Autodiscovery."
*   **Cross-File Dependencies**: "If `secure-prod.properties` exists, THEN all `CODE` `config.xml` must use `secure::`."
*   **Technology Standards**: "If an `IBM MQ` connector is found (XML), THEN the project must have the `mule-ibm-mq-connector` dependency (POM)."
*   **Environment Validation**: "If `env` property is `PROD` (Config), THEN `munit-maven-plugin` must be disabled (POM)."

## How It Works

This check executes in two phases:
1.  **Preconditions (IF)**: The engine runs these checks first.
    *   If ALL preconditions match (pass), the engine proceeds to step 2.
    *   If ANY precondition fails (doesn't match), the rule is **SKIPPED** (it does not fail, it just implies this rule doesn't apply to this project).
2.  **OnSuccess (THEN)**: The engine runs these checks ONLY if preconditions were met.
    *   If these checks pass, the Rule PASSES.
    *   If any of these checks fail, the Rule FAILS.

### Supported Triggers & Actions
**ANY** check type can be used in either `preconditions` or `onSuccess`. Common triggers include:

*   **Project Identity**: `PROJECT_CONTEXT` (Name matching)
*   **File Presence**: `FILE_EXISTS` (e.g., specific config files)
*   **XML Content**: `XML_XPATH_EXISTS` (Detect connectors, specific configs)
*   **Properties**: `MANDATORY_PROPERTY_VALUE_CHECK` (Check valid environmental configs)
*   **Dependencies**: `POM_VALIDATION_REQUIRED` (Check for libraries)

## Parameters

### Required Parameters

| Parameter | Type | Description |
| :--- | :--- | :--- |
| `preconditions` | `List<Check>` | A list of standard check definitions that define **WHEN** this rule applies. |
| `onSuccess` | `List<Check>` | A list of standard check definitions that define **WHAT** must be validated if the preconditions are met. |

## Configuration Examples

### Example 1: The "Universal" Rule (Code + Config + File)
**Scenario**: "If this is an Experience API (Name) AND it uses Salesforce (XML), THEN it must have a `secure.properties` file."

```yaml
- id: "RULE-COMPLEX-001"
  name: "Experience API Salesforce Security"
  checks:
    - type: CONDITIONAL_CHECK
      params:
        preconditions:
          - type: PROJECT_CONTEXT
            params:
              nameContains: "-exp-"
          - type: XML_XPATH_EXISTS
            params:
              filePatterns: ["src/main/mule/*.xml"]
              xpathExpressions:
                - xpath: "//*[local-name()='sfdc-config']"
        onSuccess:
          - type: FILE_EXISTS
            params:
              filePatterns: ["src/main/resources/secure.properties"]
```

### Example 2: Architecture Standard (POM + XML)
**Scenario**: "If the JMS Connector dependency is in the POM, you MUST actually use it in the XML (prevent unused dependencies)."

```yaml
- id: "RULE-ARCH-002"
  name: "JMS Dependency Usage"
  checks:
    - type: CONDITIONAL_CHECK
      params:
        preconditions:
          - type: POM_VALIDATION_REQUIRED
            params:
              validationType: DEPENDENCIES
              dependencies:
                - artifactId: mule-jms-connector
        onSuccess:
          - type: XML_XPATH_EXISTS
            params:
              filePatterns: ["src/main/mule/*.xml"]
              xpathExpressions:
                - xpath: "//*[local-name()='config' and contains(namespace-uri(), 'jms')]"
              failureMessage: "JMS dependency found in POM but no JMS Config found in XML."
```

### Example 3: File-Based Trigger

Only require Production secure properties if the PROD environment is active.

```yaml
- id: "RULE-PROD-SEC"
  name: "Prod Secure Config Enforcement"
  checks:
    - type: CONDITIONAL_CHECK
      params:
        preconditions:
          - type: MANDATORY_PROPERTY_VALUE_CHECK
            params: { properties: [{ name: "env", values: ["PROD"] }] }
        onSuccess:
          - type: FILE_EXISTS
            params: { filePatterns: ["src/main/resources/secure-prod.properties"] }
```

## Related Check Types

- **[PROJECT_CONTEXT](CODE_PROJECT_CONTEXT.md)**: Trigger based on project metadata.
- **[FILE_EXISTS](CODE_FILE_EXISTS.md)**: Trigger based on file presence.
- Any standard XML, POM, JSON, or Property check.
