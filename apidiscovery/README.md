# ApiDiscovery Tool

A standalone tool for scanning source code repositories to identify APIs, detect technologies, and enforce governance standards. It supports both **MuleSoft** and **Spring Boot** projects, along with generic technology detection.

## Features
- **Technology Detection**: Automatically identifies Mule 3/4, Spring Boot, Tibco, Python, etc.
- **API Classification**: Distinguishes between System, Process, and Experience APIs.
- **Governance Checks**: Validates against standards (e.g., Logging, HTTPS, Versioning).
- **GitLab Integration**: Can directly clone and scan entire GitLab groups.
- **Dual Mode**: Runs as a **Web GUI** for interactive use or **CLI** for automation.

## Project Structure
- `src/main/java`: Source code
- `src/main/resources/web`: Web UI assets (HTML, CSS, JS)
- `rules-config.json`: Configurable rules for detection and scoring.

## Build Instructions
To build the project usage Maven:
```bash
mvn clean package
```
This produces `target/apidiscovery-1.0.0.jar`.

## Usage Guide

### 1. Web GUI Mode (Default)
Starts a local web server with an interactive dashboard.

**Command:**
```bash
java -jar apidiscovery-1.0.0.jar
```
**Access:** Open `http://localhost:8085` in your browser.

---

### 2. CLI Mode (Automation)
Runs a scan directly from the command line without starting the web server. Ideal for CI/CD pipelines.

**Supported Arguments:**
| Flag | Description | Required |
|------|-------------|----------|
| `-source` | Path to local directory OR GitLab Group URL | **Yes** |
| `-token` | GitLab Personal Access Token (required for URL scan) | No (if local) |
| `-output` | Path to save JSON results (default: `scan_results.json`) | No |

**Examples:**

*Scan a local directory:*
```bash
java -jar apidiscovery-1.0.0.jar -source "C:\Workspaces\my-projects"
```

*Scan a GitLab group:*
```bash
java -jar apidiscovery-1.0.0.jar -source "https://gitlab.com/my-org/my-group" -token "glpat-xxxxxxxx" -output "report.json"
```

## Configuration
The tool uses `config.properties` (embedded or external) for defaults:
- `server.port`: Web server port (default 8085)
- `gitlab.token`: Default GitLab token

---
**For any further inquiries, reach out to:**

- **Author**: Rakesh Kumar
- **Email**: Rakesh.Kumar@ibm.com
- **Role**: Application Architect
---

