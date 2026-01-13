package com.raks.filesync.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Help and Guide panel with comprehensive documentation (HTML Based)
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
        
        // Add help sections (HTML Content)
        helpTabs.addTab(ThemeConfig.getString("help.tab.overview"), createHtmlPanel(getOverviewHtml()));
        helpTabs.addTab(ThemeConfig.getString("help.tab.features"), createHtmlPanel(getFeaturesHtml()));
        helpTabs.addTab(ThemeConfig.getString("help.tab.usage"), createHtmlPanel(getUsageHtml()));
        helpTabs.addTab(ThemeConfig.getString("help.tab.cli"), createHtmlPanel(getCliHtml()));
        
        // Add listener to scroll to top when tab changes
        helpTabs.addChangeListener(e -> {
            int selectedIndex = helpTabs.getSelectedIndex();
            if (selectedIndex >= 0) {
                Component comp = helpTabs.getComponentAt(selectedIndex);
                if (comp instanceof JScrollPane) {
                    JScrollPane scrollPane = (JScrollPane) comp;
                    SwingUtilities.invokeLater(() -> {
                        scrollPane.getVerticalScrollBar().setValue(0);
                        scrollPane.getHorizontalScrollBar().setValue(0);
                    });
                }
            }
        });
        
        add(helpTabs, BorderLayout.CENTER);
    }
    
    private JScrollPane createHtmlPanel(String htmlContent) {
        JEditorPane editorPane = new JEditorPane();
        editorPane.setContentType("text/html");
        editorPane.setText(htmlContent);
        editorPane.setEditable(false);
        editorPane.setBackground(Color.WHITE);
        editorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        editorPane.setFont(ThemeConfig.getScaledFont("SansSerif", Font.PLAIN, 12));
        
        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setBorder(BorderFactory.createLineBorder(ThemeConfig.BORDER_COLOR, 1));
        return scrollPane;
    }
    
    private String getCommonCss() {
        return "<style>" +
               "body { font-family: SansSerif; font-size: 12px; padding: 10px; color: #333; }" +
               "h1 { color: #6B46C1; border-bottom: 2px solid #6B46C1; padding-bottom: 5px; margin-top: 0; }" +
               "h2 { color: #553C9A; margin-top: 20px; border-bottom: 1px solid #ddd; padding-bottom: 3px; }" +
               "h3 { color: #805AD5; margin-top: 15px; }" +
               "ul { margin-left: 20px; }" +
               "li { margin-bottom: 5px; }" +
               "code { font-family: Consolas, monospace; background-color: #f0f0f0; padding: 2px 5px; border-radius: 3px; color: #d63384; }" +
               ".box { background-color: #f8f9fa; border: 1px solid #e9ecef; padding: 10px; border-radius: 5px; margin: 10px 0; }" +
               ".note { background-color: #e9f5ff; border-left: 4px solid #007bff; padding: 10px; margin: 10px 0; }" +
               ".highlight { color: #6B46C1; font-weight: bold; }" +
               "</style>";
    }
    
    private String getOverviewHtml() {
        return "<html><head>" + getCommonCss() + "</head><body>" +
               "<h1>FileSync Tool Overview</h1>" +
               "<div class='box'>" +
               "<p><b>FileSync</b> is an enterprise-grade CSV transformation platform designed to simplify complex data integration tasks. " +
               "It eliminates the need for hardcoded scripts by providing a flexible, configuration-driven approach to mapping and transforming files.</p>" +
               "</div>" +
               
               "<h2>Why FileSync? (The Use Case)</h2>" +
               "<p>If you have <b>tons of CSV files</b> and need to create different output files by extracting and combining fields from one or more sources, this tool is the answer.</p>" +
               "<ul>" +
               "<li><b>Automates File Creation:</b> Just define simple mapping rules, and the tool handles the rest.</li>" +
               "<li><b>Smart Detection:</b> Automatically detects fields and maps them, saving you hours of manual work.</li>" +
               "<li><b>Mass Processing:</b> specialized for handling large datasets and numerous files efficiently.</li>" +
               "</ul>" +
               
               "<h2>Key Concepts</h2>" +
               "<ul>" +
               "<li><b>Dynamic Discovery:</b> Automatically scans directories to detect CSV files and their schemas recursively.</li>" +
               "<li><b>Visual Mapping:</b> Drag-and-drop style field mapping with a clean, user-friendly interface.</li>" +
               "<li><b>Configuration-Driven:</b> Save your mappings as reusable JSON configurations for repeated use.</li>" +
               "<li><b>Validation Loop:</b> Real-time validation ensures data integrity before execution.</li>" +
               "</ul>" +
               "</body></html>";
    }
    
    private String getFeaturesHtml() {
        return "<html><head>" + getCommonCss() + "</head><body>" +
               "<h1>Powerful Features</h1>" +
               
               "<h2>Core Capabilities</h2>" +
               "<ul>" +
               "<li><b>Recursive Scanning:</b> Detects data deep within directory structures.</li>" +
               "<li><b>Smart Schema Detection:</b> Automatically reads headers to simplify mapping.</li>" +
               "<li><b>Advanced Filtering:</b> Quickly find fields in large datasets with the built-in search filter.</li>" +
               "</ul>" +
               
               "<h2>User Experience</h2>" +
               "<ul>" +
               "<li><b>Persistent Sessions:</b> Remembers your last used settings so you can pick up where you left off.</li>" +
               "<li><b>Resizable Layouts:</b> Adjust panels to fit your screen and workflow.</li>" +
               "<li><b>Accessibility:</b> Global zoom (Ctrl +/-) and high-contrast purple theme.</li>" +
               "</ul>" +
               
               "<h2>Transformation & Output</h2>" +
               "<ul>" +
               "<li><b>Multi-Source Merge:</b> Intelligently merges data from multiple source files into a single target.</li>" +
               "<li><b>Audit Logging:</b> Detailed logs with record counts and file sizes for compliance.</li>" +
               "<li><b>Error Handling:</b> Robust validation prevents bad data from breaking the process.</li>" +
               "</ul>" +
               "</body></html>";
    }
    
    private String getUsageHtml() {
        return "<html><head>" + getCommonCss() + "</head><body>" +
               "<h1>How to Use & Rules</h1>" +
               
               "<div class='note' style='border-color: #d53f8c; background-color: #fff5f7;'>" +
               "<h3 style='margin-top:0; color: #b83280;'>⚠ Mandatory Rules & Formats</h3>" +
               "<ul>" +
               "<li><b>Input Folder Location:</b> You MUST have your source data in a folder named <code>Input/YYYYMMDD_HHMM</code> (e.g., <code>.../Raks/Input/20240101_1200</code>).</li>" +
               "<li><b>Mapping File Name:</b> Adhere to the standard naming convention (e.g., <code>mapping.json</code> or <code>*.csv</code>) to ensure auto-detection by the picker.</li>" +
               "<li><b>Target Directory:</b> ensure the target directory is writable. The tool will automatically create an <code>Output</code> folder sibling to your Input.</li>" +
               "</ul>" +
               "</div>" +

               "<h2>1. Discovery Phase</h2>" +
               "<ul>" +
               "<li>Click <span class='highlight'>Browse...</span> to select your root data directory.</li>" +
               "<li>Click <span class='highlight'>Scan Directory</span> to index all CSV files.</li>" +
               "<li>Use the dropdown pickers to select the specific <b>Data Folder</b> and <b>Mapping File</b> you want to work with.</li>" +
               "</ul>" +
               
               "<h2>2. Mapping Phase</h2>" +
               "<ul>" +
               "<li>Review the automatically loaded mappings in the central table.</li>" +
               "<li>Use the <span class='highlight'>Filter Search Bar</span> to quickly locate specific fields.</li>" +
               "<li>Click <span class='highlight'>Validate Mapping & Files</span> to integrity-check your configuration.</li>" +
               "<li>Look at the validation results panel for a summary of what will be processed.</li>" +
               "</ul>" +
               
               "<h2>3. Execution Phase</h2>" +
               "<ul>" +
               "<li>Switch to the <b>Execute</b> tab. Your validated configuration loads automatically.</li>" +
               "<li>Click <span class='highlight'>Execute Transformation</span> to start the job.</li>" +
               "<li>Watch the <b>Execution Log</b> for real-time progress, file sizes, and record counts.</li>" +
               "</ul>" +
               "</body></html>";
    }
    
    private String getCliHtml() {
        return "<html><head>" + getCommonCss() + "</head><body>" +
               "<h1>CLI Reference</h1>" +
               "<div class='box'>The FileSync CLI allows you to integrate transformations into automated pipelines and scheduled jobs.</div>" +
               
               "<h2>Syntax</h2>" +
               "<code>java -jar filesync.jar -m execute -c &lt;config_file.json&gt;</code>" +
               
               "<h2>Modes</h2>" +
               "<table border='0' cellspacing='5'>" +
               "<tr><td><b>discover</b></td><td>Scans a directory for schemas.</td></tr>" +
               "<tr><td><b>validate</b></td><td>Checks a configuration file for errors.</td></tr>" +
               "<tr><td><b>execute</b></td><td>Runs the actual data transformation.</td></tr>" +
               "</table>" +
               
               "<h2>Examples</h2>" +
               "<h3>1. Discover Files</h3>" +
               "<code>java -jar filesync.jar -m discover -s C:\\Data\\Input</code>" +
               
               "<h3>2. Execute Transformation</h3>" +
               "<code>java -jar filesync.jar -m execute -c C:\\Configs\\daily_map.json</code>" +
               
               "<h2>Memory Configuration</h2>" +
               "<p>For large datasets (100k+ rows), allocate more memory:</p>" +
               "<code>java -Xmx4g -jar filesync.jar ...</code>" +
               "</body></html>";
    }
}
