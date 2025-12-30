package com.raks.raksanalyzer.domain.model;
import java.util.Map;
public class ResourceInfo {
    private String name;
    private String type; 
    private String relativePath;
    private Map<String, String> configuration;
    public ResourceInfo(String name, String type, String relativePath, Map<String, String> configuration) {
        this.name = name;
        this.type = type;
        this.relativePath = relativePath;
        this.configuration = configuration;
    }
    public String getName() {
        return name;
    }
    public String getType() {
        return type;
    }
    public String getRelativePath() {
        return relativePath;
    }
    public Map<String, String> getConfiguration() {
        return configuration;
    }
}
