package com.raks.raksanalyzer.domain.model;

import com.raks.raksanalyzer.domain.enums.ExecutionMode;
import com.raks.raksanalyzer.domain.enums.ProjectType;
import com.raks.raksanalyzer.model.OutputFormatConfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Request object for project analysis.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisRequest {
    private ProjectType projectTechnologyType;
    private ExecutionMode documentGenerationExecutionMode;
    private String environmentAnalysisScope;  // "ALL" or comma-separated list
    private String inputSourceType;  // "folder", "upload", "git"
    private String inputPath;  // Folder path, ZIP path, or Git URL
    private String gitBranch;  // Optional Git branch
    private List<String> selectedEnvironments;  // Parsed from environmentAnalysisScope
    private OutputFormatConfig outputFormatConfig;  // Output format preferences
    private String analysisId;
    private String uploadId;  // For uploaded files (ZIP/JAR) - used for cleanup
    private String configFilePath; // Custom configuration file path (e.g. for TIBCO global variables)
    
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
    
    public OutputFormatConfig getOutputFormatConfig() {
        return outputFormatConfig;
    }
    
    public void setOutputFormatConfig(OutputFormatConfig outputFormatConfig) {
        this.outputFormatConfig = outputFormatConfig;
    }
    
    public String getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
    }
    
    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }
    
    public String getConfigFilePath() {
        return configFilePath;
    }

    public void setConfigFilePath(String configFilePath) {
        this.configFilePath = configFilePath;
    }
    
    // Flat fields for JSON compatibility
    private Boolean generatePdf;
    private Boolean generateWord;
    private Boolean generateExcel;

    public void setGeneratePdf(Boolean generatePdf) {
        this.generatePdf = generatePdf;
        if (outputFormatConfig == null) outputFormatConfig = new OutputFormatConfig();
        outputFormatConfig.setPdfEnabled(generatePdf != null ? generatePdf : false);
    }
    
    public void setGenerateWord(Boolean generateWord) {
        this.generateWord = generateWord;
        if (outputFormatConfig == null) outputFormatConfig = new OutputFormatConfig();
        outputFormatConfig.setWordEnabled(generateWord != null ? generateWord : false);
    }
    
    public void setGenerateExcel(Boolean generateExcel) {
        this.generateExcel = generateExcel;
        if (outputFormatConfig == null) outputFormatConfig = new OutputFormatConfig();
        outputFormatConfig.setExcelEnabled(generateExcel != null ? generateExcel : false);
    }

    @Override
    public String toString() {
        return "AnalysisRequest{" +
                "projectType=" + projectTechnologyType +
                ", executionMode=" + documentGenerationExecutionMode +
                ", environmentScope='" + environmentAnalysisScope + '\'' +
                ", inputSource='" + inputSourceType + '\'' +
                ", inputPath='" + inputPath + '\'' +
                ", outputFormatConfig=" + outputFormatConfig +
                '}';
    }
}
