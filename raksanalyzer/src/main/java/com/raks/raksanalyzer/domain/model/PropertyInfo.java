package com.raks.raksanalyzer.domain.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Property information with multi-environment values.
 */
public class PropertyInfo {
    private String key;
    private String description;
    private String sourcePath;  // Relative path to source properties file
    private Map<String, String> environmentValues = new HashMap<>();  // env -> value
    private String defaultValue;
    private boolean isSensitive;  // For passwords, tokens, etc.
    
    // Getters and Setters
    public String getKey() {
        return key;
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Map<String, String> getEnvironmentValues() {
        return environmentValues;
    }
    
    public void setEnvironmentValues(Map<String, String> environmentValues) {
        this.environmentValues = environmentValues;
    }
    
    public void addEnvironmentValue(String environment, String value) {
        this.environmentValues.put(environment, value);
    }
    
    public String getValueForEnvironment(String environment) {
        return environmentValues.getOrDefault(environment, defaultValue);
    }
    
    public String getDefaultValue() {
        return defaultValue;
    }
    
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
    
    public String getSourcePath() {
        return sourcePath;
    }
    
    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }
    
    // Alias for compatibility
    public String getSource() { return sourcePath; }
    public void setSource(String source) { this.sourcePath = source; }
    
    // Alias for compatibility with defaultValue
    public String getValue() { return defaultValue; }
    public void setValue(String value) { this.defaultValue = value; }
    
    public boolean isSensitive() {
        return isSensitive;
    }
    
    public void setSensitive(boolean sensitive) {
        isSensitive = sensitive;
    }
}
