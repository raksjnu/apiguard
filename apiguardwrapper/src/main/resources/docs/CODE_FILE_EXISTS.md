# FILE_EXISTS

**Rule Type:** `CODE` | **Trigger Engine**

## Overview

A trigger that checks for the presence of one or more files in the project. It is typically used within a `CONDITIONAL_CHECK` to ensure specific templates, security files, or configuration assets exist. This rule helps enforce project structure standards across different technologies.

## Parameters

### Required Parameters

| Parameter | Type | Description |
| :--- | :--- | :--- |
| `filePatterns` | List | Glob patterns to search for files (e.g., `src/resources/secure-*.properties`) |

### Optional Parameters

| Parameter | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `failureMessage` | String | (Default) | Custom message if expected files are not found |

## Configuration Examples

### Example 1: Mandatory Security Documentation

Ensures that any project must have a `SECURITY.md` file in the root directory.

```yaml
- id: "RULE-FILE-SECURITY"
  name: "Mandatory Security Documentation"
  description: "Ensures every project contains a security policy file"
  enabled: true
  severity: HIGH
  checks:
    - type: FILE_EXISTS
      params:
        filePatterns: ["SECURITY.md"]
        failureMessage: "Project missing mandatory SECURITY.md file in root"
```

### Example 2: API Specification Trigger

Ensures that an API specification file (RAML, OAS, or Swagger) exists in the expected directory for projects tagged as APIs.

```yaml
- id: "RULE-API-SPEC-EXISTS"
  checks:
    - type: CONDITIONAL_CHECK
      params:
        preconditions:
          - type: PROJECT_CONTEXT
            params: { nameContains: "-api-" }
        onSuccess:
          - type: FILE_EXISTS
            params:
              filePatterns: 
                - "src/main/resources/api/*.raml"
                - "src/main/resources/api/*.yaml"
                - "src/main/resources/api/*.json"
              failureMessage: "API project missing specification file in src/main/resources/api/"
```

## Best Practices

- **Use as a Precondition**: detect "legacy" projects (e.g., presence of old framework files) to apply specific migration or compatibility rules.
- **Enforce Readme/Security**: Use **FILE_EXISTS** as an `onSuccess` check to ensure standard documentation is always present.
- **Technology Detection**: Combine with **[PROJECT_CONTEXT](CODE_PROJECT_CONTEXT.md)** to target specific file requirements for specific project types.

## Related Check Types

- **[PROJECT_CONTEXT](CODE_PROJECT_CONTEXT.md)**: Trigger based on project metadata or folder names.

## Solution Patterns and Technology References

The following table serves as a quick reference for enforcing file existence across various project types.

| Technology | Best Practice Goal | Key Files Checked | Purpose |
| :--- | :--- | :--- | :--- |
| **☕ Java/Maven** | Build Consistency | `pom.xml` | Dependency management |
| **📦 Node.js** | Project Metadata | `package.json` | Scripts and dependencies |
| **🐎 MuleSoft 4** | Project Structure | `mule-artifact.json` | Core runtime configuration |
| **🐍 Python** | Environment Setup | `requirements.txt` | Package dependencies |
| **⚡ TIBCO BW** | Module Validity | `*.module`, `MANIFEST.MF` | OSGi/Module configuration |
| **🐳 Docker** | Containerization | `Dockerfile` | Image build definition |

### ☕ Java / Spring Boot Patterns

Ensure that Maven or Gradle build files are present to guarantee the project can be built in CI/CD.

```yaml
id: "JAVA-BUILD-DESCRIPTOR"
name: "Require Build Descriptor"
checks:
  - type: FILE_EXISTS
    params:
      filePatterns: ["pom.xml", "build.gradle", "build.gradle.kts"]
      failureMessage: "Java projects must have a pom.xml or build.gradle file"
```

### 📦 Node.js Patterns

The `package.json` file is the heart of any Node.js application.

```yaml
id: "NODE-PACKAGE-JSON"
name: "Require Package.json"
checks:
  - type: FILE_EXISTS
    params:
      filePatterns: ["package.json"]
      failureMessage: "Node.js projects must contain a package.json file"
```

### 🐳 DevOps / Docker Patterns

For projects intended for containerization, a `Dockerfile` is essential.

```yaml
id: "DOCKER-FILE-REQUIRED"
name: "Require Dockerfile"
checks:
  - type: FILE_EXISTS
    params:
      filePatterns: ["Dockerfile", "docker-compose.yml"]
```
