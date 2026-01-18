package com.raks.aegis.model;

import java.util.List;

/**
 * Defines criteria for detecting and classifying a project type.
 * Used to determine if a project matches a specific type (e.g., CODE, CONFIG).
 */
public class DetectionCriteria {
    private List<String> markerFiles;      // Files that must exist (e.g., ["pom.xml"])
    private String namePattern;             // Regex pattern for project folder name
    private List<String> nameContains;      // Substrings the project name must contain
    private List<String> excludePatterns;   // Regex patterns to exclude projects
    private String logic;                   // "AND" or "OR" (default: OR)

    public List<String> getMarkerFiles() {
        return markerFiles;
    }

    public void setMarkerFiles(List<String> markerFiles) {
        this.markerFiles = markerFiles;
    }

    public String getNamePattern() {
        return namePattern;
    }

    public void setNamePattern(String namePattern) {
        this.namePattern = namePattern;
    }

    public List<String> getNameContains() {
        return nameContains;
    }

    public void setNameContains(List<String> nameContains) {
        this.nameContains = nameContains;
    }

    public List<String> getExcludePatterns() {
        return excludePatterns;
    }

    public void setExcludePatterns(List<String> excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    public String getLogic() {
        return logic != null ? logic : "OR";
    }

    public void setLogic(String logic) {
        this.logic = logic;
    }
}
