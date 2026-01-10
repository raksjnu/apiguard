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
    
    private JTextField configPathField;
    private JButton browseConfigButton;
    private JButton loadConfigButton;
    private JButton executeButton;
    private JButton resetButton;
    private JTextArea configInfoArea;
    private JProgressBar progressBar;
    private JTextArea logArea;
    
    private MappingConfig loadedConfig;
    private String loadedConfigFilePath; // Track the actual file path
    
    public ExecutePanel(MappingPanel mappingPanel) {
        this.mappingPanel = mappingPanel;
        initializeUI();
        loadLastConfigFile();
    }
    
    /**
     * Auto-load configuration from Mapping tab when switching to Execute tab
     */
    public void refreshFromMappingTab() {
        // Try to get config from mapping panel
        MappingConfig config = mappingPanel.getCurrentConfig();
        if (config != null && !config.getFileMappings().isEmpty()) {
            loadedConfig = config;
            loadedConfigFilePath = null; // No file path for auto-loaded config
            displayConfigInfo(config, "[From Mapping Tab]");
            configPathField.setText("[Using mappings from Mapping tab]");
        }
    }
    
    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Top panel - Config loading and execution controls
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createTitledBorder("Configuration"));
        
        // Config file selection panel
        JPanel configLoadPanel = new JPanel(new BorderLayout(5, 5));
        configPathField = new JTextField();
        browseConfigButton = new JButton("Browse...");
        browseConfigButton.addActionListener(e -> browseConfig());
        loadConfigButton = new JButton("Load Config");
        loadConfigButton.addActionListener(e -> loadConfig());
        
        JPanel pathPanel = new JPanel(new BorderLayout(5, 5));
        pathPanel.add(new JLabel("Config File:"), BorderLayout.WEST);
        pathPanel.add(configPathField, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        buttonPanel.add(browseConfigButton);
        buttonPanel.add(loadConfigButton);
        pathPanel.add(buttonPanel, BorderLayout.EAST);
        
        configLoadPanel.add(pathPanel, BorderLayout.NORTH);
        
        // Config info area - INCREASED HEIGHT for better visibility
        configInfoArea = new JTextArea(8, 40); // Increased from 3 to 8 rows
        configInfoArea.setEditable(false);
        configInfoArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        configInfoArea.setText("No configuration loaded.\nYou can load a saved configuration or use mappings from the Mapping tab.");
        JScrollPane configScrollPane = new JScrollPane(configInfoArea);
        configScrollPane.setPreferredSize(new Dimension(0, 150)); // Set minimum height
        configLoadPanel.add(configScrollPane, BorderLayout.CENTER);
        
        // Execution controls panel (always visible at bottom of config section)
        JPanel execControlPanel = new JPanel(new BorderLayout(10, 5));
        
        JPanel execButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        executeButton = new JButton("▶ Execute Transformation");
        executeButton.setFont(new Font("Arial", Font.BOLD, 14));
        executeButton.setPreferredSize(new Dimension(220, 35));
        executeButton.addActionListener(e -> executeTransformation());
        
        resetButton = new JButton("↺ Reset");
        resetButton.setFont(new Font("Arial", Font.PLAIN, 12));
        resetButton.setPreferredSize(new Dimension(100, 35));
        resetButton.addActionListener(e -> resetPanel());
        
        execButtonPanel.add(executeButton);
        execButtonPanel.add(resetButton);
        
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        progressBar.setPreferredSize(new Dimension(0, 25));
        
        execControlPanel.add(execButtonPanel, BorderLayout.NORTH);
        execControlPanel.add(progressBar, BorderLayout.CENTER);
        
        configLoadPanel.add(execControlPanel, BorderLayout.SOUTH);
        
        topPanel.add(configLoadPanel, BorderLayout.CENTER);
        
        // Bottom panel - Log (takes remaining space)
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createTitledBorder("Execution Log"));
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        
        JScrollPane logScrollPane = new JScrollPane(logArea);
        bottomPanel.add(logScrollPane, BorderLayout.CENTER);
        
        JButton clearLogButton = new JButton("Clear Log");
        clearLogButton.addActionListener(e -> logArea.setText(""));
        JPanel logButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        logButtonPanel.add(clearLogButton);
        bottomPanel.add(logButtonPanel, BorderLayout.SOUTH);
        
        // Add panels with proper sizing
        add(topPanel, BorderLayout.NORTH);
        add(bottomPanel, BorderLayout.CENTER);
    }
    
    private void resetPanel() {
        loadedConfig = null;
        loadedConfigFilePath = null;
        configPathField.setText("");
        configInfoArea.setText("No configuration loaded.\\nYou can load a saved configuration or use mappings from the Mapping tab.");
        logArea.setText("");
        progressBar.setValue(0);
        progressBar.setString("Ready");
        log("Panel reset. Ready for new configuration.");
    }
    
    private void browseConfig() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Configuration File");
        
        // Start from last config file directory if available
        String lastConfig = UserPreferences.getLastConfigFile();
        if (!lastConfig.isEmpty()) {
            File lastFile = new File(lastConfig);
            chooser.setCurrentDirectory(lastFile.getParentFile());
            chooser.setSelectedFile(lastFile);
        }
        
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            String path = file.getAbsolutePath();
            configPathField.setText(path);
            loadedConfigFilePath = path; // Store the file path
            UserPreferences.setLastConfigFile(path);
        }
    }
    
    private void loadConfig() {
        String configPath = configPathField.getText().trim();
        
        if (configPath.isEmpty() || configPath.equals("[Using mappings from Mapping tab]")) {
            JOptionPane.showMessageDialog(this,
                    "Please select a configuration file.",
                    "No File Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        try {
            ConfigLoader loader = new ConfigLoader();
            loadedConfig = loader.loadConfig(configPath);
            loadedConfigFilePath = configPath; // Store the actual file path
            
            // Display config info
            displayConfigInfo(loadedConfig, configPath);
            
            // Save config file preference
            UserPreferences.setLastConfigFile(configPath);
            
            log("Configuration loaded: " + configPath);
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error loading configuration: " + e.getMessage(),
                    "Load Error",
                    JOptionPane.ERROR_MESSAGE);
            log("ERROR: Failed to load configuration - " + e.getMessage());
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
        
        // Show first few mappings as preview
        int previewCount = Math.min(3, config.getFileMappings().size());
        if (previewCount > 0) {
            info.append("\nPreview:\n");
            for (int i = 0; i < previewCount; i++) {
                var mapping = config.getFileMappings().get(i);
                info.append("  ").append(i + 1).append(". ")
                    .append(mapping.getSourceFile())
                    .append(" → ")
                    .append(mapping.getTargetFile())
                    .append(" (").append(mapping.getFieldMappings().size()).append(" fields)\n");
            }
            if (config.getFileMappings().size() > 3) {
                info.append("  ... and ").append(config.getFileMappings().size() - 3).append(" more\n");
            }
        }
        
        configInfoArea.setText(info.toString());
        
        // Update config path field to show actual file path if it was saved
        if (loadedConfigFilePath != null && !loadedConfigFilePath.isEmpty()) {
            configPathField.setText(loadedConfigFilePath);
        }
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
                publish("═══════════════════════════════════════════════════════");
                publish("  TRANSFORMATION STARTED");
                publish("═══════════════════════════════════════════════════════");
                publish("");
                publish("Configuration:");
                publish("  Source Directory: " + finalConfig.getPaths().getSourceDirectory());
                publish("  Target Directory: " + finalConfig.getPaths().getTargetDirectory());
                publish("  File Mappings: " + finalConfig.getFileMappings().size());
                publish("");
                publish("───────────────────────────────────────────────────────");
                publish("");
                
                MappingExecutor executor = new MappingExecutor();
                return executor.execute(finalConfig);
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
        // Add timestamp only for major sections
        if (message.contains("═══") || message.contains("STARTED") || message.contains("COMPLETE")) {
            String timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            logArea.append("[" + timestamp + "] " + message + "\n");
        } else {
            logArea.append(message + "\n");
        }
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
    
    private void loadLastConfigFile() {
        String lastConfig = UserPreferences.getLastConfigFile();
        if (!lastConfig.isEmpty()) {
            configPathField.setText(lastConfig);
            loadedConfigFilePath = lastConfig;
        }
    }
}
