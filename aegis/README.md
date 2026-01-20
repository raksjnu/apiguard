# Aegis - Universal Static Analysis Tool



Aegis is a powerful, extensible static analysis tool designed to enforce coding standards, security best practices, and operational readiness across diverse technology stacks. While it offers deep native support for MuleSoft, its rule engine is technology-agnostic, supporting validation for:

* Java / Spring Boot
* Tibco
* Python
* MuleSoft
* Generic XML/JSON/Properties

The tool scans project files to validate:

* Configuration consistency (XML, YAML, Properties)
* Coding patterns and prohibited tokens
* Project structure and existence of required files
* Security vulnerabilities in dependencies or configurations

It generates detailed HTML and Excel reports highlighting compliance and identifying any violations.

* **Universal Trigger Engine**: Any check type (XML, POM, JSON, Properties) can serve as a conditional trigger.
* **Rich Standards Knowledge Base**: Rules now support detailed Use Cases, Rationales, and visual Good/Bad examples.
* **Alphanumeric Rule IDs**: Enhanced audit-ready IDs (e.g., `RULE-SEC-001`) for clear organizational alignment.
* **Comprehensive Rule Set**: 25+ built-in architectural standards with complex cross-file validation.
* **Multiple Input Modes**:
    * **Local Folder**: Scan projects from your filesystem
    * **ZIP Upload**: Upload and validate ZIP archives
    * **JAR Upload**: Validate application JAR/Archive files
    * **Git Repository**: Connect to GitLab/GitHub. Supports Public (no token) and Private (token) repositories.
* **Smart Git Discovery**: Browse repositories by Group/User, or clone directly via URL.
* **Multi-Project Analysis**: Scan a single API or a directory containing multiple APIs.
* **Consolidated Reporting**: Generates a summary dashboard for all scanned APIs.
* **Detailed Individual Reports**: Provides rule-by-rule results for each API with deep context and examples.
* **Checklist View**: Includes an interactive `rule_guide.html` that acts as a searchable standards encyclopedia.
* **Enterprise Ready**: No trial restrictions, professional SLF4J logging, cross-platform compatible.

## Configuration File Requirements

**Important**: If your organization uses environment-specific configuration files, they must follow this structure:

- **Folder Name**: Must contain `_config` in the name (e.g., `muleapp_config`, `api_config`, `myproject_config`)
- **File Extensions**: `.properties`, `.policy`, or `.deployment`
- **File Format**: Properties must use `propertyName=propertyValue` format
  - Delimiter must be `=` (equals sign)
  - Example: `database.host=localhost`
  - Example: `api.timeout=30000`

**Example Structure**:
```
your-mule-projects/
├── api-project-1/
├── api-project-2/
└── muleapp_config/
    ├── dev.properties
    ├── qa.properties
    └── prod.properties
```

**Note**: The folder name must contain `_config` somewhere in the name. The default pattern in `rules.yaml` is `".*_config.*"` which matches any folder containing `_config`. You can modify this pattern if your organization uses a different naming convention.

## Project Type Filtering

Aegis now supports intelligent **Project Type Filtering** to eliminate noise in reports. By default, the system recognizes two project types:

1.  **CODE**: Application source code (e.g., Mule, Spring Boot).
    -   *Detection*: Presence of `pom.xml`, `mule-artifact.json`, or `build.gradle`.
    -   *Rules*: Runs code quality, security, and dependency checks.
2.  **CONFIG**: Configuration and policy projects.
    -   *Detection*: Folder name matches `*_config` pattern (e.g., `muleapp_config`).
    -   *Rules*: Runs property validation, policy checks, and environment consistency rules.

**How it works**:
- Rules in `rules.yaml` can specify `appliesTo: ["CODE"]` or `appliesTo: ["CONFIG"]`.
- When scanning, Aegis classifies each project and ONLY runs the applicable rules.
- Rules without an `appliesTo` field are considered "Universal" and run on ALL projects.

## Prerequisites

To build and run Aegis, you will need the following installed:

- **Java 17**: The project is built using Java 17.
- **Apache Maven**: Required for compiling the source code and packaging the application.

## How to Build

1.  **Clone the repository** (if you haven't already):
    ```sh
    git clone <your-repository-url>
    cd aegis
    ```

2.  **Build the project using Maven**:
    This command will compile the code, run tests, and package the application into a single, executable "fat JAR" in the `target/` directory.

    ```sh
    mvn clean package
    ```

    The resulting JAR file will be named `aegis-1.0.0-jar-with-raks.jar`.

## How to Use

Aegis is a command-line tool. You can run it by executing the JAR file built in the previous step.

The tool can scan a single project or a directory containing multiple projects of any supported technology.

### Running the CLI
```sh
# Usage: java -jar <jar-file> -p <path-to-projects> [--config <rules-yaml>]
java -jar target/aegis-1.0.0-jar-with-raks.jar -p /path/to/your/projects 
```
Example: `java -jar .\target\aegis-1.0.0-jar-with-raks.jar -p C:\projects\my-java-app --config my-rules.yaml`

### Running the GUI
Aegis now supports a direct GUI mode via the CLI:
```sh
# Start GUI on default port 8080
java -jar target/aegis-1.0.0-jar-with-raks.jar --gui

# Start GUI on custom port
java -jar target/aegis-1.0.0-jar-with-raks.jar --gui --port 9000
```

### Using Launch Scripts (Windows/Mac)
For ease of use, utilize the provided launch scripts which automatically detect the latest JAR:
- **Windows**: `start-aegis-gui.bat`
- **Mac/Linux**: `./start-aegis-gui.sh`

This will generate:
1.  **A Consolidated Report**: `CONSOLIDATED-REPORT.html` and `CONSOLIDATED-REPORT.xlsx` in the root of the output directory.
2.  **Individual Reports**: A folder for each scanned project containing its specific `report.html` and `report.xlsx`.

> **Note**: Aegis automatically detects project types based on configuration, but you can also provide custom rule sets to target specific technologies using the `--config` flag.



## Rule Types Documentation

Aegis supports 18 different rule types for comprehensive validation:

### Code Rules (12 types)

Validate application code files (XML, JSON, POM, Scripts):

| Rule Type | Description | Documentation |
|-----------|-------------|---------------|
| `GENERIC_TOKEN_SEARCH_REQUIRED` | Ensure required tokens exist in files | See Rule Guide in Report |
| `GENERIC_TOKEN_SEARCH_FORBIDDEN` | Prevent forbidden tokens in files | See Rule Guide in Report |
| `XML_XPATH_EXISTS` | Validate required XPath expressions match | See Rule Guide in Report |
| `XML_XPATH_NOT_EXISTS` | Ensure forbidden XPath expressions don't match | See Rule Guide in Report |
| `XML_ATTRIBUTE_EXISTS` | Validate required XML attributes | See Rule Guide in Report |
| `XML_ATTRIBUTE_NOT_EXISTS` | Prevent forbidden XML attributes | See Rule Guide in Report |
| `XML_ELEMENT_CONTENT_REQUIRED` | Ensure XML elements contain required content | See Rule Guide in Report |
| `XML_ELEMENT_CONTENT_FORBIDDEN` | Prevent forbidden content in XML elements | See Rule Guide in Report |
| `POM_VALIDATION_REQUIRED` | Validate required Maven POM elements | See Rule Guide in Report |
| `POM_VALIDATION_FORBIDDEN` | Prevent forbidden Maven POM elements | See Rule Guide in Report |
| `JSON_VALIDATION_REQUIRED` | Ensure required JSON elements exist | See Rule Guide in Report |
| `JSON_VALIDATION_FORBIDDEN` | Prevent forbidden JSON elements | See Rule Guide in Report |

### Config Rules (6 types)

Validate environment-specific configuration files:

| Rule Type | Description | Documentation |
|-----------|-------------|---------------|
| `MANDATORY_SUBSTRING_CHECK` | Required/forbidden tokens in environment files | See Rule Guide in Report |
| `MANDATORY_PROPERTY_VALUE_CHECK` | Validate required property name-value pairs | See Rule Guide in Report |
| `OPTIONAL_PROPERTY_VALUE_CHECK` | Validate optional property values when present | See Rule Guide in Report |
| `GENERIC_TOKEN_SEARCH` | Advanced token search with environment filtering | See Rule Guide in Report |
| `GENERIC_PROPERTY_FILE_CHECK` | Generic property file validation | See Rule Guide in Report |

### Configuration Guide

All rules are configured in `src/main/resources/rules/rules.yaml`. Each rule type documentation includes:
- **Parameter reference** - All required and optional parameters
- **Configuration examples** - Real-world usage scenarios
- **Error message format** - What to expect when validation fails
- **Related rule types** - Alternative or complementary rules

## Report Output

After running the tool, the output directory will contain:

```
aegis-reports/
├── CONSOLIDATED-REPORT.html      # Dashboard for all APIs
├── CONSOLIDATED-REPORT.xlsx      # Excel summary
├── checklist.html                # All validation rules reference
├── api-name-1/
│   ├── report.html              # Individual API report
│   └── report.xlsx              # Individual API Excel
└── api-name-2/
    ├── report.html
    └── report.xlsx
```


## License & Attribution

Aegis is distributed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

### Third-Party Dependencies

This software includes the following open-source components:

- **DOM4J** - XML parsing library  
  Copyright © 2001-2016 MetaStuff, Ltd. and DOM4J contributors  
  Licensed under the BSD-3-Clause License  
  https://dom4j.github.io/

- **Apache POI**, **Apache Commons IO**, **Apache Commons Text**, **Apache Maven Model**, **Log4j**  
  Licensed under the Apache License 2.0  
  https://www.apache.org/licenses/LICENSE-2.0

- **SnakeYAML**, **Jackson**, **Picocli**, **Jaxen**  
  Licensed under the Apache License 2.0  
  https://www.apache.org/licenses/LICENSE-2.0

For a complete list of dependencies and their versions, see `pom.xml`.

### Security

### Security

For security vulnerabilities or concerns, please contact the author below.

**Last Security Audit**: January 2026
**Status**: All dependencies updated to secure versions.

---
**For any further inquiries, reach out to:**

- **Author**: Rakesh Kumar
- **Email**: Rakesh.Kumar@ibm.com
- **Role**: Application Architect
---


