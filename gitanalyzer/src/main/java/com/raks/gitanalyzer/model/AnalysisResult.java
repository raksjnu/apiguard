package com.raks.gitanalyzer.model;

import java.util.ArrayList;
import java.util.List;

public class AnalysisResult {
    private String apiName; // Or project name
    private String sourceBranch;
    private String targetBranch;
    private int totalFilesChanged;
    private int codeChangesCount;
    private int configChangesCount;
    private List<FileChange> modifiedFiles;

    public AnalysisResult() {
        this.modifiedFiles = new ArrayList<>();
    }

    public void addFileChange(FileChange change) {
        this.modifiedFiles.add(change);
        this.totalFilesChanged++;
        if ("CODE".equalsIgnoreCase(change.getType())) {
            this.codeChangesCount++;
        } else {
            this.configChangesCount++;
        }
    }

    // Getters and Setters
    public String getApiName() { return apiName; }
    public void setApiName(String apiName) { this.apiName = apiName; }

    public String getSourceBranch() { return sourceBranch; }
    public void setSourceBranch(String sourceBranch) { this.sourceBranch = sourceBranch; }

    public String getTargetBranch() { return targetBranch; }
    public void setTargetBranch(String targetBranch) { this.targetBranch = targetBranch; }

    public int getTotalFilesChanged() { return totalFilesChanged; }
    public void setTotalFilesChanged(int totalFilesChanged) { this.totalFilesChanged = totalFilesChanged; }

    public int getCodeChangesCount() { return codeChangesCount; }
    public void setCodeChangesCount(int codeChangesCount) { this.codeChangesCount = codeChangesCount; }

    public int getConfigChangesCount() { return configChangesCount; }
    public void setConfigChangesCount(int configChangesCount) { this.configChangesCount = configChangesCount; }

    public List<FileChange> getModifiedFiles() { return modifiedFiles; }
    public void setModifiedFiles(List<FileChange> modifiedFiles) { this.modifiedFiles = modifiedFiles; }
}
