# RaksAnalyzer

**Universal Document Generation Framework for analyzing and documenting Mule, Tibco, and other technology projects.**

RaksAnalyzer is a powerful tool designed to reverse-engineer and document middleware applications. It parses project files (XML, properties, configuration) and generates comprehensive design documents (Word), analysis reports (Excel), and flow diagrams (PDF).

## 🚀 Features

- **Multi-Technology Support**: Mule, Tibco BW 5.x/6.x, Spring Boot (coming soon)
- **Comprehensive Documentation**: Generates Word, PDF, and Excel documents
- **Flow Diagrams**: Automatic generation of integration flow diagrams
- **Property Resolution**: Configurable resolution for Tibco global variables `%%var%%`
- **Dual Mode**: GUI for interactive use, CLI for automation
- **Flexible Input**: Supports local folders, ZIP files, EAR/JAR archives, and Git repositories

## 📋 Prerequisites

- **Java 17** or higher (Required)
- Maven 3.8+ (for building from source)

## ⚡ Quick Start

### GUI Mode (Default)

**Windows:**
```bash
start.bat
```

**Linux/Mac:**
```bash
./start.sh
```

The application will launch in your default browser at `http://localhost:8080`.

### CLI Mode

For automation, CI/CD pipelines, or batch processing:

```bash
# Basic usage
java -jar raksanalyzer-1.0.0.jar --cli --type tibco5 --input /path/to/project

# With custom configuration
java -jar raksanalyzer-1.0.0.jar --cli --config custom.properties --type tibco5 --input project.zip

# With custom output directory
java -jar raksanalyzer-1.0.0.jar --cli --type tibco5 --input project.zip --output /custom/output

# Show help
java -jar raksanalyzer-1.0.0.jar --help
```

## 📖 CLI Usage

### Command Syntax

```
java -jar raksanalyzer.jar [OPTIONS]
```

### Modes

| Mode | Description |
|------|-------------|
| **(no args)** | Start UI mode (default) - launches web interface |
| `--cli` | Run in CLI mode - analyze and generate without UI |

### Options

| Option | Description | Required | Default |
|--------|-------------|----------|---------|
| `--config <path>` | Path to external properties file for property resolution (Mule/Tibco) | No | Project properties |
| `--type <type>` | Project type: `mule`, `tibco5`, `spring` | Yes (CLI mode) | - |
| `--input <path>` | Input project path | Yes (CLI mode) | - |
| `--input-type <type>` | Input source type: `folder`, `zip`, `ear`, `jar`, `git` | No | Auto-detected |
| `--output <path>` | Output directory for generated documents | No | `./output` |
| `--output-type <types>` | Output formats: `pdf`, `word`, `excel`, or comma-separated | No | All formats |
| `--port <number>` | Server port (UI mode only) | No | `8080` |
| `--no-browser` | Don't auto-open browser (UI mode only) | No | Opens browser |
| `--help`, `-h` | Show help message | No | - |

### External Configuration File

The `--config` option allows you to specify an external properties file for property resolution in Mule and Tibco projects:

**For Mule Projects:**
- Provide a `.properties` file (e.g., `mule-app.properties`, `dev.properties`)
- All property placeholders (`${property.name}`) in connector configurations will be resolved using this file
- Useful for generating documentation with environment-specific values

**For Tibco Projects:**
- Provide a `.substvar` or `.properties` file
- Property resolution works similarly to Mule projects

**Example:**
```bash
# Use custom properties file for Mule project
java -jar raksanalyzer.jar --cli --type mule --input /path/to/project --config /path/to/prod.properties

# Use custom substvar file for Tibco project
java -jar raksanalyzer.jar --cli --type tibco5 --input /path/to/project --config /path/to/custom.substvar
```

### Input Source Types

The `--input-type` option specifies how to interpret the input path:

| Type | Description | Example |
|------|-------------|---------|
| `folder` | Local directory | `/path/to/project` |
| `zip` | ZIP archive | `project.zip` |
| `ear` | Enterprise Archive (Tibco) | `project.ear` |
| `jar` | Java Archive | `project.jar` |
| `git` | Git repository URL | `https://github.com/user/repo.git` |

**Auto-Detection**: If `--input-type` is not specified, the system will auto-detect based on the file extension or URL pattern.

### Output Format Types

The `--output-type` option controls which document formats to generate:

| Type | Description |
|------|-------------|
| `pdf` | Generate PDF document only |
| `word` | Generate Word document only |
| `excel` | Generate Excel spreadsheet only |
| `pdf,word` | Generate both PDF and Word |
| `pdf,excel` | Generate both PDF and Excel |
| `word,excel` | Generate both Word and Excel |
| *(not specified)* | Generate all formats (default) |

### Examples

#### Local Folder
```bash
java -jar raksanalyzer.jar --cli --type tibco5 --input /path/to/tibco/project
```

#### ZIP File
```bash
java -jar raksanalyzer.jar --cli --type tibco5 --input project.zip --input-type zip
```

#### EAR Archive (Tibco)
```bash
java -jar raksanalyzer.jar --cli --type tibco5 --input application.ear --input-type ear
```

#### Git Repository
```bash
java -jar raksanalyzer.jar --cli --type mule --input https://github.com/user/mule-project.git --input-type git
```

#### Generate Only PDF
```bash
java -jar raksanalyzer.jar --cli --type tibco5 --input project.zip --output-type pdf
```

#### Generate PDF and Word
```bash
java -jar raksanalyzer.jar --cli --type mule --input /path/to/project --output-type pdf,word
```

#### Generate Only Excel
```bash
java -jar raksanalyzer.jar --cli --type tibco5 --input /path/to/project --output-type excel
```

#### Mule Project with Custom Properties
```bash
# Generate documentation using production properties
java -jar raksanalyzer.jar --cli \
  --type mule \
  --input /path/to/mule-project \
  --config /path/to/prod.properties \
  --output-type pdf
```

#### With Custom Configuration
```bash
java -jar raksanalyzer.jar --cli \
  --config custom.properties \
  --type tibco5 \
  --input project.zip \
  --output /custom/output/path \
  --output-type pdf,word
```

#### UI Mode on Custom Port
```bash
java -jar raksanalyzer.jar --port 9090 --no-browser
```

## 🛠️ Configuration

The application is highly configurable via `framework.properties`.

### Core Settings

```properties
# Project type: MULE, TIBCO_BW5, SPRING_BOOT
project.technology.type=TIBCO_BW5

# Execution mode: FULL, GENERATE_ONLY, ANALYZE_ONLY
document.generation.execution.mode=FULL

# Output directory
framework.output.directory=./output
```

### Output Control

```properties
# Enable/disable document formats
framework.output.pdf.enabled=true
framework.output.word.enabled=true
framework.output.excel.enabled=true
```

### Tibco Specifics

```properties
# Resolve %%GlobalVariables%% in documents
tibco.property.resolution.enabled=true

# Enable/disable Word document sections
word.tibco.section.project.info.enabled=true
word.tibco.section.services.enabled=true
word.tibco.section.processes.enabled=true
word.tibco.section.connections.enabled=true
```

See `src/main/resources/config/defaults/framework.properties` for all available options.

## 📦 Build Instructions

### Build from Source

```bash
# Clean build
./rebuild.bat   # Windows
./rebuild.sh    # Linux/Mac

# Or use Maven directly
mvn clean package -DskipTests
```

This will produce `raksanalyzer-1.0.0.jar` in the `target` directory.

### Build and Run

```bash
# Build and start in one command
./rebuild-restart.bat   # Windows
./rebuild-restart.sh    # Linux/Mac
```

## 🔧 Development

### Project Structure

```
raksanalyzer/
├── src/main/java/          # Java source code
├── src/main/resources/     # Configuration and templates
├── testdata/               # Sample projects for testing
├── output/                 # Generated documents (gitignored)
├── rebuild.bat/sh          # Build scripts
├── start.bat/sh            # Start scripts
└── rebuild-restart.bat/sh  # Build and start scripts
```

### Scripts

| Script | Purpose |
|--------|---------|
| `rebuild.bat/sh` | Clean build the project |
| `start.bat/sh` | Start the application |
| `rebuild-restart.bat/sh` | Build and start in one command |

All scripts automatically use Java 17 if available, otherwise fall back to system `JAVA_HOME`.

## 📄 License

Apache License 2.0

## 🤝 Contributing

Contributions are welcome! Please ensure all tests pass before submitting a pull request.

## 📞 Support

For issues, questions, or feature requests, please open an issue on the project repository.
