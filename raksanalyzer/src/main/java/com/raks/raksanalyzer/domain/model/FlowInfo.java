package com.raks.raksanalyzer.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Flow or Process information (Mule flows, Tibco processes).
 */
public class FlowInfo {
    private String name;
    private String type;  // "flow", "sub-flow", "process", etc.
    private String fileName;
    private String description;
    private List<ComponentInfo> components = new ArrayList<>();
    
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
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public List<ComponentInfo> getComponents() {
        return components;
    }
    
    public void setComponents(List<ComponentInfo> components) {
        this.components = components;
    }
    
    public void addComponent(ComponentInfo component) {
        this.components.add(component);
    }
}
