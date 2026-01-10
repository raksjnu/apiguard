package com.raks.filesync.gui;

import com.raks.filesync.core.CsvDiscovery;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.io.File;
import java.util.Map;

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
        
        // Center panel - File tree
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createTitledBorder("Discovered Files"));
        
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("No files scanned");
        treeModel = new DefaultTreeModel(root);
        fileTree = new JTree(treeModel);
        fileTree.setFont(new Font("Consolas", Font.PLAIN, 12));
        
        JScrollPane scrollPane = new JScrollPane(fileTree);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        
        // Add panels
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
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
        
        // Save the source directory
        UserPreferences.setLastSourceDirectory(sourcePath);
        
        // Scan directory
        CsvDiscovery discovery = new CsvDiscovery();
        discoveredSchemas = discovery.discoverAllSchemas(sourcePath);
        
        if (discoveredSchemas.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No CSV files found in the selected directory.",
                    "No Files Found",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // Update tree
        updateFileTree();
        
        JOptionPane.showMessageDialog(this,
                "Found " + discoveredSchemas.size() + " CSV file(s).",
                "Scan Complete",
                JOptionPane.INFORMATION_MESSAGE);
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
    
    private void loadLastSourceDirectory() {
        String lastDir = UserPreferences.getLastSourceDirectory();
        if (!lastDir.isEmpty()) {
            sourcePathField.setText(lastDir);
        }
    }
}
