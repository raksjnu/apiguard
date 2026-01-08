package com.raks.gitanalyzer.model;

import java.util.ArrayList;
import java.util.List;

public class AnalysisResult {
    private String apiName; // Or project name
    private String codeRepo;
    private String configRepo;
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
        
        // Only increment counters if there are significant changes
        // A change is significant if it's New, Deleted, or has Valid Lines > 0
        // (If it's just Modified with 0 valid lines, it means all changes were ignored/cosmetic)
        boolean isSignificant = change.isNewFile() || change.isDeletedFile() || change.getValidChangedLines() > 0;
        
        if (isSignificant) {
            this.totalFilesChanged++;
            if ("CODE".equalsIgnoreCase(change.getType())) {
                this.codeChangesCount++;
            } else {
                this.configChangesCount++;
            }
        }
    }
    
    // Getters and Setters
    public String getApiName() { return apiName; }
    public void setApiName(String apiName) { this.apiName = apiName; }

    public String getCodeRepo() { return codeRepo; }
    public void setCodeRepo(String codeRepo) { this.codeRepo = codeRepo; }

    public String getConfigRepo() { return configRepo; }
    public void setConfigRepo(String configRepo) { this.configRepo = configRepo; }


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

    private String error;

    public List<FileChange> getModifiedFiles() { return modifiedFiles; }
    public void setModifiedFiles(List<FileChange> modifiedFiles) { this.modifiedFiles = modifiedFiles; }
    
    private List<String> ignoredFiles = new ArrayList<>();
    public List<String> getIgnoredFiles() { return ignoredFiles; }
    public void setIgnoredFiles(List<String> ignoredFiles) { this.ignoredFiles = ignoredFiles; }
    public void addIgnoredFile(String path) { this.ignoredFiles.add(path); }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
