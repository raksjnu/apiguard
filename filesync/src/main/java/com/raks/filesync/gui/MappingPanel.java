package com.raks.filesync.gui;

import com.raks.filesync.config.*;
import com.raks.filesync.core.CsvDiscovery;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Mapping panel for creating field mappings
 */
public class MappingPanel extends JPanel {
    private final DiscoveryPanel discoveryPanel;
    private MappingConfig currentConfig;
    
    private JComboBox<String> sourceFileCombo;
    private JComboBox<String> sourceFieldCombo;
    private AutocompleteTextField targetFileField;
    private AutocompleteTextField targetFieldField;
    private JButton addMappingButton;
    private JTable mappingTable;
    private DefaultTableModel tableModel;
    private JButton saveMappingButton;
    private JButton loadMappingButton;
    private JButton clearAllButton;
    private JTextField targetPathField;
    
    public MappingPanel(DiscoveryPanel discoveryPanel) {
        this.discoveryPanel = discoveryPanel;
        initializeUI();
        initializeConfig();
        loadLastTargetDirectory();
    }
    
    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Top panel - Mapping builder
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(BorderFactory.createTitledBorder("Create Mapping"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Source file
        gbc.gridx = 0; gbc.gridy = 0;
        topPanel.add(new JLabel("Source File:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        sourceFileCombo = new JComboBox<>();
        sourceFileCombo.addActionListener(e -> updateSourceFields());
        topPanel.add(sourceFileCombo, gbc);
        
        // Source field
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        topPanel.add(new JLabel("Source Field:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        sourceFieldCombo = new JComboBox<>();
        topPanel.add(sourceFieldCombo, gbc);
        
        // Target file - WITH AUTOCOMPLETE
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        topPanel.add(new JLabel("Target File:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        targetFileField = new AutocompleteTextField(20);
        targetFileField.setSuggestions(UserPreferences.getTargetFileHistory());
        topPanel.add(targetFileField, gbc);
        
        // Target field - WITH AUTOCOMPLETE
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        topPanel.add(new JLabel("Target Field:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        targetFieldField = new AutocompleteTextField(20);
        targetFieldField.setSuggestions(UserPreferences.getTargetFieldHistory());
        topPanel.add(targetFieldField, gbc);
        
        // Add button
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        addMappingButton = new JButton("Add Mapping →");
        addMappingButton.addActionListener(e -> addMapping());
        topPanel.add(addMappingButton, gbc);
        
        // Center panel - Mapping table
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createTitledBorder("Current Mappings"));
        
        String[] columns = {"Source File", "Source Field", "Target File", "Target Field", "Transformation"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        mappingTable = new JTable(tableModel);
        mappingTable.setFont(new Font("Consolas", Font.PLAIN, 11));
        
        JScrollPane scrollPane = new JScrollPane(mappingTable);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton removeButton = new JButton("Remove Selected");
        removeButton.addActionListener(e -> removeSelectedMapping());
        clearAllButton = new JButton("Clear All");
        clearAllButton.addActionListener(e -> clearAllMappings());
        
        buttonPanel.add(removeButton);
        buttonPanel.add(clearAllButton);
        centerPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        // Bottom panel - Config management
        JPanel bottomPanel = new JPanel(new GridBagLayout());
        bottomPanel.setBorder(BorderFactory.createTitledBorder("Configuration"));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        gbc.gridx = 0; gbc.gridy = 0;
        bottomPanel.add(new JLabel("Target Directory:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        targetPathField = new JTextField();
        bottomPanel.add(targetPathField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        JPanel configButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        saveMappingButton = new JButton("Save Configuration");
        saveMappingButton.addActionListener(e -> saveConfiguration());
        loadMappingButton = new JButton("Load Configuration");
        loadMappingButton.addActionListener(e -> loadConfiguration());
        
        configButtonPanel.add(saveMappingButton);
        configButtonPanel.add(loadMappingButton);
        bottomPanel.add(configButtonPanel, gbc);
        
        // Add panels
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    private void initializeConfig() {
        currentConfig = new MappingConfig();
    }
    
    private void updateSourceFields() {
        sourceFieldCombo.removeAllItems();
        
        String selectedFile = (String) sourceFileCombo.getSelectedItem();
        if (selectedFile == null || !discoveryPanel.hasDiscoveredFiles()) {
            return;
        }
        
        CsvDiscovery.FileSchema schema = discoveryPanel.getDiscoveredSchemas().get(selectedFile);
        if (schema != null) {
            for (String header : schema.getHeaders()) {
                sourceFieldCombo.addItem(header);
            }
        }
    }
    
    public void refreshSourceFiles() {
        sourceFileCombo.removeAllItems();
        
        if (discoveryPanel.hasDiscoveredFiles()) {
            for (String fileName : discoveryPanel.getDiscoveredSchemas().keySet()) {
                sourceFileCombo.addItem(fileName);
            }
        }
    }
    
    private void addMapping() {
        String sourceFile = (String) sourceFileCombo.getSelectedItem();
        String sourceField = (String) sourceFieldCombo.getSelectedItem();
        String targetFile = targetFileField.getText().trim();
        String targetField = targetFieldField.getText().trim();
        
        if (sourceFile == null || sourceField == null || targetFile.isEmpty() || targetField.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please fill in all fields.",
                    "Incomplete Mapping",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Add to table
        tableModel.addRow(new Object[]{sourceFile, sourceField, targetFile, targetField, "direct"});
        
        // Save to history for autocomplete
        UserPreferences.addTargetFileName(targetFile);
        UserPreferences.addTargetFieldName(targetField);
        
        // Update autocomplete suggestions
        targetFileField.addSuggestion(targetFile);
        targetFieldField.addSuggestion(targetField);
        
        // Clear target fields for next entry
        targetFieldField.setText("");
        
        JOptionPane.showMessageDialog(this,
                "Mapping added successfully!",
                "Success",
                JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Refresh autocomplete data from DiscoveryPanel
     * Should be called when switching to this tab or after a scan
     */
    public void refreshAutocompleteData() {
        if (discoveryPanel != null && discoveryPanel.hasDiscoveredFiles()) {
            java.util.Set<String> discoveredFiles = discoveryPanel.getAllDiscoveredFileNames();
            java.util.Set<String> discoveredFields = discoveryPanel.getAllDiscoveredFields();
            
            // Add to autocomplete fields (this merges with existing history)
            for (String file : discoveredFiles) {
                targetFileField.addSuggestion(file);
            }
            
            for (String field : discoveredFields) {
                targetFieldField.addSuggestion(field);
            }
        }
    }

    private void removeSelectedMapping() {
        int selectedRow = mappingTable.getSelectedRow();
        if (selectedRow >= 0) {
            tableModel.removeRow(selectedRow);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Please select a mapping to remove.",
                    "No Selection",
                    JOptionPane.WARNING_MESSAGE);
        }
    }
    
    private void clearAllMappings() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to clear all mappings?",
                "Confirm Clear",
                JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            tableModel.setRowCount(0);
        }
    }
    
    private void saveConfiguration() {
        if (tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this,
                    "No mappings to save.",
                    "No Mappings",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Configuration");
        chooser.setSelectedFile(new File("filesync-config.json"));
        
        // Start from last config file directory if available
        String lastConfig = UserPreferences.getLastConfigFile();
        if (!lastConfig.isEmpty()) {
            chooser.setCurrentDirectory(new File(lastConfig).getParentFile());
        }
        
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            
            try {
                // Build config from table
                MappingConfig config = buildConfigFromTable();
                
                // Save target directory preference
                String targetDir = targetPathField.getText().trim();
                if (!targetDir.isEmpty()) {
                    UserPreferences.setLastTargetDirectory(targetDir);
                }
                
                // Save
                ConfigWriter writer = new ConfigWriter();
                writer.saveConfig(config, file.getAbsolutePath());
                
                // Save config file preference
                UserPreferences.setLastConfigFile(file.getAbsolutePath());
                
                JOptionPane.showMessageDialog(this,
                        "Configuration saved successfully!",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                        "Error saving configuration: " + e.getMessage(),
                        "Save Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void loadConfiguration() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load Configuration");
        
        // Start from last config file directory if available
        String lastConfig = UserPreferences.getLastConfigFile();
        if (!lastConfig.isEmpty()) {
            File lastFile = new File(lastConfig);
            chooser.setCurrentDirectory(lastFile.getParentFile());
            chooser.setSelectedFile(lastFile);
        }
        
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            
            try {
                ConfigLoader loader = new ConfigLoader();
                currentConfig = loader.loadConfig(file.getAbsolutePath());
                
                // Update UI
                loadConfigToTable();
                
                // Save config file preference
                UserPreferences.setLastConfigFile(file.getAbsolutePath());
                
                JOptionPane.showMessageDialog(this,
                        "Configuration loaded successfully!",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                        "Error loading configuration: " + e.getMessage(),
                        "Load Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private MappingConfig buildConfigFromTable() {
        MappingConfig config = new MappingConfig();
        config.getPaths().setSourceDirectory(discoveryPanel.getSourcePath());
        config.getPaths().setTargetDirectory(targetPathField.getText().trim());
        config.getMetadata().put("created", java.time.LocalDate.now().toString());
        
        // Group by source file -> target file
        java.util.Map<String, FileMapping> fileMappings = new java.util.HashMap<>();
        
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String sourceFile = (String) tableModel.getValueAt(i, 0);
            String sourceField = (String) tableModel.getValueAt(i, 1);
            String targetFile = (String) tableModel.getValueAt(i, 2);
            String targetField = (String) tableModel.getValueAt(i, 3);
            String transformation = (String) tableModel.getValueAt(i, 4);
            
            String key = sourceFile + " -> " + targetFile;
            FileMapping fileMapping = fileMappings.get(key);
            
            if (fileMapping == null) {
                fileMapping = new FileMapping(sourceFile, targetFile);
                fileMappings.put(key, fileMapping);
            }
            
            FieldMapping fieldMapping = new FieldMapping(sourceField, targetField, transformation);
            fileMapping.addFieldMapping(fieldMapping);
        }
        
        config.setFileMappings(new ArrayList<>(fileMappings.values()));
        return config;
    }
    
    private void loadConfigToTable() {
        tableModel.setRowCount(0);
        
        if (currentConfig != null) {
            targetPathField.setText(currentConfig.getPaths().getTargetDirectory());
            
            for (FileMapping fileMapping : currentConfig.getFileMappings()) {
                for (FieldMapping fieldMapping : fileMapping.getFieldMappings()) {
                    tableModel.addRow(new Object[]{
                            fileMapping.getSourceFile(),
                            fieldMapping.getSourceField(),
                            fileMapping.getTargetFile(),
                            fieldMapping.getTargetField(),
                            fieldMapping.getTransformation()
                    });
                }
            }
        }
    }
    
    public MappingConfig getCurrentConfig() {
        if (tableModel.getRowCount() > 0) {
            return buildConfigFromTable();
        }
        return currentConfig;
    }
    
    private void loadLastTargetDirectory() {
        String lastTargetDir = UserPreferences.getLastTargetDirectory();
        if (!lastTargetDir.isEmpty()) {
            targetPathField.setText(lastTargetDir);
        }
    }
}
