# MuleGuard - MuleSoft Static Analysis Tool
# raks
# By - Rakesh Kumar

MuleGuard is a static analysis tool for MuleSoft applications. It validates Mule projects against a defined set of rules to enforce coding standards, security best practices, and migration readiness.

The tool scans various project files, including:
- Maven configurations (`pom.xml`)
- Mule runtime manifests (`mule-artifact.json`)
- XML configurations
- DataWeave scripts
- Environment-specific property files (`*.yaml`, `*.properties`)

It generates detailed HTML and Excel reports highlighting compliance and identifying any violations.

## Features

- **Multiple Input Modes**: 
  - **Local Folder**: Scan projects from your filesystem
  - **ZIP Upload**: Upload and validate ZIP archives
  - **JAR Upload**: Validate Mule application JAR files
  - **Git Repository**: (Coming Soon) Clone and validate from Git
- **Comprehensive Rule Set**: Validates against a wide range of best practices.
- **Multi-Project Analysis**: Scan a single API or a directory containing multiple APIs.
- **Consolidated Reporting**: Generates a summary dashboard for all scanned APIs.
- **Detailed Individual Reports**: Provides rule-by-rule results for each API, with links from the consolidated report.
- **Multiple Formats**: Reports are generated in both user-friendly HTML and easy-to-process Excel formats.
- **Checklist View**: Includes a `checklist.html` page that lists all possible validation rules the tool checks for.
- **Enterprise Ready**: No trial restrictions, professional SLF4J logging, cross-platform compatible.

## Configuration File Requirements

**Important**: If your organization uses environment-specific configuration files, they must follow this structure:

- **Folder Name**: `muleapp_config` (at the same level as your API project folders)
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

**Note**: If your organization uses a different folder name or structure, you can either rename your folder to `muleapp_config` or modify the `rules.yaml` configuration to match your structure. Configuration validation rules (RULE-3001 through RULE-3999) only apply to files in the `muleapp_config` folder.

## Prerequisites

To build and run MuleGuard, you will need the following installed:

- **Java 17**: The project is built using Java 17.
- **Apache Maven**: Required for compiling the source code and packaging the application.

## How to Build

1.  **Clone the repository** (if you haven't already):
    ```sh
    git clone <your-repository-url>
    cd muleguard
    ```

2.  **Build the project using Maven**:
    This command will compile the code, run tests, and package the application into a single, executable "fat JAR" in the `target/` directory.

    ```sh
    mvn clean package
    ```

    The resulting JAR file will be named `muleguard-1.0.0-jar-with-raks.jar`.

## How to Use

MuleGuard is a command-line tool. You can run it by executing the JAR file you built in the previous step.

The tool can scan a single Mule project or a directory containing multiple projects.

### Scanning a Single Project

Use the following command to scan a single MuleSoft API project. You need to provide the path to the project directory and specify an output directory for the reports.

```sh
# Usage: java -jar <jar-file> <path-to-mule-project> <output-directory>
java -jar target/muleguard-1.0.0-jar-with-raks.jar -p /path/to/your/mule-api
```

> **IMPORTANT:** Ensure you are running with **Java 17**. If you see `UnsupportedClassVersionError`, your default `java` is likely older. Set your `JAVA_HOME` to JDK 17 before running:
> **Windows:** `set JAVA_HOME=C:\Path\To\Jdk17`
> **Mac/Linux:** `export JAVA_HOME=/path/to/jdk17`
> Then run the start script.

example: java -jar .\target\muleguard-1.0.0-jar-with-raks.jar -p C:\Users\raksj\Documents\raks\tmp\t2mtemp\MigrationOutput\Tibco2MuleCode

This will generate an individual report for the API in `report.html` and `report.xlsx` inside the specified output directory.

### Scanning Multiple Projects

To scan multiple projects at once, provide the path to a directory that contains all the MuleSoft API project folders.

```sh
# Usage: java -jar <jar-file> <path-to-directory-of-mule-projects> <output-directory>
java -jar target/muleguard-1.0.0-jar-with-raks.jar -p /path/to/your/apis 
```
example: java -jar .\target\muleguard-1.0.0-jar-with-raks.jar -p C:\Users\raksj\Documents\raks\tmp\t2mtemp\MigrationOutput\Tibco2MuleCode

This will generate:
1.  **A Consolidated Report**: `CONSOLIDATED-REPORT.html` and `CONSOLIDATED-REPORT.xlsx` in the root of the output directory. This report provides a summary of all scanned APIs.
2.  **Individual Reports**: A sub-directory for each API, containing its specific `report.html` and `report.xlsx`.
3.  **Checklist**: A `checklist.html` file listing all rules.


## Rule Types Documentation

MuleGuard supports 18 different rule types for comprehensive validation:

### Code Rules (12 types)

Validate Mule application code files (XML, JSON, POM, DataWeave):

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
| `CLIENTIDMAP_VALIDATOR` | Validate client ID mappings and secure properties | See Rule Guide in Report |
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
muleguard-reports/
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

MuleGuard is distributed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

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

For security vulnerabilities or concerns, please contact: **raksjnu@gmail.com**

**Last Security Audit**: December 2025  
**Status**: All dependencies updated to secure versions (see `SECURITY_COMPLIANCE_REPORT.md`)
