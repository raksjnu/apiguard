package com.raks.apiforge;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
public class IterationMetadata {
    @JsonProperty("iterationNumber")
    private int iterationNumber;
    @JsonProperty("timestamp")
    private String timestamp; 
    @JsonProperty("tokensUsed")
    private Map<String, String> tokensUsed;
    @JsonProperty("endpoint")
    private String endpoint;
    @JsonProperty("method")
    private String method;
    @JsonProperty("soapAction")
    private String soapAction; 
    @JsonProperty("authentication")
    private Map<String, String> authentication;
    public IterationMetadata() {
    }
    public IterationMetadata(int iterationNumber, String timestamp, Map<String, String> tokensUsed,
            String endpoint, String method, String soapAction, Map<String, String> authentication) {
        this.iterationNumber = iterationNumber;
        this.timestamp = timestamp;
        this.tokensUsed = tokensUsed;
        this.endpoint = endpoint;
        this.method = method;
        this.soapAction = soapAction;
        this.authentication = authentication;
    }
    public int getIterationNumber() {
        return iterationNumber;
    }
    public void setIterationNumber(int iterationNumber) {
        this.iterationNumber = iterationNumber;
    }
    public String getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    public Map<String, String> getTokensUsed() {
        return tokensUsed;
    }
    public void setTokensUsed(Map<String, String> tokensUsed) {
        this.tokensUsed = tokensUsed;
    }
    public String getEndpoint() {
        return endpoint;
    }
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
    public String getMethod() {
        return method;
    }
    public void setMethod(String method) {
        this.method = method;
    }
    public String getSoapAction() {
        return soapAction;
    }
    public void setSoapAction(String soapAction) {
        this.soapAction = soapAction;
    }
    public Map<String, String> getAuthentication() {
        return authentication;
    }
    public void setAuthentication(Map<String, String> authentication) {
        this.authentication = authentication;
    }
}
