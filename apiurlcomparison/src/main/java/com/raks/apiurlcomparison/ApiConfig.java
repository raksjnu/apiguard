package com.raks.apiurlcomparison;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiConfig {
    @JsonProperty("baseUrl")
    private String baseUrl;
    @JsonProperty("authentication")
    private Authentication authentication;
    @JsonProperty("operations")
    private List<Operation> operations;
    public String getBaseUrl() {
        return baseUrl;
    }
    public Authentication getAuthentication() {
        return authentication;
    }
    public List<Operation> getOperations() {
        return operations;
    }
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    public void setAuthentication(Authentication authentication) {
        this.authentication = authentication;
    }
    public void setOperations(List<Operation> operations) {
        this.operations = operations;
    }
    public Iterable<Map.Entry<String, String>> getHeaderEntries() {
        return java.util.Collections.emptySet();
    }
}