package com.raks.apiurlcomparison;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Represents the configuration details for a single API (e.g., api1, api2 under
 * rest or soap).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiConfig {

    @JsonProperty("baseUrl")
    private String baseUrl;

    @JsonProperty("authentication")
    private Authentication authentication;

    @JsonProperty("operations")
    private List<Operation> operations;

    // Getters
    public String getBaseUrl() {
        return baseUrl;
    }

    public Authentication getAuthentication() {
        return authentication;
    }

    public List<Operation> getOperations() {
        return operations;
    }

    // Setters
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setAuthentication(Authentication authentication) {
        this.authentication = authentication;
    }

    public void setOperations(List<Operation> operations) {
        this.operations = operations;
    }

    // This helper method is no longer directly applicable to ApiConfig as headers
    // are now defined per operation.
    // It might be moved to the Operation class if needed for templating.
    // For now, it's returning an empty set to avoid compilation errors if it's
    // still referenced.
    public Iterable<Map.Entry<String, String>> getHeaderEntries() {
        return java.util.Collections.emptySet();
    }
}