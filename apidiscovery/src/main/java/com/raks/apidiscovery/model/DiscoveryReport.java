package com.raks.apidiscovery.model;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class DiscoveryReport {
    private String repoName;
    private String repoPath;
    private String technology = "Unknown"; 
    private String classification = "Unknown"; 
    private int confidenceScore = 0;
    private List<String> indicators = new ArrayList<>();
    private List<String> evidence = new ArrayList<>();
    private Map<String, String> metadata = new HashMap<>();
    public void addIndicator(String indicator) {
        this.indicators.add(indicator);
    }
    public void addEvidence(String evidence) {
        this.evidence.add(evidence);
    }
    public void addMetadata(String key, String value) {
        this.metadata.put(key, value);
    }
    public String getRepoName() { return repoName; }
    public void setRepoName(String repoName) { this.repoName = repoName; }
    public String getRepoPath() { return repoPath; }
    public void setRepoPath(String repoPath) { this.repoPath = repoPath; }
    public String getTechnology() { return technology; }
    public void setTechnology(String technology) { this.technology = technology; }
    public String getClassification() { return classification; }
    public void setClassification(String classification) { this.classification = classification; }
    public int getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(int confidenceScore) { this.confidenceScore = confidenceScore; }
    public List<String> getIndicators() { return indicators; }
    public List<String> getEvidence() { return evidence; }
    public Map<String, String> getMetadata() { return metadata; }
}
