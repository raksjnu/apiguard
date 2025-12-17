package com.raks.apiurlcomparison;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Metadata for a baseline run (run-level information)
 */
public class RunMetadata {

    @JsonProperty("runId")
    private String runId;

    @JsonProperty("serviceName")
    private String serviceName;

    @JsonProperty("captureDate")
    private String captureDate; // YYYYMMDD format

    @JsonProperty("captureTimestamp")
    private String captureTimestamp; // ISO 8601 format

    @JsonProperty("testType")
    private String testType; // "REST" or "SOAP"

    @JsonProperty("endpoint")
    private String endpoint;

    @JsonProperty("operation")
    private String operation;

    @JsonProperty("totalIterations")
    private int totalIterations;

    @JsonProperty("description")
    private String description;

    @JsonProperty("tags")
    private List<String> tags;

    @JsonProperty("configUsed")
    private Map<String, Object> configUsed;

    // Constructors
    public RunMetadata() {
    }

    public RunMetadata(String runId, String serviceName, String captureDate, String captureTimestamp,
            String testType, String endpoint, String operation, int totalIterations,
            String description, List<String> tags, Map<String, Object> configUsed) {
        this.runId = runId;
        this.serviceName = serviceName;
        this.captureDate = captureDate;
        this.captureTimestamp = captureTimestamp;
        this.testType = testType;
        this.endpoint = endpoint;
        this.operation = operation;
        this.totalIterations = totalIterations;
        this.description = description;
        this.tags = tags;
        this.configUsed = configUsed;
    }

    // Getters and Setters
    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getCaptureDate() {
        return captureDate;
    }

    public void setCaptureDate(String captureDate) {
        this.captureDate = captureDate;
    }

    public String getCaptureTimestamp() {
        return captureTimestamp;
    }

    public void setCaptureTimestamp(String captureTimestamp) {
        this.captureTimestamp = captureTimestamp;
    }

    public String getTestType() {
        return testType;
    }

    public void setTestType(String testType) {
        this.testType = testType;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public int getTotalIterations() {
        return totalIterations;
    }

    public void setTotalIterations(int totalIterations) {
        this.totalIterations = totalIterations;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Map<String, Object> getConfigUsed() {
        return configUsed;
    }

    public void setConfigUsed(Map<String, Object> configUsed) {
        this.configUsed = configUsed;
    }
}
