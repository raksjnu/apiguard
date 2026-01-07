# GitAnalyzer - Comparison & Migration Tool

GitAnalyzer is a standalone Java application designed to analyze, compare, and report on Git repositories. It helps in migration projects by highlighting code vs. configuration changes and providing advanced filtering capabilities.

## Features
- **Bulk Download**: Clone multiple repositories from GitLab/GitHub.
- **Migration Analysis**: Compare source and target branches.
- **Smart XML Comparison**: Ignore attribute reordering in XML files to reduce noise.
- **Search**: Find tokens locally or continuously across remote repositories.
- **Reporting**: Detailed HTML reports.

## Installation & Setup
1.  **Prerequisites**: Java 11+ installed.
2.  **Configuration**:
    -   Copy `gitanalyzer.properties.template` to `gitanalyzer.properties`.
    -   Set your GitLab/GitHub credentials (`gitlab.url`, `gitlab.token`, etc.).
3.  **Run**:
    -   Windows: `start.bat`
    -   Mac/Linux: `./start.sh`
    -   Browser opens automatically at `http://localhost:8080`.

## Analysis Guide
1.  **Select Repositories**: Choose your Code and Config repositories.
2.  **Select Branches**: Use "Fetch" to load branches. Use "Swap" to reverse comparisons.
3.  **Ignore Patterns**:
    -   **File Patterns**: Glob style (e.g. `*.md`, `test/`).
    -   **Content Patterns**: 
        -   **Plain Text**: Matches if the line contains the text (e.g. `DEBUG_LOG`).
        -   **Regex**: Matches the *entire line*. Use wildcards for partial match.
            -   Example: `regex:.*TODO.*` (Matches any line containing TODO).
            -   Example: `regex:^//.*` (Matches lines starting with //).
4.  **Smart XML Compare**:
    -   Enable **"Smart XML Compare (Ignore Attribute Order)"** checkbox.
    -   When enabled, XML files are analyzed semantically. If a line change only involves reordered attributes (e.g. `<tag a="1" b="2">` vs `<tag b="2" a="1">`), it is **IGNORED** (marked as IG).

## Troubleshooting
-   **Port Conflict**: If 8080 is busy, change `http.port` in `gitanalyzer.properties`.
-   **Credentials**: Ensure your Token has specific read permissions.
