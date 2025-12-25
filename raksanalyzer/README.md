# RaksAnalyzer

**Universal Document Generation Framework for analyzing and documenting Mule, Tibco, and other technology projects.**

RaksAnalyzer is a powerful tool designed to reverse-engineer and document middleware applications. It parses project files (XML, properties, configuration) and generates comprehensive design documents (Word), analysis reports (Excel), and flow diagrams (PDF).

## 🚀 Recent Features (v1.0.0)
- **Tibco BW 5.x/6.x Support**: Full analysis of Tibco projects, including process diagrams and resource inventory.
- **Excel RawExtract**: Detailed "RawContent" tab in Excel report showing full file content for all project files.
- **Path Normalization**: Automatically fixes path issues (e.g., duplicates `folder/folder/file` or relative paths `./output`) across all generated documents and UI.
- **Property Resolution**: Configurable property resolution for Tibco global variables `%%var%%`.
- **Improved UI**: Cleaner interface with direct file opening capabilities.

## 📋 Prerequisites
- **Java 17** or higher (Required)
- Maven 3.8+ (for building from source)

## ⚡ Quick Start

### GUI Mode (Recommended)
1. Double-click `start-raksanalyzer.bat` or `raksanalyzer-1.0.0.jar`.
2. The application will launch in your default browser at `http://localhost:8080`.
3. Use the UI to select your project folder and generate documents.

### Command Line Mode
```bash
# Run with default configuration
java -jar raksanalyzer-1.0.0.jar

# Run with custom configuration file
java -jar raksanalyzer-1.0.0.jar -config custom.properties

# Analyze a specific project directly
java -jar raksanalyzer-1.0.0.jar -type TIBCO -input C:/projects/MyTibcoApp -mode FULL
```

## 🛠️ Configuration
The application is highly configurable via `framework.properties`.
Key configurations include:

### Core Settings
- `project.technology.type`: Set to `MULE` or `TIBCO`.
- `document.generation.execution.mode`: `FULL`, `GENERATE_ONLY`, or `ANALYZE_ONLY`.
- `framework.output.directory`: Path to save generated documents.

### Tibco Specifics
- `tibco.property.resolution.enabled`: Set to `true` to resolve `%%GlobalVariables%%` in documents.
- `word.tibco.section.*`: Enable/disable specific sections in the Word document.

### Output Control
- `framework.output.pdf.enabled`: Generate PDF with diagrams (true/false).
- `framework.output.word.enabled`: Generate detailed Word design document (true/false).
- `framework.output.excel.enabled`: Generate Excel analysis spreadsheet (true/false).

## 📦 Build Instructions
To build the project from source:
```bash
mvn clean package
```
This will produce `raksanalyzer-1.0.0.jar` in the `target` directory.

## 📄 License
Apache License 2.0

