package com.raks.raksanalyzer.domain.model;

import com.raks.raksanalyzer.domain.enums.ExecutionMode;
import com.raks.raksanalyzer.domain.enums.ProjectType;

import java.util.List;

/**
 * Request object for project analysis.
 */
public class AnalysisRequest {
    private ProjectType projectTechnologyType;
    private ExecutionMode documentGenerationExecutionMode;
    private String environmentAnalysisScope;  // "ALL" or comma-separated list
    private String inputSourceType;  // "folder", "upload", "git"
    private String inputPath;  // Folder path, ZIP path, or Git URL
    private String gitBranch;  // Optional Git branch
    private List<String> selectedEnvironments;  // Parsed from environmentAnalysisScope
    
    // Getters and Setters
    public ProjectType getProjectTechnologyType() {
        return projectTechnologyType;
    }
    
    public void setProjectTechnologyType(ProjectType projectTechnologyType) {
        this.projectTechnologyType = projectTechnologyType;
    }
    
    public ExecutionMode getDocumentGenerationExecutionMode() {
        return documentGenerationExecutionMode;
    }
    
    public void setDocumentGenerationExecutionMode(ExecutionMode documentGenerationExecutionMode) {
        this.documentGenerationExecutionMode = documentGenerationExecutionMode;
    }
    
    public String getEnvironmentAnalysisScope() {
        return environmentAnalysisScope;
    }
    
    public void setEnvironmentAnalysisScope(String environmentAnalysisScope) {
        this.environmentAnalysisScope = environmentAnalysisScope;
    }
    
    public String getInputSourceType() {
        return inputSourceType;
    }
    
    public void setInputSourceType(String inputSourceType) {
        this.inputSourceType = inputSourceType;
    }
    
    public String getInputPath() {
        return inputPath;
    }
    
    public void setInputPath(String inputPath) {
        this.inputPath = inputPath;
    }
    
    public String getGitBranch() {
        return gitBranch;
    }
    
    public void setGitBranch(String gitBranch) {
        this.gitBranch = gitBranch;
    }
    
    public List<String> getSelectedEnvironments() {
        return selectedEnvironments;
    }
    
    public void setSelectedEnvironments(List<String> selectedEnvironments) {
        this.selectedEnvironments = selectedEnvironments;
    }
    
    @Override
    public String toString() {
        return "AnalysisRequest{" +
                "projectType=" + projectTechnologyType +
                ", executionMode=" + documentGenerationExecutionMode +
                ", environmentScope='" + environmentAnalysisScope + '\'' +
                ", inputSource='" + inputSourceType + '\'' +
                ", inputPath='" + inputPath + '\'' +
                '}';
    }
}
