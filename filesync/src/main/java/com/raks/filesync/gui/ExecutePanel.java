package com.raks.filesync.gui;

import com.raks.filesync.config.ConfigLoader;
import com.raks.filesync.config.MappingConfig;
import com.raks.filesync.core.MappingExecutor;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Execute panel for running transformations
 */
public class ExecutePanel extends JPanel {
    private final MappingPanel mappingPanel;
    
    private JTextField configPathField;
    private JButton browseConfigButton;
    private JButton loadConfigButton;
    private JTextArea configInfoArea;
    private JButton executeButton;
    private JProgressBar progressBar;
    private JTextArea logArea;
    
    private MappingConfig loadedConfig;
    
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
            displayConfigInfo(config, "[From Mapping Tab]");
            configPathField.setText("[Using mappings from Mapping tab]");
        }
    }
    
    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Top panel - Config loading
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createTitledBorder("Configuration"));
        
        JPanel configLoadPanel = new JPanel(new BorderLayout(5, 5));
        configPathField = new JTextField();
        browseConfigButton = new JButton("Browse...");
        browseConfigButton.addActionListener(e -> browseConfig());
        loadConfigButton = new JButton("Load Config");
        loadConfigButton.addActionListener(e -> loadConfig());
        
        JPanel pathPanel = new JPanel(new BorderLayout(5, 5));
        pathPanel.add(new JLabel("Config File:"), BorderLayout.WEST);
        pathPanel.add(configPathField, BorderLayout.CENTER);
        pathPanel.add(browseConfigButton, BorderLayout.EAST);
        
        configLoadPanel.add(pathPanel, BorderLayout.CENTER);
        configLoadPanel.add(loadConfigButton, BorderLayout.EAST);
        
        configInfoArea = new JTextArea(4, 40);
        configInfoArea.setEditable(false);
        configInfoArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        configInfoArea.setText("No configuration loaded.\nYou can load a saved configuration or use mappings from the Mapping tab.");
        
        topPanel.add(configLoadPanel, BorderLayout.NORTH);
        topPanel.add(new JScrollPane(configInfoArea), BorderLayout.CENTER);
        
        // Middle panel - Execute controls
        JPanel middlePanel = new JPanel(new BorderLayout(10, 10));
        middlePanel.setBorder(BorderFactory.createTitledBorder("Execution"));
        
        executeButton = new JButton("▶ Execute Transformation");
        executeButton.setFont(new Font("Arial", Font.BOLD, 14));
        executeButton.setPreferredSize(new Dimension(250, 40));
        executeButton.addActionListener(e -> executeTransformation());
        
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        
        JPanel executePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        executePanel.add(executeButton);
        
        middlePanel.add(executePanel, BorderLayout.NORTH);
        middlePanel.add(progressBar, BorderLayout.CENTER);
        
        // Bottom panel - Log
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
        
        // Add panels
        add(topPanel, BorderLayout.NORTH);
        add(middlePanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
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
            UserPreferences.setLastConfigFile(path);
        }
    }
    
    private void loadConfig() {
        String configPath = configPathField.getText().trim();
        
        if (configPath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please select a configuration file.",
                    "No File Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        try {
            ConfigLoader loader = new ConfigLoader();
            loadedConfig = loader.loadConfig(configPath);
            
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
        info.append("Source: ").append(source).append("\n\n");
        info.append("Version: ").append(config.getVersion()).append("\n");
        info.append("Source Directory: ").append(config.getPaths().getSourceDirectory()).append("\n");
        info.append("Target Directory: ").append(config.getPaths().getTargetDirectory()).append("\n");
        info.append("File Mappings: ").append(config.getFileMappings().size()).append("\n");
        
        configInfoArea.setText(info.toString());
    }
    
    private void executeTransformation() {
        // Get config (either loaded or from mapping panel)
        MappingConfig config = loadedConfig;
        
        if (config == null) {
            // Try to use config from mapping panel
            config = mappingPanel.getCurrentConfig();
            
            if (config == null || config.getFileMappings().isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "No configuration available.\nPlease load a configuration file or create mappings in the Mapping tab.",
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
                publish("=== TRANSFORMATION STARTED ===");
                publish("Source Directory: " + finalConfig.getPaths().getSourceDirectory());
                publish("Target Directory: " + finalConfig.getPaths().getTargetDirectory());
                publish("File Mappings: " + finalConfig.getFileMappings().size());
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
                    
                    // Display results with full paths
                    log("");
                    log("=== EXECUTION SUMMARY ===");
                    log(result.getSummary());
                    log("");
                    
                    if (!result.getSuccesses().isEmpty()) {
                        log("SUCCESSES:");
                        for (String success : result.getSuccesses()) {
                            log("  ✓ " + success);
                            // Add full path info
                            if (success.contains("Processed")) {
                                String targetDir = finalConfig.getPaths().getTargetDirectory();
                                log("    Target: " + targetDir);
                            }
                        }
                    }
                    
                    if (!result.getWarnings().isEmpty()) {
                        log("");
                        log("WARNINGS:");
                        for (String warning : result.getWarnings()) {
                            log("  ⚠ " + warning);
                        }
                    }
                    
                    if (!result.getErrors().isEmpty()) {
                        log("");
                        log("ERRORS:");
                        for (String error : result.getErrors()) {
                            log("  ✗ " + error);
                        }
                    }
                    
                    log("");
                    log("=== TRANSFORMATION COMPLETE ===");
                    
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    progressBar.setString("Complete");
                    
                    if (result.hasErrors()) {
                        JOptionPane.showMessageDialog(ExecutePanel.this,
                                "Transformation completed with errors.\nCheck the log for details.",
                                "Execution Complete",
                                JOptionPane.WARNING_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(ExecutePanel.this,
                                "Transformation completed successfully!",
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                    
                } catch (Exception e) {
                    log("ERROR: Execution failed - " + e.getMessage());
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
        String timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        logArea.append("[" + timestamp + "] " + message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
    
    private void loadLastConfigFile() {
        String lastConfig = UserPreferences.getLastConfigFile();
        if (!lastConfig.isEmpty()) {
            configPathField.setText(lastConfig);
        }
    }
}
