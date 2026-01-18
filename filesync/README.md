# FileSync Tool

Configuration-driven CSV transformation tool with GUI and CLI support.

## Features

- **Global Zoom**: **[NEW]** Use `Ctrl` + `+`/`-`/`0` to scale UI text size across the entire application.
- **Taskbar Icon**: **[NEW]** Custom RAKS brand icon for the application taskbar (no more default Java cup).
- **Audit Summaries**: **[NEW]** Detailed row counts for source and target files in execution logs.
- **Recursive Scan**: **[NEW]** Automatically finds CSV files in nested subdirectories (e.g., `Input/20260113_1453/`).
- **Discovery Picker**: **[NEW]** Directly pick your **Data Folder** and **Mapping File** in the Discovery tab after scanning.
- **Resizable Panels**: **[NEW]** Resizable UI sections in Mapping AND Execute tabs via split panes.
- **Persistent Logic**: **[NEW]** Logs are preserved across the session; output folder names are locked during validation.
- **Improved Aesthetics**: **[NEW]** Truly rounded corners, ApiGuard purple headings, and larger 14pt table fonts.

## Quick Start

### GUI Mode

```bash
java -jar target/filesync-1.0.0.jar
```

## GUI Workflow

### 1. Discovery Tab (Initial Scan)
1. **Source Directory**: Select your root folder (containing `Input/` and `Mapping_*.csv` files).
2. **Scan**: Click `Scan Directory` to recursively find all files.
3. **Data Selection**: Use the **Configuration Selection** section to pick:
   - **Data Folder**: Choose the specific dated input folder (default is root if no subfolders exist).
   - **Mapping File**: Select the desired CSV mapping configuration.
4. **Verified Schema**: Review the discovered fields in the tree view before proceeding.

### 2. Mapping Tab (Validation)
1. **Editable Table**: Modify mappings directly in the table. Use the larger fonts for better experience.
2. **Validation**: Click `✓ Validate Mapping & Files` to see detailed statistics:
   - Required source files and target files to be created.
   - Total field mappings and absolute data paths.
3. **Save**: Click `💾 Save Mapping` to persist your changes back to the CSV.

### 3. Execute Tab (Transformation)
1. **Auto-Load**: The configuration from the Discovery/Mapping tabs is automatically passed here.
2. **Execute**: Click `▶ Execute Transformation`.
3. **Detailed Summary**: Review the final log showing created files, their sizes, and the timestamped output folder path.

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
- Displays configuration details with preview
- Shows source/target directories and file count
- Ready to execute immediately

No need to manually save and load configuration files during your workflow!

## Smart Autocomplete

The Mapping tab features intelligent autocomplete for faster data entry:

### Target File Names
- Start typing in the "Target File" field
- Dropdown shows previously used target file names
- Select from history or type new name
- History persists across sessions

### Target Field Names
- Start typing in the "Target Field" field
- Dropdown shows previously used field names
- Quick selection from common field names
- Automatically saves new entries

**Storage**: History limited to 10 most recent entries per category (~1-2 KB total)

**Benefits**:
- Faster mapping creation
- Consistent naming across mappings
- Reduced typos
- Better productivity

## Heap Memory Configuration

For processing large CSV files, you can increase the Java heap memory:

### Standard Launch
```bash
java -jar target/filesync-1.0.0.jar
```

### With Increased Heap Memory
```bash
# 2GB heap (for medium files: 1,000-10,000 rows)
java -Xmx2g -jar target/filesync-1.0.0.jar

# 4GB heap (for large files: 10,000-100,000 rows)
java -Xmx4g -jar target/filesync-1.0.0.jar

# Set both min and max (recommended for consistent performance)
java -Xms1g -Xmx4g -jar target/filesync-1.0.0.jar

# 8GB for very large datasets (100,000+ rows)
java -Xms2g -Xmx8g -jar target/filesync-1.0.0.jar
```

**Guidelines**:
- Default (256MB-512MB): Small files (<1,000 rows)
- 2GB: Medium files (1,000-10,000 rows)
- 4GB: Large files (10,000-100,000 rows)
- 8GB+: Very large files (100,000+ rows)

**Note**: `-Xms` sets minimum heap, `-Xmx` sets maximum heap. Setting both to similar values can improve performance.

## Future Enhancements

- Rule-based conditional mappings
- Multi-field transformations
- Cross-file lookups
- Formula support
- Export execution logs

## License

© 2018 RAKS ApiGuard FileSync - Enterprise API Solution Suite

---
**For any further inquiries, reach out to:**

- **Author**: Rakesh Kumar
- **Email**: Rakesh.Kumar@ibm.com
- **Role**: Application Architect
---


