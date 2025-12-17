package com.raks.raksanalyzer.domain.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a Global Connector Configuration (e.g., http:listener-config).
 */
public class ConnectorConfig {
    private String name;
    private String type; // The XML tag name
    private Map<String, String> attributes = new HashMap<>();
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public Map<String, String> getAttributes() { return attributes; }
    public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }
    public void addAttribute(String key, String value) { this.attributes.put(key, value); }
}
