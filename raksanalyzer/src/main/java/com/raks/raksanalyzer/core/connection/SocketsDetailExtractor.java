package com.raks.raksanalyzer.core.connection;

import com.raks.raksanalyzer.domain.model.ComponentInfo;
import com.raks.raksanalyzer.domain.model.ConnectorConfig;
import com.raks.raksanalyzer.core.utils.PropertyResolver;

/**
 * Extracts connection details for Sockets connectors.
 */
public class SocketsDetailExtractor implements ConnectorDetailExtractor {
    
    private final String connectorType;
    
    public SocketsDetailExtractor(String connectorType) {
        this.connectorType = connectorType;
    }
    
    @Override
    public String extractDetails(ConnectorConfig config, ComponentInfo component, PropertyResolver propertyResolver) {
        if (config == null) {
            return "";
        }
        
        // Extract host and port from nested tcp-requester-connection or tcp-listener-connection
        // Try requester first
        String host = extractNestedAttribute(config, "tcp-requester-connection", "host", propertyResolver);
        if (host == null) {
            host = extractNestedAttribute(config, "tcp-listener-connection", "host", propertyResolver);
        }
        
        // Fallback: check config attributes directly (sometimes inline)
        if (host == null) {
             host = extractAttribute(config, "host", propertyResolver);
        }
        
        String port = extractNestedAttribute(config, "tcp-requester-connection", "port", propertyResolver);
        if (port == null) {
            port = extractNestedAttribute(config, "tcp-listener-connection", "port", propertyResolver);
        }
        if (port == null) {
             port = extractAttribute(config, "port", propertyResolver);
        }
        
        // Format: host:port
        StringBuilder result = new StringBuilder();
        if (host != null && !host.isEmpty()) {
            result.append(host);
        }
        if (port != null && !port.isEmpty()) {
            if (result.length() > 0) result.append(":");
            result.append(port);
        }
        
        // Add path if relevant? Sockets usually don't have path in the same way, but maybe component has attributes?
        // sockets:send doesn't usually have a path override, it sends payload. 
        // But if there are other attributes like 'timeout', we could add them, but let's stick to host:port for now.
        
        return result.toString();
    }
    
    @Override
    public String getConnectorType() {
        return connectorType;
    }
    
    // Helper to extract nested attribute
    private String extractNestedAttribute(ConnectorConfig config, String nestedType, String attributeName, PropertyResolver resolver) {
        if (config.getNestedComponents() == null) {
            return null;
        }
        
        for (ComponentInfo nested : config.getNestedComponents()) {
            if (nested.getType() != null && nested.getType().contains(nestedType)) {
                String value = nested.getAttributes().get(attributeName);
                if (value != null && resolver != null) {
                    value = resolver.resolve(value);
                }
                return value;
            }
        }
        return null;
    }
    
    private String extractAttribute(ConnectorConfig config, String attributeName, PropertyResolver resolver) {
        String value = config.getAttributes().get(attributeName);
         if (value != null && resolver != null) {
            value = resolver.resolve(value);
        }
        return value;
    }
}
