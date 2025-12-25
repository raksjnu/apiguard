package com.raks.raksanalyzer.domain.model;

import com.raks.raksanalyzer.domain.enums.ProjectType;

/**
 * Project information extracted during analysis.
 */
public class ProjectInfo {
    private String projectName;
    private ProjectType projectType;
    private String projectPath;
    private String version;
    private String description;
    
    // Technology-specific fields
    private String muleVersion;  // For Mule projects
    private String tibcoVersion;  // For Tibco projects
    private String springBootVersion;  // For Spring Boot projects
    private java.util.Properties properties = new java.util.Properties();  // For Tibco global variables
    
    // Getters and Setters
    public String getProjectName() {
        return projectName;
    }
    
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }
    
    public ProjectType getProjectType() {
        return projectType;
    }
    
    public void setProjectType(ProjectType projectType) {
        this.projectType = projectType;
    }
    
    public String getProjectPath() {
        return projectPath;
    }
    
    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getMuleVersion() {
        return muleVersion;
    }
    
    public void setMuleVersion(String muleVersion) {
        this.muleVersion = muleVersion;
    }
    
    public String getTibcoVersion() {
        return tibcoVersion;
    }
    
    public void setTibcoVersion(String tibcoVersion) {
        this.tibcoVersion = tibcoVersion;
    }
    
    public String getSpringBootVersion() {
        return springBootVersion;
    }
    
    public void setSpringBootVersion(String springBootVersion) {
        this.springBootVersion = springBootVersion;
    }
    
    public java.util.Properties getProperties() {
        return properties;
    }
    
    public void setProperties(java.util.Properties properties) {
        this.properties = properties;
    }
}
