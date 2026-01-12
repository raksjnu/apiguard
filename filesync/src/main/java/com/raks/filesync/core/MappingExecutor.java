package com.raks.filesync.core;

import com.raks.filesync.config.FieldMapping;
import com.raks.filesync.config.FileMapping;
import com.raks.filesync.config.MappingConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Executes CSV transformations based on mapping configuration
 */
public class MappingExecutor {
    private final CsvReader csvReader;
    private final CsvWriter csvWriter;
    private final TransformationEngine transformationEngine;
    private final Set<String> createdFiles; // Track files created in this execution session
    
    public MappingExecutor() {
        this.csvReader = new CsvReader();
        this.csvWriter = new CsvWriter();
        this.transformationEngine = new TransformationEngine();
        this.createdFiles = new HashSet<>();
    }
    
    /**
     * Execute all mappings in the configuration
     */
    public ExecutionResult execute(MappingConfig config) {
        ExecutionResult result = new ExecutionResult();
        
        // Ensure target directory exists
        try {
            Files.createDirectories(Paths.get(config.getPaths().getTargetDirectory()));
        } catch (IOException e) {
            result.addError("Failed to create target directory: " + e.getMessage());
            return result;
        }
        
        // Process each file mapping
        for (FileMapping fileMapping : config.getFileMappings()) {
            try {
                processFileMapping(config, fileMapping, result);
            } catch (Exception e) {
                result.addError("Error processing " + fileMapping.getSourceFile() + ": " + e.getMessage());
            }
        }
        
        return result;
    }
    
    /**
     * Process a single file mapping
     */
    private void processFileMapping(MappingConfig config, FileMapping fileMapping, ExecutionResult result) throws IOException {
        String sourceFilePath = Paths.get(config.getPaths().getSourceDirectory(), fileMapping.getSourceFile()).toString();
        String targetFilePath = Paths.get(config.getPaths().getTargetDirectory(), fileMapping.getTargetFile()).toString();
        
        // Check if source file exists
        if (!new File(sourceFilePath).exists()) {
            result.addWarning("Source file not found: " + sourceFilePath);
            return;
        }
        
        // Read source CSV
        List<Map<String, String>> sourceRows = csvReader.readCsv(sourceFilePath);
        
        if (sourceRows.isEmpty()) {
            result.addWarning("Source file is empty: " + sourceFilePath);
            return;
        }
        
        // Build current mapping target headers
        List<String> currentTargetHeaders = new ArrayList<>();
        for (FieldMapping fieldMapping : fileMapping.getFieldMappings()) {
            currentTargetHeaders.add(fieldMapping.getTargetField());
        }
        
        // Transform current rows
        List<Map<String, String>> currentTargetRows = new ArrayList<>();
        for (Map<String, String> sourceRow : sourceRows) {
            Map<String, String> targetRow = transformRow(sourceRow, fileMapping.getFieldMappings());
            currentTargetRows.add(targetRow);
        }
        
        // MULTI-SOURCE MERGE LOGIC
        // If we have already written to this file in this session, we must merge
        if (createdFiles.contains(targetFilePath)) {
            // Read Existing Data
            List<Map<String, String>> existingRows = csvReader.readCsv(targetFilePath);
            
            // Should not happen if we just wrote it, but handling empty case safe-guard
            if (existingRows.isEmpty()) {
                 // Fallback: Overwrite if empty (headers lost)
                 csvWriter.writeCsv(targetFilePath, currentTargetHeaders, currentTargetRows);
                 result.addSuccess("Processed (Overwrite Empty) " + fileMapping.getSourceFile() + " -> " + fileMapping.getTargetFile());
                 return;
            }
            
            // Merge Headers (Existing + New unique)
            Set<String> mergedHeaderSet = new LinkedHashSet<>(existingRows.get(0).keySet());
            mergedHeaderSet.addAll(currentTargetHeaders);
            List<String> finalHeaders = new ArrayList<>(mergedHeaderSet);
            
            // Merge Rows (Index-based)
            List<Map<String, String>> finalRows = new ArrayList<>();
            int maxRows = Math.max(existingRows.size(), currentTargetRows.size());
            
            for (int i = 0; i < maxRows; i++) {
                Map<String, String> mergedRow = new LinkedHashMap<>();
                
                // Add existing data (if available)
                if (i < existingRows.size()) {
                    mergedRow.putAll(existingRows.get(i));
                }
                
                // Add new data (if available) - This effectively appends columns
                if (i < currentTargetRows.size()) {
                    mergedRow.putAll(currentTargetRows.get(i));
                }
                
                finalRows.add(mergedRow);
            }
            
            // Write Merged Result
            csvWriter.writeCsv(targetFilePath, finalHeaders, finalRows);
            result.addSuccess("Processed (Merged) " + fileMapping.getSourceFile() + " -> " + fileMapping.getTargetFile() + 
                             " (Total " + finalRows.size() + " rows)");
            
        } else {
            // first time writing this file
            csvWriter.writeCsv(targetFilePath, currentTargetHeaders, currentTargetRows);
            createdFiles.add(targetFilePath);
            
            result.addSuccess("Processed " + fileMapping.getSourceFile() + " -> " + fileMapping.getTargetFile() + 
                             " (" + currentTargetRows.size() + " rows)");
        }
    }
    
    /**
     * Transform a single row based on field mappings
     */
    private Map<String, String> transformRow(Map<String, String> sourceRow, List<FieldMapping> fieldMappings) {
        Map<String, String> targetRow = new LinkedHashMap<>();
        
        for (FieldMapping fieldMapping : fieldMappings) {
            String sourceValue = sourceRow.getOrDefault(fieldMapping.getSourceField(), "");
            String transformedValue = transformationEngine.transform(sourceValue, fieldMapping.getTransformation());
            targetRow.put(fieldMapping.getTargetField(), transformedValue);
        }
        
        return targetRow;
    }
    
    /**
     * Result of execution
     */
    public static class ExecutionResult {
        private final List<String> successes = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        
        public void addSuccess(String message) {
            successes.add(message);
        }
        
        public void addWarning(String message) {
            warnings.add(message);
        }
        
        public void addError(String message) {
            errors.add(message);
        }
        
        public List<String> getSuccesses() {
            return successes;
        }
        
        public List<String> getWarnings() {
            return warnings;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("Execution Summary:\n");
            sb.append("  Successes: ").append(successes.size()).append("\n");
            sb.append("  Warnings: ").append(warnings.size()).append("\n");
            sb.append("  Errors: ").append(errors.size()).append("\n");
            return sb.toString();
        }
    }
}
