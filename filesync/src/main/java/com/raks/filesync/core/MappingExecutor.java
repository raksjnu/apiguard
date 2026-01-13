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
    /**
     * Execute all mappings with auto-generated timestamp
     */
    public ExecutionResult execute(MappingConfig config) {
        return execute(config, null);
    }

    /**
     * Execute all mappings in the configuration with specific timestamp
     */
    public ExecutionResult execute(MappingConfig config, String timestampOverride) {
        ExecutionResult result = new ExecutionResult();
        
        // Create timestamped output directory
        String timestamp = (timestampOverride != null) ? timestampOverride : 
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
            
        String outputDir = Paths.get(config.getPaths().getTargetDirectory(), "Output", timestamp).toString();
        String errorDir = Paths.get(config.getPaths().getTargetDirectory(), "Error", timestamp).toString();
        
        try {
            Files.createDirectories(Paths.get(outputDir));
        } catch (IOException e) {
            result.addError("Failed to create output directory: " + e.getMessage());
            return result;
        }
        
        // Sort by sequence number
        List<FileMapping> sortedMappings = config.getFileMappings().stream()
                .sorted(Comparator.comparing(fm -> fm.getSequenceNumber() != null ? fm.getSequenceNumber() : Integer.MAX_VALUE))
                .collect(java.util.stream.Collectors.toList());
        
        // Process each file mapping
        for (FileMapping fileMapping : sortedMappings) {
            try {
                processFileMapping(config, fileMapping, outputDir, result);
            } catch (Exception e) {
                handleError(config, fileMapping, errorDir, e, result);
            }
        }
        
        return result;
    }
    
    /**
     * Process a single file mapping
     */
    private void processFileMapping(MappingConfig config, FileMapping fileMapping, String outputDir, ExecutionResult result) throws IOException {
        // Discover all source files to get actual paths
        CsvDiscovery discovery = new CsvDiscovery();
        Map<String, CsvDiscovery.FileSchema> schemas = discovery.discoverAllSchemas(config.getPaths().getSourceDirectory());
        
        // Find the actual file path
        CsvDiscovery.FileSchema schema = schemas.get(fileMapping.getSourceFile());
        if (schema == null) {
            result.addWarning("Source file not found: " + fileMapping.getSourceFile());
            return;
        }
        
        String sourceFilePath = schema.getFilePath();
        String targetFilePath = Paths.get(outputDir, fileMapping.getTargetFile()).toString();
        
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
                 result.addSuccess(String.format("Processed (Overwrite): %s -> %s | Records: %d | Size: %s", 
                      new File(sourceFilePath).getName(), new File(targetFilePath).getName(), currentTargetRows.size(), formatFileSize(new File(targetFilePath).length())));
                 result.addTargetRows(fileMapping.getTargetFile(), currentTargetRows.size());
                 result.addSourceRows(fileMapping.getSourceFile(), sourceRows.size());
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
            result.addSuccess(String.format("Merged: %s Into -> %s | Final Records: %d | Size: %s", 
                new File(sourceFilePath).getName(), new File(targetFilePath).getName(), finalRows.size(), formatFileSize(new File(targetFilePath).length())));
            result.addTargetRows(fileMapping.getTargetFile(), finalRows.size());
            result.addSourceRows(fileMapping.getSourceFile(), sourceRows.size());
            
        } else {
            // first time writing this file
            csvWriter.writeCsv(targetFilePath, currentTargetHeaders, currentTargetRows);
            createdFiles.add(targetFilePath);
            
            result.addSuccess(String.format("Processed: %s -> %s | Records: %d | Size: %s", 
                new File(sourceFilePath).getName(), new File(targetFilePath).getName(), currentTargetRows.size(), formatFileSize(new File(targetFilePath).length())));
            result.addTargetRows(fileMapping.getTargetFile(), currentTargetRows.size());
            result.addSourceRows(fileMapping.getSourceFile(), sourceRows.size());
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
     * Handle errors by moving file to Error folder and logging
     */
    private void handleError(MappingConfig config, FileMapping fileMapping, String errorDir, Exception e, ExecutionResult result) {
        try {
            Files.createDirectories(Paths.get(errorDir));
            
            // Copy source file to error folder
            String sourceFilePath = Paths.get(config.getPaths().getSourceDirectory(), fileMapping.getSourceFile()).toString();
            File sourceFile = new File(sourceFilePath);
            if (sourceFile.exists()) {
                String errorFilePath = Paths.get(errorDir, fileMapping.getSourceFile()).toString();
                Files.copy(sourceFile.toPath(), Paths.get(errorFilePath), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Write error log
            String logPath = Paths.get(errorDir, "error_log.txt").toString();
            String logEntry = String.format("[%s] Error processing %s -> %s: %s%n",
                    java.time.LocalDateTime.now(), fileMapping.getSourceFile(), fileMapping.getTargetFile(), e.getMessage());
            Files.write(Paths.get(logPath), logEntry.getBytes(), 
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            
            result.addError("Error processing " + fileMapping.getSourceFile() + ": " + e.getMessage());
        } catch (IOException ioEx) {
            result.addError("Failed to handle error for " + fileMapping.getSourceFile() + ": " + ioEx.getMessage());
        }
    }

    /**
     * Helper to format file size smartly
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return "< 1 KB";
        } else if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }
    
    /**
     * Result of execution
     */
    public static class ExecutionResult {
        private final List<String> successes = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        
        private final Map<String, Integer> sourceRowCounts = new LinkedHashMap<>();
        private final Map<String, Integer> targetRowCounts = new LinkedHashMap<>();
        
        public void addSuccess(String message) {
            successes.add(message);
        }
        
        public void addWarning(String message) {
            warnings.add(message);
        }
        
        public void addError(String message) {
            errors.add(message);
        }
        
        public void addSourceRows(String fileName, int count) {
            sourceRowCounts.put(fileName, count);
        }
        
        public void addTargetRows(String fileName, int count) {
            targetRowCounts.put(fileName, count);
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
            sb.append("═══ EXECUTION AUDIT ═══\n");
            sb.append(String.format("Files Processed: %d\n", successes.size()));
            sb.append(String.format("Warnings Found: %d\n", warnings.size()));
            sb.append(String.format("Errors Encountered: %d\n", errors.size()));
            
            sb.append("\n📈 RECORD COUNTS (Audit Support):\n");
            sb.append("   Source Records:\n");
            for (Map.Entry<String, Integer> entry : sourceRowCounts.entrySet()) {
                sb.append(String.format("     • %s: %d\n", entry.getKey(), entry.getValue()));
            }
            sb.append("   Target Records:\n");
            for (Map.Entry<String, Integer> entry : targetRowCounts.entrySet()) {
                sb.append(String.format("     • %s: %d\n", entry.getKey(), entry.getValue()));
            }
            
            return sb.toString();
        }
    }
}
