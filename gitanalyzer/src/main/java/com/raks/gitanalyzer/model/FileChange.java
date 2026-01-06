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

    public FileChange(String path, String type, int validChangedLines, int ignoredLines, String severity) {
        this.path = path;
        this.type = type;
        this.validChangedLines = validChangedLines;
        this.ignoredLines = ignoredLines;
        this.severity = severity;
    }

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

    public boolean isNewFile() { return isNewFile; }
    public void setNewFile(boolean newFile) { isNewFile = newFile; }

    public boolean isDeletedFile() { return isDeletedFile; }
    public void setDeletedFile(boolean deletedFile) { isDeletedFile = deletedFile; }

    public String getDiffContent() { return diffContent; }
    public void setDiffContent(String diffContent) { this.diffContent = diffContent; }
}
