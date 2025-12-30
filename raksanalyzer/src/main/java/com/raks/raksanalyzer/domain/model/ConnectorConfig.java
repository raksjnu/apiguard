package com.raks.raksanalyzer.domain.model;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class ConnectorConfig {
    private String name;
    private String type; 
    private Map<String, String> attributes = new HashMap<>();
    private List<ComponentInfo> nestedComponents = new ArrayList<>();
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Map<String, String> getAttributes() { return attributes; }
    public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }
    public void addAttribute(String key, String value) { this.attributes.put(key, value); }
    public List<ComponentInfo> getNestedComponents() { return nestedComponents; }
    public void setNestedComponents(List<ComponentInfo> nestedComponents) { this.nestedComponents = nestedComponents; }
    public void addNestedComponent(ComponentInfo component) { this.nestedComponents.add(component); }
}
