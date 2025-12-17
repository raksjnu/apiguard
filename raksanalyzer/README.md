# RaksAnalyzer

Universal Document Generation Framework for analyzing and documenting Mule, Tibco, and other technology projects.

## Prerequisites
- Java 17 or higher
- Maven 3.8+ (for building)

## Quick Start

### Double-Click to Run
Simply double-click `raksanalyzer-1.0.0.jar` to start the application. It will automatically open your browser.
**Note**: Ensure your system default Java is version 17.

### Command Line
```bash
# Run with defaults
java -jar raksanalyzer-1.0.0.jar

# Run with custom configuration
java -jar raksanalyzer-1.0.0.jar -config custom.properties

# Analyze a Mule project
java -jar raksanalyzer-1.0.0.jar -type MULE -input /path/to/project
```

## Features

- ✅ Analyze Mule 4.x projects
- ✅ Analyze Tibco BusinessWorks 5.x/6.x projects
- ✅ Generate Excel analysis reports
- ✅ Generate Word design documents
- ✅ Multi-environment property analysis
- ✅ Email delivery
- ✅ Git repository support

## Build

```bash
mvn clean package
```

## License

Apache License 2.0
