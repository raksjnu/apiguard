package com.raks.filesync.gui;

import com.raks.filesync.core.CsvDiscovery;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.io.File;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * Discovery panel for scanning source CSV files
 */
public class DiscoveryPanel extends JPanel {
    private JTextField sourcePathField;
    private JButton browseButton;
    private JButton scanButton;
    private JTree fileTree;
    private DefaultTreeModel treeModel;
    private Map<String, CsvDiscovery.FileSchema> discoveredSchemas;
    private JComboBox<String> dataFolderCombo;
    private JComboBox<String> mappingFileCombo;
    private File inputFolder;
    private List<File> discoveredMappingFiles;
    
    public DiscoveryPanel() {
        initializeUI();
        loadLastSourceDirectory();
    }
    
    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Top panel - Directory selection
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(BorderFactory.createTitledBorder("Source Directory"));
        
        sourcePathField = new JTextField();
        sourcePathField.setPreferredSize(new Dimension(400, 30));
        
        browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browseDirectory());
        
        scanButton = new JButton("Scan Directory");
        scanButton.addActionListener(e -> scanDirectory());
        scanButton.setPreferredSize(new Dimension(150, 30));
        
        JPanel pathPanel = new JPanel(new BorderLayout(5, 5));
        pathPanel.add(sourcePathField, BorderLayout.CENTER);
        pathPanel.add(browseButton, BorderLayout.EAST);
        
        topPanel.add(pathPanel, BorderLayout.CENTER);
        topPanel.add(scanButton, BorderLayout.EAST);
        
        // Selection Picker Panel (Hidden initially)
        JPanel pickerPanel = new JPanel(new GridBagLayout());
        pickerPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ThemeConfig.PRIMARY_COLOR, 1, true),
            "Configuration selection (Pick and proceed)",
            javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
            javax.swing.border.TitledBorder.DEFAULT_POSITION,
            new Font("Arial", Font.BOLD, 12),
            ThemeConfig.PRIMARY_COLOR));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        pickerPanel.add(new JLabel("Data Folder:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        dataFolderCombo = new JComboBox<>();
        pickerPanel.add(dataFolderCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        pickerPanel.add(new JLabel("Mapping File:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        mappingFileCombo = new JComboBox<>();
        pickerPanel.add(mappingFileCombo, gbc);

        JPanel pickerContainer = new JPanel(new BorderLayout());
        pickerContainer.add(pickerPanel, BorderLayout.NORTH);

        // Center panel - File tree
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(BorderFactory.createTitledBorder("Discovered Files & Schema"));
        
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("No files scanned");
        treeModel = new DefaultTreeModel(root);
        fileTree = new JTree(treeModel);
        fileTree.setFont(new Font("Consolas", Font.PLAIN, 12));
        
        JScrollPane scrollPane = new JScrollPane(fileTree);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        
        // Combine Picker and Tree
        JPanel mainCenter = new JPanel(new BorderLayout(5, 5));
        mainCenter.add(pickerContainer, BorderLayout.NORTH);
        mainCenter.add(centerPanel, BorderLayout.CENTER);

        // Add panels
        add(topPanel, BorderLayout.NORTH);
        add(mainCenter, BorderLayout.CENTER);
    }
    
    private void browseDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        // Start from last used directory if available
        String lastDir = UserPreferences.getLastSourceDirectory();
        if (!lastDir.isEmpty()) {
            chooser.setCurrentDirectory(new File(lastDir));
        }
        
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedDir = chooser.getSelectedFile();
            String path = selectedDir.getAbsolutePath();
            sourcePathField.setText(path);
            UserPreferences.setLastSourceDirectory(path);
        }
    }
    
    private void scanDirectory() {
        String sourcePath = sourcePathField.getText().trim();
        
        if (sourcePath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please select a source directory first.",
                    "No Directory Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        File sourceDir = new File(sourcePath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            JOptionPane.showMessageDialog(this,
                    "Invalid directory: " + sourcePath,
                    "Invalid Directory",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        UserPreferences.setLastSourceDirectory(sourcePath);
        
        // Look for Input folder
        inputFolder = new File(sourceDir, "Input");
        String scanPath = inputFolder.exists() ? inputFolder.getAbsolutePath() : sourcePath;
        
        // Scan CSV files
        CsvDiscovery discovery = new CsvDiscovery();
        discoveredSchemas = discovery.discoverAllSchemas(scanPath);
        
        // Look for Mapping CSV files
        com.raks.filesync.core.CsvMappingParser parser = new com.raks.filesync.core.CsvMappingParser();
        discoveredMappingFiles = parser.findMappingFiles(sourcePath);
        
        if (discoveredSchemas.isEmpty() && discoveredMappingFiles.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No CSV or Mapping files found.",
                    "No Files Found",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        updateFileTree();
        updatePickers();
        
        String msg = String.format("Found %d CSV file(s) and %d Mapping file(s).", 
                discoveredSchemas.size(), discoveredMappingFiles.size());
        JOptionPane.showMessageDialog(this, msg, "Scan Complete", JOptionPane.INFORMATION_MESSAGE);
    }

    private void updatePickers() {
        dataFolderCombo.removeAllItems();
        mappingFileCombo.removeAllItems();

        // Populate Data Folders
        if (inputFolder != null && inputFolder.exists()) {
            File[] subfolders = inputFolder.listFiles(File::isDirectory);
            if (subfolders != null && subfolders.length > 0) {
                for (File f : subfolders) dataFolderCombo.addItem(f.getName());
            } else {
                dataFolderCombo.addItem("Input (root)");
            }
        } else {
            dataFolderCombo.addItem("Source (root)");
        }

        // Populate Mapping Files
        if (discoveredMappingFiles != null && !discoveredMappingFiles.isEmpty()) {
            for (File f : discoveredMappingFiles) mappingFileCombo.addItem(f.getName());
        } else {
            mappingFileCombo.addItem("No mapping files found");
        }
    }

    public File getSelectedDataFolder() {
        String selectedName = (String) dataFolderCombo.getSelectedItem();
        if (selectedName == null) return inputFolder != null ? inputFolder : new File(getSourcePath());
        
        if (selectedName.contains("(root)")) return inputFolder != null ? inputFolder : new File(getSourcePath());
        
        return new File(inputFolder, selectedName);
    }

    public File getSelectedMappingFile() {
        String selectedName = (String) mappingFileCombo.getSelectedItem();
        if (selectedName == null || selectedName.startsWith("No mapping")) return null;
        
        return discoveredMappingFiles.stream()
            .filter(f -> f.getName().equals(selectedName))
            .findFirst()
            .orElse(null);
    }
    
    private void updateFileTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Source Files (" + discoveredSchemas.size() + ")");
        
        for (CsvDiscovery.FileSchema schema : discoveredSchemas.values()) {
            DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode("📄 " + schema.getFileName());
            
            for (String header : schema.getHeaders()) {
                DefaultMutableTreeNode fieldNode = new DefaultMutableTreeNode("  ├─ " + header);
                fileNode.add(fieldNode);
            }
            
            root.add(fileNode);
        }
        
        treeModel.setRoot(root);
        
        // Expand all nodes
        for (int i = 0; i < fileTree.getRowCount(); i++) {
            fileTree.expandRow(i);
        }
    }
    
    public String getSourcePath() {
        return sourcePathField.getText().trim();
    }
    
    public Map<String, CsvDiscovery.FileSchema> getDiscoveredSchemas() {
        return discoveredSchemas;
    }
    
    public boolean hasDiscoveredFiles() {
        return discoveredSchemas != null && !discoveredSchemas.isEmpty();
    }
    
    /**
     * Get all discovered file names (just the names)
     */
    public java.util.Set<String> getAllDiscoveredFileNames() {
        java.util.Set<String> fileNames = new java.util.HashSet<>();
        if (discoveredSchemas != null) {
            for (CsvDiscovery.FileSchema schema : discoveredSchemas.values()) {
                fileNames.add(schema.getFileName());
            }
        }
        return fileNames;
    }
    
    /**
     * Get all discovered unique field names (headers) from all files
     */
    public java.util.Set<String> getAllDiscoveredFields() {
        java.util.Set<String> fields = new java.util.HashSet<>();
        if (discoveredSchemas != null) {
            for (CsvDiscovery.FileSchema schema : discoveredSchemas.values()) {
                fields.addAll(schema.getHeaders());
            }
        }
        return fields;
    }
    
    private void loadLastSourceDirectory() {
        String lastDir = UserPreferences.getLastSourceDirectory();
        if (!lastDir.isEmpty()) {
            sourcePathField.setText(lastDir);
        }
    }
    
    public List<File> getDiscoveredMappingFiles() {
        return discoveredMappingFiles != null ? discoveredMappingFiles : new ArrayList<>();
    }
    
    public File getInputFolder() {
        return inputFolder;
    }
}
