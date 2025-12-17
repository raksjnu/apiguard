package com.raks.raksanalyzer.domain.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Component information (connectors, transformers, activities).
 */
public class ComponentInfo {
    private String name;
    private String type;  // "http:request", "db:select", "logger", etc.
    private String category;  // "connector", "transformer", "router", etc.
    private Map<String, String> attributes = new HashMap<>();
    private String configRef;  // Reference to global configuration
    
    // Getters and Setters
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public Map<String, String> getAttributes() {
        return attributes;
    }
    
    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }
    
    public void addAttribute(String key, String value) {
        this.attributes.put(key, value);
    }
    
    public String getConfigRef() {
        return configRef;
    }
    
    public void setConfigRef(String configRef) {
        this.configRef = configRef;
    }
}
