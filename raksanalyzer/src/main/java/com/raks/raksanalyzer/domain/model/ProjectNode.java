package com.raks.raksanalyzer.domain.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a node in the project file structure (File or Directory).
 * Used for "IDE View" reporting.
 */
public class ProjectNode {
    private String name;
    private String absolutePath;
    private String relativePath;
    private NodeType type;
    private List<ProjectNode> children = new ArrayList<>();
    
    // Metadata map to store analysis results for this node
    // e.g. "pomInfo" -> PomInfo object
    // "muleFlows" -> List<FlowInfo>
    // "properties" -> List<PropertyInfo>
    private Map<String, Object> metadata = new HashMap<>();

    public enum NodeType {
        DIRECTORY,
        FILE
    }
    
    public ProjectNode(String name, String absolutePath, String relativePath, NodeType type) {
        this.name = name;
        this.absolutePath = absolutePath;
        this.relativePath = relativePath;
        this.type = type;
    }

    // Getters and Setters
    public String getName() { return name; }
    public String getAbsolutePath() { return absolutePath; }
    public String getRelativePath() { return relativePath; }
    public NodeType getType() { return type; }
    
    public List<ProjectNode> getChildren() { return children; }
    public void addChild(ProjectNode child) { this.children.add(child); }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void addMetadata(String key, Object value) { this.metadata.put(key, value); }
    public Object getMetadata(String key) { return this.metadata.get(key); }
}
