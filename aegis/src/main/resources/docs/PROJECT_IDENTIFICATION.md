# Project Identification

Aegis identifies projects to scan based on **Marker Files**. It does not semantically "auto-detect" technology types like Java or Python unless told what files signify the root of such a project.

## Overview

When Aegis scans a directory, it looks for specific "markers" to decide if a folder should be treated as a project and subjected to the validation rules. This is controlled in the `config` section of your `rules.yaml`.

## Configuration

The `projectIdentification` block in `rules.yaml` defines these settings:

```yaml
config:
  projectIdentification:
    targetProject:
      matchMode: ANY      # ANY or ALL
      markerFiles:        # Files that identify a project
      - pom.xml           # Signifies a Maven/Java project
      - package.json       # Signifies a Node.js project
      - requirements.txt  # Signifies a Python project
      - mule-artifact.json # Signifies a MuleSoft project
    configFolder:
      namePattern: .*_config.* # Pattern for identifying configuration projects
```

### Match Modes

- **ANY**: A folder is considered a project if **AT LEAST ONE** of the marker files exists.
- **ALL**: A folder is considered a project ONLY if **EVERY** marker file in the list exists.

## Managing Multiple Technologies

Because Aegis is technology-agnostic, you can scan a mixed repository (e.g., containing both Java and Python services) by simply including markers for both:

1. **Shared Scan**: Add both `pom.xml` and `requirements.txt` to `markerFiles`.
2. **Targeted Rules**: Use `filePatterns` in your rules to ensure Java rules only run on Java files (`**/*.java`) and Python rules only on Python files (`**/*.py`).

## Best Practices

- **Narrow the Markers**: Only use files that truly represent the root of a project. Using a very common file like `README.md` as a marker might cause Aegis to scan many folders that aren't actually code projects.
- **Excluded Folders**: Use the `ignoredFolders` configuration to skip directories like `node_modules`, `target`, or `.git` to speed up the discovery process.
