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
    
    public MappingExecutor() {
        this.csvReader = new CsvReader();
        this.csvWriter = new CsvWriter();
        this.transformationEngine = new TransformationEngine();
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
        
        // Build target headers from field mappings
        List<String> targetHeaders = new ArrayList<>();
        for (FieldMapping fieldMapping : fileMapping.getFieldMappings()) {
            targetHeaders.add(fieldMapping.getTargetField());
        }
        
        // Transform rows
        List<Map<String, String>> targetRows = new ArrayList<>();
        for (Map<String, String> sourceRow : sourceRows) {
            Map<String, String> targetRow = transformRow(sourceRow, fileMapping.getFieldMappings());
            targetRows.add(targetRow);
        }
        
        // Write target CSV
        csvWriter.writeCsv(targetFilePath, targetHeaders, targetRows);
        
        result.addSuccess("Processed " + fileMapping.getSourceFile() + " -> " + fileMapping.getTargetFile() + 
                         " (" + targetRows.size() + " rows)");
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
