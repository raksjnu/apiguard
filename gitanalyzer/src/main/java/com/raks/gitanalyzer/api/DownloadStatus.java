package com.raks.gitanalyzer.api;

import java.util.*;

public class DownloadStatus {
    private int total;
    private int success;
    private String currentRepo = "";
    private String outputDir = "";
    private boolean finished = false;
    private String error = null;
    private List<Map<String, String>> details = new ArrayList<>();

    // Getters and Setters
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public int getSuccess() { return success; }
    public void setSuccess(int success) { this.success = success; }

    public String getCurrentRepo() { return currentRepo; }
    public void setCurrentRepo(String currentRepo) { this.currentRepo = currentRepo; }

    public String getOutputDir() { return outputDir; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }

    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public List<Map<String, String>> getDetails() { return details; }
    public void setDetails(List<Map<String, String>> details) { this.details = details; }
    
    public void addDetail(Map<String, String> detail) {
        this.details.add(detail);
    }
}
