package com.raks.filesync.core;

import com.raks.filesync.config.FieldMapping;
import com.raks.filesync.config.FileMapping;
import com.raks.filesync.config.MappingConfig;

import java.util.*;

/**
 * Validates mapping configuration against actual source files
 */
public class MappingValidator {
    private final CsvDiscovery csvDiscovery;
    private boolean caseSensitive;
    
    public MappingValidator() {
        this.csvDiscovery = new CsvDiscovery();
        this.caseSensitive = true; // Default: case sensitive
    }
    
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }
    
    public ValidationResult validate(MappingConfig config) {
        ValidationResult result = new ValidationResult();
        String sourceDir = config.getPaths().getSourceDirectory();
        
        // Discover all source files recursively
        Map<String, CsvDiscovery.FileSchema> schemas = csvDiscovery.discoverAllSchemas(sourceDir);
        
        // Build case-insensitive map if needed
        Map<String, CsvDiscovery.FileSchema> schemaMap = new HashMap<>();
        for (Map.Entry<String, CsvDiscovery.FileSchema> entry : schemas.entrySet()) {
            String key = caseSensitive ? entry.getKey() : entry.getKey().toLowerCase();
            schemaMap.put(key, entry.getValue());
        }
        
        // Validate each file mapping
        for (FileMapping fileMapping : config.getFileMappings()) {
            String sourceFile = fileMapping.getSourceFile();
            String lookupKey = caseSensitive ? sourceFile : sourceFile.toLowerCase();
            
            CsvDiscovery.FileSchema schema = schemaMap.get(lookupKey);
            if (schema == null) {
                result.addError("Source file not found: " + sourceFile);
                continue;
            }
            
            // Build field map
            Set<String> fieldSet = new HashSet<>();
            for (String field : schema.getHeaders()) {
                fieldSet.add(caseSensitive ? field : field.toLowerCase());
            }
            
            // Validate each field mapping
            for (FieldMapping fieldMapping : fileMapping.getFieldMappings()) {
                String sourceField = fieldMapping.getSourceField();
                String lookupField = caseSensitive ? sourceField : sourceField.toLowerCase();
                
                if (!fieldSet.contains(lookupField)) {
                    result.addError(String.format("Field '%s' not found in file '%s'", sourceField, sourceFile));
                }
            }
        }
        
        return result;
    }
    
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public List<String> getWarnings() {
            return warnings;
        }
        
        public String getSummary() {
            if (isValid()) {
                return "✓ Validation passed! All source files and fields are available.";
            }
            StringBuilder sb = new StringBuilder("✗ Validation failed:\n");
            for (String error : errors) {
                sb.append("  - ").append(error).append("\n");
            }
            return sb.toString();
        }
    }
}
