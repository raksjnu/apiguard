package com.raks.raksanalyzer.core.connection;

import com.raks.raksanalyzer.domain.model.ComponentInfo;
import com.raks.raksanalyzer.domain.model.ConnectorConfig;
import com.raks.raksanalyzer.core.utils.PropertyResolver;

/**
 * Extracts connection details for Database connectors.
 */
public class DatabaseDetailExtractor implements ConnectorDetailExtractor {
    
    private final String connectorType;
    
    public DatabaseDetailExtractor(String connectorType) {
        this.connectorType = connectorType;
    }
    
    @Override
    public String extractDetails(ConnectorConfig config, ComponentInfo component, PropertyResolver propertyResolver) {
        if (config == null) {
            return "";
        }
        
        // Try to extract from nested connection components
        String host = extractNestedAttribute(config, "connection", "host", propertyResolver);
        String port = extractNestedAttribute(config, "connection", "port", propertyResolver);
        String database = extractNestedAttribute(config, "connection", "database", propertyResolver);
        
        // For Oracle, try serviceName
        if (database == null) {
            database = extractNestedAttribute(config, "connection", "serviceName", propertyResolver);
        }
        
        // Try generic URL if specific attributes not found
        if (host == null) {
            String url = extractNestedAttribute(config, "connection", "url", propertyResolver);
            if (url != null) {
                // Clean URL: remove query parameters after semicolon for cleaner display
                // Example: jdbc:sqlserver://host:1433;DatabaseName=X;encrypt=true
                // Result: jdbc:sqlserver://host:1433;DatabaseName=X
                return cleanDatabaseUrl(url);
            }
        }
        
        // Format: host:port/database
        StringBuilder result = new StringBuilder();
        if (host != null && !host.isEmpty()) {
            result.append(host);
        }
        if (port != null && !port.isEmpty()) {
            result.append(":").append(port);
        }
        if (database != null && !database.isEmpty()) {
            result.append("/").append(database);
        }
        
        return result.toString();
    }
    
    @Override
    public String getConnectorType() {
        return connectorType;
    }
    
    private String extractNestedAttribute(ConnectorConfig config, String nestedTypeContains, String attributeName, PropertyResolver resolver) {
        if (config.getNestedComponents() == null) {
            return null;
        }
        
        for (ComponentInfo nested : config.getNestedComponents()) {
            if (nested.getType() != null && nested.getType().contains(nestedTypeContains)) {
                String value = nested.getAttributes().get(attributeName);
                if (value != null && resolver != null) {
                    value = resolver.resolve(value);
                }
                return value;
            }
        }
        
        // Also check config attributes directly
        String value = config.getAttributes().get(attributeName);
        if (value != null && resolver != null) {
            value = resolver.resolve(value);
        }
        return value;
    }
    
    /**
     * Cleans database URL by keeping only essential connection info.
     * Removes extra parameters like encrypt, trustServerCertificate, etc.
     * Keeps: protocol, host, port, database/serviceName
     */
    private String cleanDatabaseUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        
        // For JDBC URLs with semicolon-separated parameters
        // Keep only up to DatabaseName or serviceName parameter
        if (url.contains(";")) {
            String[] parts = url.split(";");
            StringBuilder cleaned = new StringBuilder(parts[0]); // Protocol and server
            
            // Add only DatabaseName or serviceName parameter
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.toLowerCase().startsWith("databasename=") || 
                    part.toLowerCase().startsWith("servicename=")) {
                    cleaned.append(";").append(part);
                    break; // Stop after finding database/service name
                }
            }
            
            return cleaned.toString();
        }
        
        return url;
    }
}
