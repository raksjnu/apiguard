# GitAnalyzer Requirements

## 1. Core Functionality
- **Bulk Download**: Capability to download/clone multiple repositories for automated analysis.
- **Migration Analysis**: Compare source and target branches to generate migration impact reports.
  - **Ignore Logic**: Capability to configure and ignore specific lines or patterns (e.g., timestamps, auto-generated IDs) during the comparison to reduce noise.
- **Git Search**:
  - **Local Search**: Search for specific tokens/patterns within a local directory path.
  - **Remote Search**: Search for tokens within remote GitLab/GitHub repositories via API.
- **Reporting**: Generate detailed HTML reports highlighting code vs. config changes.

## 2. Architecture
- **Type**: Standalone Web Application.
- **Tech Stack**: Java, Embedded Jetty Server (v11), Jersey (JAX-RS).
- **Constraints**:
  - **No Spring Boot**: Must use lightweight Jetty/Jersey to ensure compatibility with MuleSoft wrappers.
  - **Standalone JAR**: Must operate as a single executable JAR file.
- **Browser Integration**: Application must automatically launch the default web browser upon startup.

## 3. Platform & Deployment
- **Cross-Platform Support**: Must run seamlessly on Windows, macOS, and Linux.
  - **Path Handling**: No hardcoded paths or OS-specific separators (`\` vs `/`). Use Java's `File.separator` or `Path` API.
- **CloudHub Compatibility**:
  - **Temp Directory**: All temporary files must be written to `{app.home}/temp`.
  - **Resources**: No dependency on the local file system for static assets. All resources (HTML/CSS) must be loaded from the JAR's `src/main/resources`.
- **Configuration**:
  - **Property File**: configuration via `gitanalyzer.properties`.
  - **Port**: Configurable HTTP port (e.g., `http.port=6060`). Default to 8080 if not specified.
  - **Credentials**: Git provider credentials (URL, Token, Group/Owner) managed in properties.

## 4. UI / UX Design
- **Look & Feel**: Must match existing RAKS tools (`raksanalyzer`, `muleguard`).
- **Branding**:
  - **Logo**: Use the standard RAKS `logo.png`.
  - **Colors**: Primary color is **RAKS Purple** (`#663399`). Use gradients or clean solids.
  - **Typography**: Font family must be **Segoe UI**.
- **Components**:
  - **Dashboard**: Card-based layout for major features (Bulk Download, Analysis, Search) with hover effects.
  - **Buttons**:
    - Primary: Purple background, white text.
    - Secondary/Nav: "Ghost Button" style (transparent with white border).
  - **Visibility**: Ensure all text is legible (high contrast, appropriate font sizes).
