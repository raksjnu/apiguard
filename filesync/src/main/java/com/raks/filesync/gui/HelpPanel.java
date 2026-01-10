package com.raks.filesync.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Help and Guide panel with comprehensive documentation
 */
public class HelpPanel extends JPanel {
    
    public HelpPanel() {
        initializeUI();
    }
    
    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ThemeConfig.PANEL_BACKGROUND);
        
        // Create tabbed pane for different help sections
        JTabbedPane helpTabs = new JTabbedPane();
        helpTabs.setBackground(ThemeConfig.BACKGROUND_COLOR);
        helpTabs.setForeground(ThemeConfig.TEXT_PRIMARY);
        
        // Add help sections
        helpTabs.addTab(ThemeConfig.getString("help.tab.overview"), createOverviewPanel());
        helpTabs.addTab(ThemeConfig.getString("help.tab.features"), createFeaturesPanel());
        helpTabs.addTab(ThemeConfig.getString("help.tab.architecture"), createArchitecturePanel());
        helpTabs.addTab(ThemeConfig.getString("help.tab.usage"), createUsagePanel());
        helpTabs.addTab(ThemeConfig.getString("help.tab.cli"), createCLIPanel());
        
        // Add listener to scroll to top when tab changes
        helpTabs.addChangeListener(e -> {
            int selectedIndex = helpTabs.getSelectedIndex();
            if (selectedIndex >= 0) {
                Component comp = helpTabs.getComponentAt(selectedIndex);
                if (comp instanceof JPanel) {
                    JPanel panel = (JPanel) comp;
                    for (Component c : panel.getComponents()) {
                        if (c instanceof JScrollPane) {
                            JScrollPane scrollPane = (JScrollPane) c;
                            SwingUtilities.invokeLater(() -> {
                                scrollPane.getVerticalScrollBar().setValue(0);
                                scrollPane.getHorizontalScrollBar().setValue(0);
                            });
                        }
                    }
                }
            }
        });
        
        add(helpTabs, BorderLayout.CENTER);
    }
    
    private JPanel createOverviewPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ThemeConfig.BACKGROUND_COLOR);
        
        JTextArea textArea = createStyledTextArea();
        textArea.setText(
            "FILESYNC TOOL - OVERVIEW\n" +
            "========================\n\n" +
            "FileSync is a configuration-driven CSV transformation tool designed to handle\n" +
            "hundreds of different source files with unknown layouts.\n\n" +
            "KEY CONCEPTS:\n\n" +
            "• Dynamic Discovery: Automatically scans directories and detects CSV schemas\n" +
            "• Configuration-Based: Uses JSON files to define reusable mappings\n" +
            "• Session Persistence: Remembers your last used directories and files\n" +
            "• Auto-Load: Automatically loads mappings when switching to Execute tab\n" +
            "• Dual Interface: Both GUI and CLI for different workflows\n" +
            "• Extensible: Ready for future rule-based conditional mappings\n\n" +
            "PURPOSE:\n\n" +
            "This tool eliminates the need for hardcoded transformation logic by allowing\n" +
            "you to visually create field mappings between source and target CSV files,\n" +
            "save them as configurations, and execute transformations automatically.\n\n" +
            "TYPICAL WORKFLOW:\n\n" +
            "1. Discover: Scan source directory to identify CSV files and their fields\n" +
            "2. Map: Create field-to-field mappings between source and target files\n" +
            "3. Save: Export mappings as a reusable JSON configuration\n" +
            "4. Execute: Run transformations to generate target CSV files\n\n" +
            "VERSION: " + ThemeConfig.getString("app.version") + "\n" +
            ThemeConfig.getString("app.copyright")
        );
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(ThemeConfig.BORDER_COLOR, 1));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createFeaturesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ThemeConfig.BACKGROUND_COLOR);
        
        JTextArea textArea = createStyledTextArea();
        textArea.setText(
            "FEATURES\n" +
            "========\n\n" +
            "CORE FEATURES:\n\n" +
            "✓ Dynamic CSV Discovery\n" +
            "  - Scans directories for CSV files\n" +
            "  - Automatically extracts headers (field names)\n" +
            "  - No hardcoded file structures required\n\n" +
            "✓ Visual Mapping Builder\n" +
            "  - Intuitive GUI for creating field mappings\n" +
            "  - Source file/field selection\n" +
            "  - Target file/field definition\n" +
            "  - Mapping table view with add/remove/edit\n\n" +
            "✓ Configuration Management\n" +
            "  - JSON-based mapping definitions\n" +
            "  - Save/Load configurations\n" +
            "  - Reusable across different scenarios\n" +
            "  - Version controlled\n\n" +
            "✓ Flexible Transformation\n" +
            "  - File-to-file mappings\n" +
            "  - Field-to-field mappings\n" +
            "  - Multiple source → multiple target files\n" +
            "  - Configurable transformation types\n\n" +
            "✓ Dual Interface\n" +
            "  - GUI: Visual mapping creation and interactive use\n" +
            "  - CLI: Automation, scripting, and batch processing\n\n" +
            "✓ Robust Error Handling\n" +
            "  - Configuration validation\n" +
            "  - Missing file warnings\n" +
            "  - Detailed execution logs\n" +
            "  - Success/Warning/Error categorization\n\n" +
            "✓ Session Persistence\n" +
            "  - Remembers last source directory\n" +
            "  - Remembers last target directory\n" +
            "  - Remembers last config file location\n" +
            "  - Remembers window size and position\n" +
            "  - Persists across application restarts\n\n" +
            "✓ Auto-Load Configuration\n" +
            "  - Automatically loads mappings from Mapping tab\n" +
            "  - No manual save/load required during workflow\n" +
            "  - Seamless tab switching\n" +
            "  - Shows configuration source\n\n" +
            "TRANSFORMATION TYPES:\n\n" +
            "• Direct: Copy value as-is (currently supported)\n" +
            "• Formula: Mathematical or string formulas (future)\n" +
            "• Conditional: Rule-based logic (future)\n" +
            "• Lookup: Reference table mapping (future)\n\n" +
            "SUPPORTED FORMATS:\n\n" +
            "• Input: CSV files (comma-separated)\n" +
            "• Output: CSV files with custom headers\n" +
            "• Configuration: JSON format"
        );
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(ThemeConfig.BORDER_COLOR, 1));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createArchitecturePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ThemeConfig.BACKGROUND_COLOR);
        
        JTextArea textArea = createStyledTextArea();
        textArea.setText(
            "ARCHITECTURE\n" +
            "============\n\n" +
            "DESIGN PRINCIPLES:\n\n" +
            "• Configuration-Driven: All mappings defined in JSON, not code\n" +
            "• Scalable: Handles hundreds of files with unknown layouts\n" +
            "• Reusable: Configurations can be shared and version controlled\n" +
            "• Extensible: Ready for future rule-based logic\n\n" +
            "COMPONENTS:\n\n" +
            "1. Configuration Module (com.raks.filesync.config)\n" +
            "   • MappingConfig: Root configuration model\n" +
            "   • FileMapping: Source → Target file mappings\n" +
            "   • FieldMapping: Field-level mappings with transformations\n" +
            "   • ConfigLoader: JSON deserialization and validation\n" +
            "   • ConfigWriter: JSON serialization\n\n" +
            "2. Core Processing Engine (com.raks.filesync.core)\n" +
            "   • CsvDiscovery: Dynamic file and schema discovery\n" +
            "   • CsvReader: CSV parsing with OpenCSV\n" +
            "   • CsvWriter: CSV output generation\n" +
            "   • MappingExecutor: Main transformation engine\n" +
            "   • TransformationEngine: Field value transformations\n\n" +
            "3. CLI Interface (com.raks.filesync.cli)\n" +
            "   • CliInterface: Command-line interface\n" +
            "   • Modes: discover, execute, validate\n\n" +
            "4. GUI Interface (com.raks.filesync.gui)\n" +
            "   • MainWindow: Main application window\n" +
            "   • DiscoveryPanel: Directory browser and file tree\n" +
            "   • MappingPanel: Visual mapping builder\n" +
            "   • ExecutePanel: Execution controls and logging\n" +
            "   • HelpPanel: User guide and documentation\n\n" +
            "CONFIGURATION SCHEMA:\n\n" +
            "{\n" +
            "  \"version\": \"1.0\",\n" +
            "  \"paths\": {\n" +
            "    \"sourceDirectory\": \"path/to/source\",\n" +
            "    \"targetDirectory\": \"path/to/target\"\n" +
            "  },\n" +
            "  \"fileMappings\": [\n" +
            "    {\n" +
            "      \"sourceFile\": \"source.csv\",\n" +
            "      \"targetFile\": \"target.csv\",\n" +
            "      \"fieldMappings\": [\n" +
            "        {\n" +
            "          \"sourceField\": \"field1\",\n" +
            "          \"targetField\": \"field2\",\n" +
            "          \"transformation\": \"direct\"\n" +
            "        }\n" +
            "      ]\n" +
            "    }\n" +
            "  ]\n" +
            "}\n\n" +
            "DEPENDENCIES:\n\n" +
            "• OpenCSV 5.9: CSV processing\n" +
            "• Gson 2.10.1: JSON configuration\n" +
            "• Commons CLI 1.6.0: Command-line parsing\n" +
            "• SLF4J 2.0.9: Logging"
        );
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(ThemeConfig.BORDER_COLOR, 1));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createUsagePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ThemeConfig.BACKGROUND_COLOR);
        
        JTextArea textArea = createStyledTextArea();
        textArea.setText(
            "HOW TO USE - GUI WORKFLOW\n" +
            "=========================\n\n" +
            "TAB 1: DISCOVERY\n\n" +
            "Purpose: Scan source directory and identify CSV files\n\n" +
            "Steps:\n" +
            "1. Click 'Browse...' to select your source directory\n" +
            "2. Click 'Scan Directory' to discover CSV files\n" +
            "3. Review the file tree showing all discovered files and their fields\n\n" +
            "Tips:\n" +
            "• The tool automatically extracts column headers from each CSV\n" +
            "• You can scan multiple times if you add new files to the directory\n\n\n" +
            "TAB 2: MAPPING\n\n" +
            "Purpose: Create field-to-field mappings visually\n\n" +
            "Steps:\n" +
            "1. Select a Source File from the dropdown\n" +
            "2. Select a Source Field from the dropdown\n" +
            "3. Enter the Target File name (e.g., 'output.csv')\n" +
            "4. Enter the Target Field name (e.g., 'CustomerID')\n" +
            "5. Click 'Add Mapping →' to create the mapping\n" +
            "6. Repeat for all field mappings you need\n" +
            "7. Enter the Target Directory path\n" +
            "8. Click 'Save Configuration' to export as JSON\n\n" +
            "Tips:\n" +
            "• You can map multiple source files to different target files\n" +
            "• Use 'Remove Selected' to delete a mapping from the table\n" +
            "• Use 'Clear All' to start over\n" +
            "• Save configurations for reuse across different runs\n\n\n" +
            "TAB 3: EXECUTE\n\n" +
            "Purpose: Run transformations and generate target CSV files\n\n" +
            "AUTO-LOAD FEATURE:\n" +
            "When you switch to the Execute tab, the tool automatically loads\n" +
            "the mappings you created in the Mapping tab. No manual steps needed!\n\n" +
            "Steps:\n" +
            "Option A - Use auto-loaded mappings (RECOMMENDED):\n" +
            "1. Switch to Execute tab (mappings auto-load!)\n" +
            "2. Review the configuration details\n" +
            "3. Click '▶ Execute Transformation'\n\n" +
            "Option B - Load a saved configuration file:\n" +
            "1. Click 'Browse...' to select a saved config JSON file\n" +
            "2. Click 'Load Config' to load the configuration\n" +
            "3. Review the configuration details\n" +
            "4. Click '▶ Execute Transformation'\n\n" +
            "Tips:\n" +
            "• The tool shows '[From Mapping Tab]' when using auto-loaded config\n" +
            "• Watch the progress bar during execution\n" +
            "• Review the execution log for detailed results with full paths\n" +
            "• Check the target directory for generated CSV files\n" +
            "• Use 'Clear Log' to clean up the log area\n\n" +
            "SESSION PERSISTENCE:\n" +
            "The tool remembers your last session:\n" +
            "• Source directory auto-fills in Discovery tab\n" +
            "• Target directory auto-fills in Mapping tab\n" +
            "• Config file location is remembered\n" +
            "• Window size and position are preserved\n\n\n" +
            "TAB 4: HELP & GUIDE\n\n" +
            "Purpose: Access documentation and help\n\n" +
            "• Overview: Introduction and key concepts\n" +
            "• Features: List of all features\n" +
            "• Architecture: Technical design and components\n" +
            "• How to Use: This guide\n" +
            "• CLI Reference: Command-line usage"
        );
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(ThemeConfig.BORDER_COLOR, 1));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createCLIPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ThemeConfig.BACKGROUND_COLOR);
        
        JTextArea textArea = createStyledTextArea();
        textArea.setText(
            "CLI REFERENCE\n" +
            "=============\n\n" +
            "LAUNCHING CLI:\n\n" +
            "java -jar filesync-1.0.0.jar [options]\n\n" +
            "If no arguments are provided, the GUI will launch.\n\n\n" +
            "MODES:\n\n" +
            "1. DISCOVER MODE\n" +
            "   Purpose: Scan source directory and display CSV schemas\n\n" +
            "   Syntax:\n" +
            "   java -jar filesync-1.0.0.jar -m discover -s <source_directory>\n\n" +
            "   Example:\n" +
            "   java -jar filesync-1.0.0.jar -m discover -s C:\\data\\source\n\n" +
            "   Output:\n" +
            "   - Lists all CSV files found\n" +
            "   - Shows field names for each file\n" +
            "   - Displays field count\n\n\n" +
            "2. EXECUTE MODE\n" +
            "   Purpose: Run transformations from a configuration file\n\n" +
            "   Syntax:\n" +
            "   java -jar filesync-1.0.0.jar -m execute -c <config_file.json>\n\n" +
            "   Example:\n" +
            "   java -jar filesync-1.0.0.jar -m execute -c C:\\config\\mapping.json\n\n" +
            "   Output:\n" +
            "   - Loads configuration\n" +
            "   - Executes transformations\n" +
            "   - Shows success/warning/error summary\n" +
            "   - Lists processed files and row counts\n\n\n" +
            "3. VALIDATE MODE\n" +
            "   Purpose: Validate configuration file syntax\n\n" +
            "   Syntax:\n" +
            "   java -jar filesync-1.0.0.jar -m validate -c <config_file.json>\n\n" +
            "   Example:\n" +
            "   java -jar filesync-1.0.0.jar -m validate -c C:\\config\\mapping.json\n\n" +
            "   Output:\n" +
            "   - Validates JSON syntax\n" +
            "   - Checks required fields\n" +
            "   - Reports configuration details\n\n\n" +
            "OPTIONS:\n\n" +
            "-h, --help\n" +
            "   Show help message and examples\n\n" +
            "-m, --mode <mode>\n" +
            "   Operation mode: discover, execute, validate\n\n" +
            "-s, --source <directory>\n" +
            "   Source directory path (for discover mode)\n\n" +
            "-c, --config <file>\n" +
            "   Configuration file path (for execute/validate modes)\n\n\n" +
            "EXAMPLES:\n\n" +
            "# Show help\n" +
            "java -jar filesync-1.0.0.jar -h\n\n" +
            "# Launch GUI\n" +
            "java -jar filesync-1.0.0.jar\n" +
            "java -jar filesync-1.0.0.jar gui\n\n" +
            "# Discover files\n" +
            "java -jar filesync-1.0.0.jar -m discover -s C:\\data\\source\n\n" +
            "# Execute transformation\n" +
            "java -jar filesync-1.0.0.jar -m execute -c config.json\n\n" +
            "# Validate configuration\n" +
            "java -jar filesync-1.0.0.jar -m validate -c config.json\n\n\n" +
            "EXIT CODES:\n\n" +
            "0 - Success\n" +
            "1 - Error (check console output for details)"
        );
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(ThemeConfig.BORDER_COLOR, 1));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JTextArea createStyledTextArea() {
        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        textArea.setForeground(ThemeConfig.TEXT_PRIMARY);
        textArea.setBackground(ThemeConfig.BACKGROUND_COLOR);
        textArea.setMargin(new Insets(10, 10, 10, 10));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        return textArea;
    }
}
