package com.raks.apiurlcomparison;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class Config {

    @JsonProperty("testType")
    private String testType;

    @JsonProperty("rest")
    private Map<String, ApiConfig> restApis;

    @JsonProperty("soap")
    private Map<String, ApiConfig> soapApis;

    @JsonProperty("maxIterations")
    private int maxIterations;

    @JsonProperty("tokens")
    private Map<String, List<Object>> tokens;

    @JsonProperty("iterationController")
    private String iterationController;

    @JsonProperty("comparisonMode")
    private String comparisonMode = "LIVE"; // "LIVE" or "BASELINE"

    @JsonProperty("baseline")
    private BaselineConfig baseline;

    // Getters
    public String getTestType() {
        return testType;
    }

    public String getIterationController() {
        return iterationController;
    }

    public String getComparisonMode() {
        return comparisonMode != null ? comparisonMode : "LIVE";
    }

    public BaselineConfig getBaseline() {
        return baseline;
    }

    public Map<String, ApiConfig> getRestApis() {
        return restApis;
    }

    public Map<String, ApiConfig> getSoapApis() {
        return soapApis;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public Map<String, List<Object>> getTokens() {
        return tokens;
    }

    // Setters (optional, but good for Jackson deserialization)
    public void setTestType(String testType) {
        this.testType = testType;
    }

    public void setIterationController(String iterationController) {
        this.iterationController = iterationController;
    }

    public void setComparisonMode(String comparisonMode) {
        this.comparisonMode = comparisonMode;
    }

    public void setBaseline(BaselineConfig baseline) {
        this.baseline = baseline;
    }

    public void setRestApis(Map<String, ApiConfig> restApis) {
        this.restApis = restApis;
    }

    public void setSoapApis(Map<String, ApiConfig> soapApis) {
        this.soapApis = soapApis;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public void setTokens(Map<String, List<Object>> tokens) {
        this.tokens = tokens;
    }

    // Inner class for baseline configuration
    public static class BaselineConfig {
        @JsonProperty("operation")
        private String operation; // "CAPTURE" or "COMPARE"

        @JsonProperty("storageDir")
        private String storageDir = "baselines";

        @JsonProperty("serviceName")
        private String serviceName;

        @JsonProperty("description")
        private String description;

        @JsonProperty("tags")
        private List<String> tags;

        @JsonProperty("compareDate")
        private String compareDate;

        @JsonProperty("compareRunId")
        private String compareRunId;

        // Getters
        public String getOperation() {
            return operation;
        }

        public String getStorageDir() {
            return storageDir != null ? storageDir : "baselines";
        }

        public String getServiceName() {
            return serviceName;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getTags() {
            return tags;
        }

        public String getCompareDate() {
            return compareDate;
        }

        public String getCompareRunId() {
            return compareRunId;
        }

        // Setters
        public void setOperation(String operation) {
            this.operation = operation;
        }

        public void setStorageDir(String storageDir) {
            this.storageDir = storageDir;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public void setCompareDate(String compareDate) {
            this.compareDate = compareDate;
        }

        public void setCompareRunId(String compareRunId) {
            this.compareRunId = compareRunId;
        }
    }
}