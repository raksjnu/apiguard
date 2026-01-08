package com.raks.gitanalyzer.model;

public class FileChange {
    private String path;
    private String type; // CODE or CONFIG
    private int validChangedLines;
    private int ignoredLines;
    private String severity;
    private boolean isNewFile;
    private boolean isDeletedFile;
    private String diffContent; // For viewing diffs later

    private String status;
    private int additions;
    private int deletions;

    public FileChange() {
        // Default constructor
    }

    public FileChange(String path, String type, int validChangedLines, int ignoredLines, String severity) {
        this.path = path;
        this.type = type;
        this.validChangedLines = validChangedLines;
        this.ignoredLines = ignoredLines;
        this.severity = severity;
    }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // Getters and Setters
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getValidChangedLines() { return validChangedLines; }
    public void setValidChangedLines(int validChangedLines) { this.validChangedLines = validChangedLines; }

    public int getIgnoredLines() { return ignoredLines; }
    public void setIgnoredLines(int ignoredLines) { this.ignoredLines = ignoredLines; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    
    public int getAdditions() { return additions; }
    public void setAdditions(int additions) { this.additions = additions; }
    
    public int getDeletions() { return deletions; }
    public void setDeletions(int deletions) { this.deletions = deletions; }

    public boolean isNewFile() { return isNewFile; }
    public void setNewFile(boolean newFile) { isNewFile = newFile; }

    public boolean isDeletedFile() { return isDeletedFile; }
    public void setDeletedFile(boolean deletedFile) { isDeletedFile = deletedFile; }

    private java.util.Set<String> matchTypes = new java.util.HashSet<>();

    public java.util.Set<String> getMatchTypes() { return matchTypes; }
    public void setMatchTypes(java.util.Set<String> matchTypes) { this.matchTypes = matchTypes; }
    public void addMatchType(String type) { this.matchTypes.add(type); }

    public String getDiffContent() { return diffContent; }
    public void setDiffContent(String diffContent) { this.diffContent = diffContent; }
}
