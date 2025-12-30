package com.raks.raksanalyzer.domain.model;
import java.util.HashMap;
import java.util.Map;
public class PropertyInfo {
    private String key;
    private String description;
    private String sourcePath;  
    private Map<String, String> environmentValues = new HashMap<>();  
    private String defaultValue;
    private boolean isSensitive;  
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
    public String getSource() { return sourcePath; }
    public void setSource(String source) { this.sourcePath = source; }
    public String getValue() { return defaultValue; }
    public void setValue(String value) { this.defaultValue = value; }
    public boolean isSensitive() {
        return isSensitive;
    }
    public void setSensitive(boolean sensitive) {
        isSensitive = sensitive;
    }
}
