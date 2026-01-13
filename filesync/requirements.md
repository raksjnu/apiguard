# FileSync New Features Requirements

This document captures the requirements for the new features and integration of the FileSync tool.

## 1. Folder Structure & Scanning
- **Input Structure:** The provided path should contain:
    - `Input/` folder: Contains the source CSV files.
    - `Mapping_*.csv`: Mapping configuration file(s).
- **Scanning Logic:**
    - Extract mapping files and CSV files from the `Input/` folder.
    - **Auto-selection:**
        - If one `Mapping_*.csv` is found, load it automatically.
        - If multiple are found, show dropdown to select.
    - **Manual Mapping UI Removed**: All mappings defined via CSV only.

## 2. Output Management
- **Output Location:** Automatically create an `Output/` folder within the user-provided path.
- **Dated Folders:** Within `Output/`, create a timestamped folder: `YYYYMMDD_HHmm`.
- **Pre-existing Folders:** Ensure new dated folders are created for every run, even if `Output/` or other dated folders already exist.

## 3. Discover Tab Enhancements
- **View Mode:** The user should be able to select any folder and scan it to see metadata for all nested CSV files (not just source files).
- **UI Update:** Add functionality to toggle or support this "View" type scanning.

## 4. Configurability
- **UI Priority:** Any configuration provided via the UI (rules, file names, source/target paths, output folder names) should overwrite default settings in `config.properties` for that session.

## 5. Mapping CSV Layout & Relationships
The `Mapping_*.csv` file will have the following columns:
1. `Sequence Number`: Creation priority for target files. Lower numbers first. Blank rows processed last.
2. `Source File Name`: Name of the source CSV.
3. `Source Field Name`: Field name in the source CSV.
4. `Target File Name`: Name of the target CSV to be created.
5. `Target Field Name`: Field name in the target CSV header.
6. `Mapping Rule Type`: `Direct` (simple copy) or `Transform` (apply rules).
7. `Mapping Rule1` to `Mapping Rule5`: Placeholders for transformation logic.

**Mapping Relationships:**
- **1:1**: One source field → One target field
- **1:N**: One source file → Multiple target files (different fields to different targets)
- **N:1**: Multiple source files → One target file (fields from different sources combined)
- **Sequence Number** controls target file creation order, not individual field mappings
- All rows for the same target file are grouped together regardless of sequence number

**Example:**
```
Seq, Source File, Source Field, Target File, Target Field, Rule Type
1, Salsify_Extract.csv, Launch Quarter, Salsify_NewTarget1, Launch Quarter_New, Direct
2, Salsify_Extract.csv, Season, Salsify_NewTarget2, Season_New, Direct
3, products_export.csv, color, Salsify_NewTarget2, color_raks, Direct
4, products_export.csv, upc, Salsify_NewTarget1, upc_new, Direct
```

**Case Sensitivity:**
- Default: **Case-sensitive** validation (configurable via property)
- Property: `filesync.validation.case.sensitive=true`

## 6. Transformation Rules (TBD)
- Logic like: `if source field1 == "12345" then map to target A else map to target B`.
- Rules might be configured in a separate JSON file referenced by the rule columns.

## 7. Error Handling
- **Error Folder:** If an error occurs, create an `Error/` folder in the provided path.
- **Structure:** Create a dated folder `YYYYMMDD_HHmm` inside `Error/`.
- **Contents:**
    - The file that caused the error.
    - A log file with details (error message, stack trace, input file, field name).
- **Resilience:** Continue processing subsequent files after an error.

## 8. Automated Workflow
1. **Discover Tab:**
    - User selects path.
    - Tool scans and shows status (Mapping found, Input folder status, Output/Error detection).
    - User clicks "Next".
2. **Mapping Tab:**
    - Pre-populated from the mapping file.
    - **Validate Files:** Button to check availability of source files and fields.
    - Success/Failure prompts with specific details.
    - Display the finalized output path: `<input_path>/Output/YYYYMMDD_HHmm/`.
3. **Execute Tab:**
    - User clicks "Execute".
    - Files are created in the prompted output folder.

## 9. Wrapper Integration (apiguardwrapper)
- **ZIP Support:** Accept a ZIP file as input.
- **ZIP Output:** Return a ZIP file containing the generated output and logs.
- **Smart Integration:** Ensure new features work seamlessly within the existing Mule-based wrapper.

## 10. Logging
- Activity logs in the UI/backend.
- Log files specifically in the Error folder for failed items.

## 11. Corporate Styling
- **Borders:** Every shape/box must have a **solid purple line** border.
- **Corners:** Every shape/box must have **rounded corners**.
- **Headlines:** All headlines/titles must use **purple text**.
- **Color Consistency:** Ensure the specific purple shade `#6B46C1` is used across both Java GUI and Web Wrapper.
