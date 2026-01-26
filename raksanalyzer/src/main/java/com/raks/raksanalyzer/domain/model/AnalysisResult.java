package com.raks.raksanalyzer.domain.model;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class AnalysisResult {
    private String analysisId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean success;
    private String errorMessage;
    private ProjectInfo projectInfo;
    private ProjectNode projectStructure;
    private List<FlowInfo> flows = new ArrayList<>();
    private List<ComponentInfo> components = new ArrayList<>();
    private List<PropertyInfo> properties = new ArrayList<>();
    private List<String> propertyFiles = new ArrayList<>();  
    private List<ResourceInfo> resources = new ArrayList<>(); 
    private Map<String, List<com.raks.raksanalyzer.domain.model.tibco.TibcoGlobalVariable>> globalVariables = new HashMap<>(); 
    private String excelReportPath;
    private String wordDocumentPath;
    private String pdfDocumentPath;
    private String outputDirectory;  
    private String sourceUrl;
    private String progressMessage = "Initializing...";
    private int progressPercentage = 0;
    private String status;
    private Map<String, Object> metadata = new HashMap<>();  
    public String getAnalysisId() {
        return analysisId;
    }
    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
    }
    public LocalDateTime getStartTime() {
        return startTime;
    }
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
    public LocalDateTime getEndTime() {
        return endTime;
    }
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
    public boolean isSuccess() {
        return success;
    }
    public void setSuccess(boolean success) {
        this.success = success;
    }
    public String getErrorMessage() {
        return errorMessage;
    }
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    public ProjectInfo getProjectInfo() {
        return projectInfo;
    }
    public void setProjectInfo(ProjectInfo projectInfo) {
        this.projectInfo = projectInfo;
    }
    public ProjectNode getProjectStructure() {
        return projectStructure;
    }
    public void setProjectStructure(ProjectNode projectStructure) {
        this.projectStructure = projectStructure;
    }
    public List<FlowInfo> getFlows() {
        return flows;
    }
    public void setFlows(List<FlowInfo> flows) {
        this.flows = flows;
    }
    public List<ComponentInfo> getComponents() {
        return components;
    }
    public Map<String, List<com.raks.raksanalyzer.domain.model.tibco.TibcoGlobalVariable>> getGlobalVariables() {
        return globalVariables;
    }
    public void setGlobalVariables(Map<String, List<com.raks.raksanalyzer.domain.model.tibco.TibcoGlobalVariable>> globalVariables) {
        this.globalVariables = globalVariables;
    }
    public void setComponents(List<ComponentInfo> components) {
        this.components = components;
    }
    public List<PropertyInfo> getProperties() {
        return properties;
    }
    public void setProperties(List<PropertyInfo> properties) {
        this.properties = properties;
    }
    public String getExcelReportPath() {
        return excelReportPath;
    }
    public void setExcelReportPath(String excelReportPath) {
        this.excelReportPath = excelReportPath;
    }
    public String getWordDocumentPath() {
        return wordDocumentPath;
    }
    public void setWordDocumentPath(String wordDocumentPath) {
        this.wordDocumentPath = wordDocumentPath;
    }
    public String getPdfDocumentPath() {
        return pdfDocumentPath;
    }
    public void setPdfDocumentPath(String pdfDocumentPath) {
        this.pdfDocumentPath = pdfDocumentPath;
    }
    public List<String> getPropertyFiles() {
        return propertyFiles;
    }
    public void setPropertyFiles(List<String> propertyFiles) {
        this.propertyFiles = propertyFiles;
    }
    public List<ResourceInfo> getResources() {
        return resources;
    }
    public void setResources(List<ResourceInfo> resources) {
        this.resources = resources;
    }
    public String getOutputDirectory() {
        return outputDirectory;
    }
    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }
    public long getDurationMillis() {
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
        return 0;
    }
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    public Object getMetadata(String key) {
        return this.metadata.get(key);
    }
    public String getSourceUrl() {
        return sourceUrl;
    }
    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }
    public String getProgressMessage() {
        return progressMessage;
    }
    public void setProgressMessage(String progressMessage) {
        this.progressMessage = progressMessage;
    }
    public int getProgressPercentage() {
        return progressPercentage;
    }
    public void setProgressPercentage(int progressPercentage) {
        this.progressPercentage = progressPercentage;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
}
