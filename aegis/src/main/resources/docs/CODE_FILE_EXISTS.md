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
- id: "SECURE-PROP-CHECK"
  name: "Mandatory Secure Properties"
  description: "Ensures that any project has a secure properties file"
  enabled: true
  severity: HIGH
  checks:
    - type: FILE_EXISTS
      params:
        filePatterns: ["src/main/resources/secure-*.properties"]
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

- **[PROJECT_CONTEXT](CODE_PROJECT_CONTEXT.md)**: Trigger based on project metadata.

## Solution Patterns and Technology References

The following table serves as a quick reference for enforcing file existence across various project types.

| Technology | Best Practice Goal | Key Files Checked | Reason |
| :--- | :--- | :--- | :--- |
| **🐎 MuleSoft 4** | Project Structure | `mule-artifact.json` | Core runtime configuration |
| **☕ Java/Spring** | Build Consistency | `pom.xml` / `build.gradle` | Dependency management |
| **🐍 Python** | Environment Setup | `requirements.txt` | Package dependencies |
| **📦 Node.js** | Project Metadata | `package.json` | Scripts and dependencies |
| **⚡ TIBCO BW** | Module Validity | `.module`, `MANIFEST.MF` | OSGi/Module configuration |
| **🐳 Docker** | Containerization | `Dockerfile` | Image build definition |

### 🐎 MuleSoft 4 Patterns

**Scenario**: A valid Mule 4 project must contain the `mule-artifact.json` descriptor and at least one Mule configuration file to be deployable.

```yaml
id: "MULE-STRUCT-01"
name: "Require Mule Artifact"
description: "Ensure valid Mule 4 project structure"
enabled: true
severity: CRITICAL
checks:
  - type: FILE_EXISTS
    params:
      filePatterns: ["mule-artifact.json", "src/main/mule/*.xml"]
      failureMessage: "Project missing core Mule 4 configuration files"
```

### ☕ Java / Spring Boot Patterns

**Scenario**: To guarantee that a Java project can be built by the CI/CD pipeline, a standard build descriptor must be present at the root.

```yaml
id: "JAVA-STRUCT-01"
name: "Require Build Descriptor"
description: "Ensure Maven or Gradle build file exists"
enabled: true
severity: CRITICAL
checks:
  - type: FILE_EXISTS
    params:
      filePatterns: ["pom.xml", "build.gradle", "build.gradle.kts"]
      failureMessage: "Java projects must have a pom.xml or build.gradle file"
```

### 🐍 Python Patterns

**Scenario**: Python projects typically require a `requirements.txt` for pip or `pyproject.toml` for modern packaging tools like Poetry.

```yaml
id: "PYTHON-STRUCT-01"
name: "Require Dependency Definition"
description: "Ensure requirements.txt or pyproject.toml exists"
enabled: true
severity: HIGH
checks:
  - type: FILE_EXISTS
    params:
      filePatterns: ["requirements.txt", "pyproject.toml"]
```

### 📦 Node.js / TypeScript Patterns

**Scenario**: The `package.json` file is the heart of any Node.js application, defining everything from start scripts to dependencies.

```yaml
id: "NODE-STRUCT-01"
name: "Require Package.json"
description: "Ensure package.json exists globally"
enabled: true
severity: CRITICAL
checks:
  - type: FILE_EXISTS
    params:
      filePatterns: ["package.json"]
```

### ⚡ TIBCO BW 6.x Patterns

**Scenario**: TIBCO BusinessWorks 6 modules are OSGi-based and must contain specific metadata files to be recognized by the runtime.

```yaml
id: "TIBCO-STRUCT-01"
name: "Require TIBCO Module Descriptor"
description: "Ensure .module file exists in the project root or meta-inf"
enabled: true
severity: HIGH
checks:
  - type: FILE_EXISTS
    params:
      filePatterns: ["META-INF/MANIFEST.MF", "*.module"]
```

### 🐳 DevOps / Docker Patterns

**Scenario**: For projects intended to run in containers, the presence of a `Dockerfile` is non-negotiable.

```yaml
id: "DOCKER-01"
name: "Require Dockerfile"
description: "Ensure Dockerfile exists for container builds"
enabled: true
severity: MEDIUM
checks:
  - type: FILE_EXISTS
    params:
      filePatterns: ["Dockerfile", "docker-compose.yml"]
```
