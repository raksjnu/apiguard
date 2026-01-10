# FileSync Tool

Configuration-driven CSV transformation tool with GUI and CLI support.

## Features

- **Dynamic CSV Discovery**: Automatically scan directories and detect CSV file schemas
- **Visual Mapping Builder**: GUI to create field mappings between source and target files
- **Configuration-Based**: Save and reuse mappings as JSON configuration files
- **Session Persistence**: Remembers your last used directories and configuration files
- **Auto-Load Configuration**: Automatically loads mappings when switching to Execute tab
- **Dual Interface**: Both GUI and CLI modes for different workflows
- **ApiGuard Branding**: Professional purple theme with RAKS logo
- **Comprehensive Help**: Built-in documentation with 5 help sections
- **Extensible**: Ready for future rule-based conditional mappings

## Quick Start

### GUI Mode

```bash
java -jar target/filesync-1.0.0.jar
```

Or:

```bash
java -jar target/filesync-1.0.0.jar gui
```

### CLI Mode

**Discover source files:**
```bash
java -jar target/filesync-1.0.0.jar -m discover -s C:\path\to\source
```

**Execute transformation:**
```bash
java -jar target/filesync-1.0.0.jar -m execute -c C:\path\to\config.json
```

**Validate configuration:**
```bash
java -jar target/filesync-1.0.0.jar -m validate -c C:\path\to\config.json
```

## Building

```bash
mvn clean package
```

This creates an executable JAR: `target/filesync-1.0.0.jar`

## Configuration Format

See `config/filesync-config.json` for a sample configuration.

```json
{
  "version": "1.0",
  "paths": {
    "sourceDirectory": "C:/path/to/source",
    "targetDirectory": "C:/path/to/target"
  },
  "fileMappings": [
    {
      "sourceFile": "source.csv",
      "targetFile": "target.csv",
      "fieldMappings": [
        {
          "sourceField": "field1",
          "targetField": "field2",
          "transformation": "direct"
        }
      ]
    }
  ]
}
```

## GUI Workflow

### First Time Use
1. **Discovery Tab**: Select source directory and scan for CSV files
2. **Mapping Tab**: Create field mappings visually and save configuration
3. **Execute Tab**: Configuration auto-loads! Click Execute to run transformation

### Subsequent Use
The tool remembers your last session:
- Source directory is pre-filled
- Target directory is pre-filled
- Last config file location is remembered
- Just scan, map, and execute!

## Session Persistence

The tool automatically remembers:
- **Last source directory** - Auto-fills in Discovery tab
- **Last target directory** - Auto-fills in Mapping tab
- **Last config file** - File choosers start in the right location
- **Window size and position** - Opens exactly where you left it

Settings persist across sessions using Java Preferences API.

## Auto-Load Feature

When you switch to the Execute tab, the tool automatically:
- Loads the current mappings from the Mapping tab
- Displays configuration details
- Shows "[From Mapping Tab]" as the source
- Ready to execute immediately

No need to manually save and load configuration files during your workflow!

## Future Enhancements

- Rule-based conditional mappings
- Multi-field transformations
- Cross-file lookups
- Formula support
- Export execution logs

## License

© 2018 RAKS ApiGuard FileSync - Enterprise API Solution Suite

