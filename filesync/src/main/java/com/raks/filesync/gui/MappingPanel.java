package com.raks.filesync.gui;

import com.raks.filesync.config.*;
import com.raks.filesync.core.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Simplified mapping panel - loads, edits, and saves Mapping CSV
 */
public class MappingPanel extends JPanel {
    private final DiscoveryPanel discoveryPanel;
    private JTable mappingTable;
    private DefaultTableModel tableModel;
    private javax.swing.table.TableRowSorter<DefaultTableModel> sorter;
    private JTextField filterField;
    private JButton saveMappingButton;
    private JButton validateButton;
    private JTextArea validationResultArea;
    private JSplitPane splitPane;
    private File currentMappingFile;
    private List<FileMapping> loadedMappings;
    private String lastValidatedSourceDir;
    private String lastGeneratedOutputDir;
    
    public MappingPanel(DiscoveryPanel discoveryPanel) {
        this.discoveryPanel = discoveryPanel;
        initializeUI();
    }
    
    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Center panel - Table
        // Center panel - Table
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ThemeConfig.PRIMARY_COLOR, 3, true),
            "Field Mappings (Editable)",
            javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
            javax.swing.border.TitledBorder.DEFAULT_POSITION,
            new Font("Arial", Font.BOLD, 14),
            ThemeConfig.PRIMARY_COLOR));
        
        // Filter Panel
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterPanel.setBackground(ThemeConfig.PANEL_BACKGROUND);
        JLabel filterLabel = new JLabel("Filter Mappings:");
        filterLabel.setFont(ThemeConfig.getScaledFont("Arial", Font.BOLD, 12));
        filterField = new JTextField(20);
        filterField.setFont(ThemeConfig.getScaledFont("Arial", Font.PLAIN, 12));
        filterPanel.add(filterLabel);
        filterPanel.add(filterField);
        centerPanel.add(filterPanel, BorderLayout.NORTH);
        
        String[] columns = {"Seq", "Source File", "Source Field", "Target File", "Target Field", "Rule Type"};
        tableModel = new DefaultTableModel(columns, 0);
        mappingTable = new JTable(tableModel);
        
        // Setup Sorter
        sorter = new javax.swing.table.TableRowSorter<>(tableModel);
        mappingTable.setRowSorter(sorter);
        
        // Add Filter Listener
        filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { newFilter(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { newFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { newFilter(); }
        });
        mappingTable.setFont(ThemeConfig.getScaledFont("Arial", Font.PLAIN, 14));
        mappingTable.setRowHeight((int)(25 * ThemeConfig.getFontScale()));
        mappingTable.getTableHeader().setFont(ThemeConfig.getScaledFont("Arial", Font.BOLD, 12));
        mappingTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); // Enable horizontal scroll if needed
        
        JScrollPane tableScroll = new JScrollPane(mappingTable);
        centerPanel.add(tableScroll, BorderLayout.CENTER);
        
        // Bottom panel - Actions and validation
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        saveMappingButton = new StyledButton("💾 Save Mapping");
        saveMappingButton.addActionListener(e -> saveMappingToFile());
        buttonPanel.add(saveMappingButton);
        
        validateButton = new StyledButton("✓ Validate Mapping & Files");
        validateButton.addActionListener(e -> validateMapping());
        buttonPanel.add(validateButton);
        
        bottomPanel.add(buttonPanel, BorderLayout.NORTH);
        
        validationResultArea = new JTextArea();
        validationResultArea.setEditable(false);
        validationResultArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        validationResultArea.setBorder(BorderFactory.createLineBorder(ThemeConfig.PRIMARY_COLOR, 2, true));
        JScrollPane resultScroll = new JScrollPane(validationResultArea);
        bottomPanel.add(resultScroll, BorderLayout.CENTER);
        
        // Use SplitPane for resizability
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, centerPanel, bottomPanel);
        splitPane.setDividerLocation(400);
        splitPane.setResizeWeight(0.6);
        
        add(splitPane, BorderLayout.CENTER);
    }
    
    public void refreshMappingFiles() {
        loadSelectedMapping();
    }
    
    private void loadSelectedMapping() {
        currentMappingFile = discoveryPanel.getSelectedMappingFile();
        if (currentMappingFile == null) {
            tableModel.setRowCount(0);
            validationResultArea.setText("No mapping file selected in Discovery tab.");
            return;
        }
        
        try {
            CsvMappingParser parser = new CsvMappingParser();
            loadedMappings = parser.parseMappingCsv(currentMappingFile.getAbsolutePath());
            displayMappings();
            autoResizeColumns();
            validationResultArea.setText("Mapping loaded from:\n" + currentMappingFile.getAbsolutePath() + 
                "\nData Folder: " + discoveryPanel.getSelectedDataFolder().getAbsolutePath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error loading mapping: " + e.getMessage(), 
                "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void newFilter() {
        RowFilter<DefaultTableModel, Object> rf = null;
        try {
            rf = RowFilter.regexFilter("(?i)" + filterField.getText());
        } catch (java.util.regex.PatternSyntaxException e) {
            return;
        }
        sorter.setRowFilter(rf);
    }
    
    private void displayMappings() {
        tableModel.setRowCount(0);
        if (loadedMappings == null) return;
        
        for (FileMapping fileMapping : loadedMappings) {
            for (FieldMapping fieldMapping : fileMapping.getFieldMappings()) {
                tableModel.addRow(new Object[]{
                    fieldMapping.getSequenceNumber() != null ? fieldMapping.getSequenceNumber() : "",
                    fileMapping.getSourceFile(),
                    fieldMapping.getSourceField(),
                    fileMapping.getTargetFile(),
                    fieldMapping.getTargetField(),
                    fieldMapping.getRuleType() != null ? fieldMapping.getRuleType() : "Direct"
                });
            }
        }
    }
    
    private void saveMappingToFile() {
        if (currentMappingFile == null) {
            JOptionPane.showMessageDialog(this, "No mapping file loaded", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try (FileWriter writer = new FileWriter(currentMappingFile)) {
            writer.write("Sequence Number,Source File Name,Source Field Name,Target File Name,Target Field Name,Mapping Rule Type,Mapping Rule1,Mapping Rule2,Mapping Rule3,Mapping Rule4,Mapping Rule5\n");
            
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String seq = tableModel.getValueAt(i, 0).toString();
                String srcFile = tableModel.getValueAt(i, 1).toString();
                String srcField = tableModel.getValueAt(i, 2).toString();
                String tgtFile = tableModel.getValueAt(i, 3).toString();
                String tgtField = tableModel.getValueAt(i, 4).toString();
                String ruleType = tableModel.getValueAt(i, 5).toString();
                
                writer.write(String.format("%s,%s,%s,%s,%s,%s,,,,,\n", 
                    seq, srcFile, srcField, tgtFile, tgtField, ruleType));
            }
            
            JOptionPane.showMessageDialog(this, "Mapping saved successfully!", 
                "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error saving mapping: " + e.getMessage(), 
                "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void validateMapping() {
        if (loadedMappings == null || loadedMappings.isEmpty()) {
            validationResultArea.setText("No mapping loaded to validate.");
            return;
        }
        
        MappingConfig config = new MappingConfig();
        String sourceDir = discoveryPanel.getSelectedDataFolder().getAbsolutePath();
        config.getPaths().setSourceDirectory(sourceDir);
        config.setFileMappings(loadedMappings);
        
        MappingValidator validator = new MappingValidator();
        validator.setCaseSensitive(true);
        MappingValidator.ValidationResult result = validator.validate(config);
        
        // Build detailed statistics
        StringBuilder details = new StringBuilder();
        details.append("═══ VALIDATION RESULTS ═══\n\n");
        
        lastValidatedSourceDir = sourceDir;
        // Generate a fresh timestamped output folder name
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String ts = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        lastGeneratedOutputDir = new File(new File(sourceDir).getParentFile().getParentFile(), "Output/" + ts).getAbsolutePath();
        
        // Count unique source and target files
        Set<String> sourceFiles = new HashSet<>();
        Set<String> targetFiles = new HashSet<>();
        int totalFieldMappings = 0;
        
        for (FileMapping fm : loadedMappings) {
            sourceFiles.add(fm.getSourceFile());
            targetFiles.add(fm.getTargetFile());
            totalFieldMappings += fm.getFieldMappings().size();
        }
        
        details.append(String.format(">> Source Files Required: %d\n", sourceFiles.size()));
        for (String sf : sourceFiles) {
            details.append(String.format("   - %s\n", sf));
        }
        
        details.append(String.format("\n>> Target Files to Create: %d\n", targetFiles.size()));
        for (String tf : targetFiles) {
            details.append(String.format("   - %s\n", tf));
        }
        
        details.append(String.format("\n>> Total Field Mappings: %d\n", totalFieldMappings));
        details.append(String.format(">> Data Directory: %s\n", sourceDir));
        details.append(String.format(">> Output Directory (Target): %s\n\n", lastGeneratedOutputDir));
        
        if (result.isValid()) {
            details.append("SUCCESS: VALIDATION PASSED!\n");
            details.append("All source files and fields are available.\n");
            validationResultArea.setForeground(ThemeConfig.PRIMARY_COLOR);
        } else {
            details.append("ERROR: VALIDATION FAILED:\n");
            for (String error : result.getErrors()) {
                details.append(String.format("   ! %s\n", error));
            }
            validationResultArea.setForeground(Color.RED);
        }
        
        validationResultArea.setText(details.toString());
    }
    
    private void autoResizeColumns() {
        for (int column = 0; column < mappingTable.getColumnCount(); column++) {
            javax.swing.table.TableColumn tableColumn = mappingTable.getColumnModel().getColumn(column);
            int preferredWidth = tableColumn.getMinWidth();
            int maxWidth = 400; // Restrict as requested

            // Check header
            TableCellRenderer headerRenderer = mappingTable.getTableHeader().getDefaultRenderer();
            Component headerComp = headerRenderer.getTableCellRendererComponent(mappingTable, tableColumn.getHeaderValue(), false, false, 0, column);
            preferredWidth = Math.max(preferredWidth, headerComp.getPreferredSize().width + 10);

            // Check rows
            for (int row = 0; row < mappingTable.getRowCount(); row++) {
                TableCellRenderer cellRenderer = mappingTable.getCellRenderer(row, column);
                Component c = mappingTable.prepareRenderer(cellRenderer, row, column);
                int width = c.getPreferredSize().width + mappingTable.getIntercellSpacing().width + 10;
                preferredWidth = Math.max(preferredWidth, width);
                
                if (preferredWidth >= maxWidth) {
                    preferredWidth = maxWidth;
                    break;
                }
            }
            tableColumn.setPreferredWidth(preferredWidth);
        }
    }
    
    public String getLastGeneratedOutputDir() {
        return lastGeneratedOutputDir;
    }
    
    public MappingConfig getCurrentConfig() {
        // Build config from table
        List<FileMapping> mappings = new java.util.ArrayList<>();
        if (loadedMappings != null) {
            mappings.addAll(loadedMappings);
        }
        
        MappingConfig config = new MappingConfig();
        config.setVersion("1.0");
        
        // Set paths
        MappingConfig.PathConfig paths = new MappingConfig.PathConfig();
        if (discoveryPanel.getSelectedDataFolder() != null) {
             paths.setSourceDirectory(discoveryPanel.getSelectedDataFolder().getAbsolutePath());
        }
        // Use the validated/generated output dir if available
        if (lastGeneratedOutputDir != null) {
            paths.setTargetDirectory(lastGeneratedOutputDir);
        } else if (discoveryPanel.getSelectedDataFolder() != null) {
             // Default to sibling "Output" if not yet validated
             File sourceDir = discoveryPanel.getSelectedDataFolder();
             if (sourceDir.getParentFile() != null) {
                 paths.setTargetDirectory(new File(sourceDir.getParentFile(), "Output").getAbsolutePath());
             }
        }
        
        config.setPaths(paths);
        config.setFileMappings(mappings);
        return config;
    }

    public File getCurrentMappingFile() {
        return currentMappingFile;
    }
}
