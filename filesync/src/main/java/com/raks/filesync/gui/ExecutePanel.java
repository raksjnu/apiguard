package com.raks.filesync.gui;

import com.raks.filesync.config.ConfigLoader;
import com.raks.filesync.config.MappingConfig;
import com.raks.filesync.core.MappingExecutor;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Execute panel for running transformations - Redesigned layout
 */
public class ExecutePanel extends JPanel {
    private final MappingPanel mappingPanel;
    
    private JButton executeButton;
    private JButton resetButton;
    private JTextArea configInfoArea;
    private JProgressBar progressBar;
    private JTextPane logPane;
    private javax.swing.text.StyledDocument logDoc;
    
    private MappingConfig loadedConfig;
    private String loadedConfigFilePath; // Track the actual file path
    
    public ExecutePanel(MappingPanel mappingPanel) {
        this.mappingPanel = mappingPanel;
        initializeUI();
    }
    
    /**
     * Auto-load configuration from Mapping tab when switching to Execute tab
     */
    public void refreshFromMappingTab() {
        // Try to get config from mapping panel
        MappingConfig config = mappingPanel.getCurrentConfig();
        if (config != null && !config.getFileMappings().isEmpty()) {
            loadedConfig = config;
            // Config from Mapping tab has a validated output directory like .../Output/Timestamp
            // MappingExecutor appends /Output/Timestamp again.
            // We need to set the Target Directory to the root (parent of parent) to avoid duplication.
            String validatedDir = mappingPanel.getLastGeneratedOutputDir();
            if (validatedDir != null) {
                File dir = new File(validatedDir);
                // Go up 2 levels: Output/Timestamp -> Output -> Root
                if (dir.getParentFile() != null && dir.getParentFile().getParentFile() != null) {
                    loadedConfig.getPaths().setTargetDirectory(dir.getParentFile().getParent());
                } else {
                    loadedConfig.getPaths().setTargetDirectory(validatedDir);
                }
            }

            
            // Get the mapping file path if available
            File mappingFile = mappingPanel.getCurrentMappingFile();
            if (mappingFile != null) {
                loadedConfigFilePath = mappingFile.getAbsolutePath();
            } else {
                loadedConfigFilePath = null;
            }
            
            displayConfigInfo(config, "[From Mapping Tab]");
        }
    }
    
    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Top panel - Config loading and execution controls
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ThemeConfig.PRIMARY_COLOR, 3, true),
            "Configuration Preview",
            javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
            javax.swing.border.TitledBorder.DEFAULT_POSITION,
            ThemeConfig.getScaledFont("Arial", Font.BOLD, 14),
            ThemeConfig.PRIMARY_COLOR));
        
        // Config info area
        configInfoArea = new JTextArea();
        configInfoArea.setEditable(false);
        configInfoArea.setFont(ThemeConfig.getScaledFont("Consolas", Font.PLAIN, 12));
        configInfoArea.setBackground(Color.WHITE);
        configInfoArea.setText("No configuration loaded.\nYou can load a saved configuration or use mappings from the Mapping tab.");
        JScrollPane configScrollPane = new JScrollPane(configInfoArea);
        topPanel.add(configScrollPane, BorderLayout.CENTER);
        
        // Bottom panel - Log and Execution Controls
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        
        // Execution controls panel
        JPanel execControlPanel = new JPanel(new BorderLayout(5, 5));
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        executeButton = new StyledButton("▶ Execute Transformation");
        executeButton.addActionListener(e -> executeTransformation());
        
        resetButton = new StyledButton("↺ Reset", true); // Headline style for secondary
        resetButton.addActionListener(e -> resetPanel());
        
        buttonPanel.add(executeButton);
        buttonPanel.add(resetButton);
        
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        progressBar.setPreferredSize(new Dimension(0, 25));
        
        execControlPanel.add(buttonPanel, BorderLayout.NORTH);
        execControlPanel.add(progressBar, BorderLayout.CENTER);
        
        bottomPanel.add(execControlPanel, BorderLayout.NORTH);
        
        // Log area
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ThemeConfig.PRIMARY_COLOR, 3, true),
            "Execution Log (Session Activity)",
            javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
            javax.swing.border.TitledBorder.DEFAULT_POSITION,
            ThemeConfig.getScaledFont("Arial", Font.BOLD, 12),
            ThemeConfig.PRIMARY_COLOR));
        
        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setFont(ThemeConfig.getScaledFont("Consolas", Font.PLAIN, 12));
        logDoc = logPane.getStyledDocument();
        
        JScrollPane logScrollPane = new JScrollPane(logPane);
        logPanel.add(logScrollPane, BorderLayout.CENTER);
        bottomPanel.add(logPanel, BorderLayout.CENTER);
        
        // Use SplitPane for resizability
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, bottomPanel);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.4);
        
        add(splitPane, BorderLayout.CENTER);
        
        // Print RAKS Logo
        printAsciiLogo();
    }
    
    private void printAsciiLogo() {
        String[] logo = {
            " ____      _    _  __ ____  ",
            "|  _ \\    / \\  | |/ // ___| ",
            "| |_) |  / _ \\ | ' / \\___ \\ ",
            "|  _ <  / ___ \\| . \\  ___) |",
            "|_| \\_\\/_/   \\_\\_|\\_\\|____/ ",
            "                            ",
            "FileSync Tool v" + ThemeConfig.getString("app.version"),
            "-------------------------------------------------------"
        };
        
        try {
            javax.swing.text.Style purpleStyle = logPane.addStyle("purple-bold", null);
            javax.swing.text.StyleConstants.setForeground(purpleStyle, ThemeConfig.PRIMARY_COLOR);
            javax.swing.text.StyleConstants.setFontFamily(purpleStyle, "Consolas");
            javax.swing.text.StyleConstants.setBold(purpleStyle, true);
            
            for (String line : logo) {
                logDoc.insertString(logDoc.getLength(), line + "\n", purpleStyle);
            }
            logDoc.insertString(logDoc.getLength(), "\n", purpleStyle);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void displayConfigInfo(MappingConfig config, String source) {
        StringBuilder info = new StringBuilder();
        info.append("Configuration loaded successfully!\n");
        info.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        
        info.append("Source: ").append(source).append("\n");
        info.append("Version: ").append(config.getVersion()).append("\n\n");
        
        info.append("Directories:\n");
        info.append("  • Source: ").append(config.getPaths().getSourceDirectory()).append("\n");
        info.append("  • Target: ").append(config.getPaths().getTargetDirectory()).append("\n\n");
        
        info.append("File Mappings: ").append(config.getFileMappings().size()).append(" mapping(s)\n");
        
        // Show all mappings
        if (!config.getFileMappings().isEmpty()) {
            info.append("\nMappings (").append(config.getFileMappings().size()).append("):\n");
            int index = 1;
            for (var mapping : config.getFileMappings()) {
                info.append("  ").append(index++).append(". ")
                    .append(mapping.getSourceFile())
                    .append(" → ")
                    .append(mapping.getTargetFile())
                    .append(" (").append(mapping.getFieldMappings().size()).append(" fields)\n");
            }
        }
        
        configInfoArea.setText(info.toString());
    }

    private void resetPanel() {
        loadedConfig = null;
        loadedConfigFilePath = null;
        configInfoArea.setText("No configuration loaded.\nYou can load a saved configuration or use mappings from the Mapping tab.");
        progressBar.setValue(0);
        progressBar.setString("Ready");
        log("Panel reset. Configuration cleared.");
    }
    

    
    private void executeTransformation() {
        // Get config (either loaded or from mapping panel)
        MappingConfig config = loadedConfig;
        
        if (config == null) {
            // Try to use config from mapping panel
            refreshFromMappingTab();
            config = loadedConfig;
            
            if (config == null || config.getFileMappings().isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "No configuration available.\\nPlease load a configuration file or create mappings in the Mapping tab.",
                        "No Configuration",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        
        // Confirm execution
        int confirm = JOptionPane.showConfirmDialog(this,
                "Execute transformation with " + config.getFileMappings().size() + " file mapping(s)?",
                "Confirm Execution",
                JOptionPane.YES_NO_OPTION);
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        // Execute in background thread
        final MappingConfig finalConfig = config;
        executeButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setString("Executing...");
        
        SwingWorker<MappingExecutor.ExecutionResult, String> worker = new SwingWorker<>() {
            @Override
            protected MappingExecutor.ExecutionResult doInBackground() {
                String sessionTimestamp = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
                
                publish("═══════════════════════════════════════════════════════");
                publish("  TRANSFORMATION SESSION");
                publish("═══════════════════════════════════════════════════════");
                publish("");
                publish("Source Folder: " + finalConfig.getPaths().getSourceDirectory());
                
                // Construct the full target path that WILL be created
                String effectiveTargetRoot = finalConfig.getPaths().getTargetDirectory();
                String fullTargetPath = java.nio.file.Paths.get(effectiveTargetRoot, "Output", sessionTimestamp).toString();
                
                publish("Target Folder: " + fullTargetPath);
                
                // Log full path for Mapping File
                publish("Mapping File:  " + (loadedConfigFilePath != null ? loadedConfigFilePath : "[Memory/Unsaved]"));
                publish("-------------------------------------------------------");
                publish("");
                publish("───────────────────────────────────────────────────────");
                publish("");
                
                MappingExecutor executor = new MappingExecutor();
                return executor.execute(finalConfig, sessionTimestamp);
            }
            
            @Override
            protected void process(java.util.List<String> chunks) {
                for (String message : chunks) {
                    log(message);
                }
            }
            
            @Override
            protected void done() {
                try {
                    MappingExecutor.ExecutionResult result = get();
                    
                    // Display results with clear formatting
                    log("");
                    log("═══════════════════════════════════════════════════════");
                    log("  EXECUTION SUMMARY");
                    log("═══════════════════════════════════════════════════════");
                    log("");
                    log(result.getSummary());
                    log("");
                    
                    if (!result.getSuccesses().isEmpty()) {
                        log("✓ SUCCESSES:");
                        log("───────────────────────────────────────────────────────");
                        for (String success : result.getSuccesses()) {
                            log("  " + success);
                        }
                        log("");
                    }
                    
                    if (!result.getWarnings().isEmpty()) {
                        log("⚠ WARNINGS:");
                        log("───────────────────────────────────────────────────────");
                        for (String warning : result.getWarnings()) {
                            log("  " + warning);
                        }
                        log("");
                    }
                    
                    if (!result.getErrors().isEmpty()) {
                        log("✗ ERRORS:");
                        log("───────────────────────────────────────────────────────");
                        for (String error : result.getErrors()) {
                            log("  " + error);
                        }
                        log("");
                    }
                    
                    log("═══════════════════════════════════════════════════════");
                    log("  TRANSFORMATION COMPLETE");
                    log("═══════════════════════════════════════════════════════");
                    
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    progressBar.setString("Complete");
                    
                    if (result.hasErrors()) {
                        JOptionPane.showMessageDialog(ExecutePanel.this,
                                "Transformation completed with errors.\\nCheck the log for details.",
                                "Execution Complete",
                                JOptionPane.WARNING_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(ExecutePanel.this,
                                "Transformation completed successfully!",
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                    
                } catch (Exception e) {
                    log("");
                    log("✗ ERROR: Execution failed");
                    log("  " + e.getMessage());
                    e.printStackTrace();
                    
                    JOptionPane.showMessageDialog(ExecutePanel.this,
                            "Execution failed: " + e.getMessage(),
                            "Execution Error",
                            JOptionPane.ERROR_MESSAGE);
                    
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(0);
                    progressBar.setString("Failed");
                } finally {
                    executeButton.setEnabled(true);
                }
            }
        };
        
        worker.execute();
    }
    
    private void log(String message) {
        try {
            // Define color styles
            javax.swing.text.Style defaultStyle = logPane.addStyle("default", null);
            javax.swing.text.StyleConstants.setForeground(defaultStyle, Color.BLACK);
            
            javax.swing.text.Style blueStyle = logPane.addStyle("blue", null);
            javax.swing.text.StyleConstants.setForeground(blueStyle, new Color(0, 102, 204)); // Blue
            javax.swing.text.StyleConstants.setBold(blueStyle, true);
            
            javax.swing.text.Style greenStyle = logPane.addStyle("green", null);
            javax.swing.text.StyleConstants.setForeground(greenStyle, new Color(0, 153, 0)); // Green
            javax.swing.text.StyleConstants.setBold(greenStyle, true);
            
            javax.swing.text.Style purpleStyle = logPane.addStyle("purple", null);
            javax.swing.text.StyleConstants.setForeground(purpleStyle, new Color(107, 70, 193)); // Purple
            javax.swing.text.StyleConstants.setBold(purpleStyle, true);
            
            // Determine style based on message content
            javax.swing.text.Style style = defaultStyle;
            String displayMessage = message;
            
            if (message.contains("TRANSFORMATION SESSION")) {
                style = blueStyle;
                String timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                displayMessage = "[" + timestamp + "] " + message;
            } else if (message.contains("TRANSFORMATION COMPLETE")) {
                style = greenStyle;
                String timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                displayMessage = "[" + timestamp + "] " + message;
            } else if (message.contains("EXECUTION SUMMARY")) {
                style = purpleStyle;
                String timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                displayMessage = "[" + timestamp + "] " + message;
            } else if (message.contains("═══")) {
                // Box drawing characters - add timestamp
                String timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                displayMessage = "[" + timestamp + "] " + message;
            }
            
            logDoc.insertString(logDoc.getLength(), displayMessage + "\n", style);
            logPane.setCaretPosition(logDoc.getLength());
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    

}
