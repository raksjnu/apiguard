package com.raks.raksanalyzer.core.discovery;

import java.nio.file.Path;

/**
 * Represents a discovered Mule project.
 */
public class DiscoveredProject {
    private final Path projectPath;
    private final String projectName;
    
    public DiscoveredProject(Path projectPath, String projectName) {
        this.projectPath = projectPath;
        this.projectName = projectName;
    }
    
    public Path getProjectPath() {
        return projectPath;
    }
    
    public String getProjectName() {
        return projectName;
    }
    
    @Override
    public String toString() {
        return "DiscoveredProject{" +
                "projectPath=" + projectPath +
                ", projectName='" + projectName + '\'' +
                '}';
    }
}
