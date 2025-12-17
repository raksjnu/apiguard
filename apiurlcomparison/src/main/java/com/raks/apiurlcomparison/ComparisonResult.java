package com.raks.apiurlcomparison;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComparisonResult {
    private String operationName;
    private Map<String, Object> iterationTokens;
    private String status; // e.g., MATCH, MISMATCH, ERROR
    private String errorMessage;
    private List<String> differences;
    private String timestamp;

    // Baseline metadata (only populated in BASELINE comparison mode)
    private String baselineServiceName;
    private String baselineDate;
    private String baselineRunId;
    private String baselinePath; // Formatted path: baselines\{service}\{date}\{runId}
    private String baselineDescription;
    private java.util.List<String> baselineTags;
    private String baselineCaptureTimestamp;

    private ApiCallResult api1;
    private ApiCallResult api2;

    public enum Status {
        MATCH, MISMATCH, ERROR
    }

    // Getters and Setters

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public Map<String, Object> getIterationTokens() {
        return iterationTokens;
    }

    public void setIterationTokens(Map<String, Object> iterationTokens) {
        this.iterationTokens = iterationTokens;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status.name();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<String> getDifferences() {
        return differences;
    }

    public void setDifferences(List<String> differences) {
        this.differences = differences;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public ApiCallResult getApi1() {
        return api1;
    }

    public void setApi1(ApiCallResult api1) {
        this.api1 = api1;
    }

    public ApiCallResult getApi2() {
        return api2;
    }

    public void setApi2(ApiCallResult api2) {
        this.api2 = api2;
    }

    public String getBaselineServiceName() {
        return baselineServiceName;
    }

    public void setBaselineServiceName(String baselineServiceName) {
        this.baselineServiceName = baselineServiceName;
    }

    public String getBaselineDate() {
        return baselineDate;
    }

    public void setBaselineDate(String baselineDate) {
        this.baselineDate = baselineDate;
    }

    public String getBaselineRunId() {
        return baselineRunId;
    }

    public void setBaselineRunId(String baselineRunId) {
        this.baselineRunId = baselineRunId;
    }

    public String getBaselineDescription() {
        return baselineDescription;
    }

    public void setBaselineDescription(String baselineDescription) {
        this.baselineDescription = baselineDescription;
    }

    public java.util.List<String> getBaselineTags() {
        return baselineTags;
    }

    public void setBaselineTags(java.util.List<String> baselineTags) {
        this.baselineTags = baselineTags;
    }

    public String getBaselineCaptureTimestamp() {
        return baselineCaptureTimestamp;
    }

    public void setBaselineCaptureTimestamp(String baselineCaptureTimestamp) {
        this.baselineCaptureTimestamp = baselineCaptureTimestamp;
    }

    public String getBaselinePath() {
        return baselinePath;
    }

    public void setBaselinePath(String baselinePath) {
        this.baselinePath = baselinePath;
    }
}