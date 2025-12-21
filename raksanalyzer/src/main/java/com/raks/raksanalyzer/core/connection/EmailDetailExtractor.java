package com.raks.raksanalyzer.core.connection;

import com.raks.raksanalyzer.domain.model.ComponentInfo;
import com.raks.raksanalyzer.domain.model.ConnectorConfig;
import com.raks.raksanalyzer.core.utils.PropertyResolver;

/**
 * Extracts connection details for Email connectors (SMTP, IMAP, POP3).
 */
public class EmailDetailExtractor implements ConnectorDetailExtractor {
    
    private final String connectorType;
    
    public EmailDetailExtractor(String connectorType) {
        this.connectorType = connectorType;
    }
    
    @Override
    public String extractDetails(ConnectorConfig config, ComponentInfo component, PropertyResolver propertyResolver) {
        if (config == null) {
            return "";
        }
        
        // Extract from nested connection (smtp-connection, imap-connection, etc.)
        String host = extractNestedAttribute(config, "connection", "host", propertyResolver);
        String port = extractNestedAttribute(config, "connection", "port", propertyResolver);
        
        // Get toAddresses from component if it's an email:send
        String toAddresses = "";
        if (component != null && component.getType() != null && component.getType().contains("send")) {
            if (component.getAttributes() != null) {
                String to = component.getAttributes().get("toAddresses");
                if (to != null && !to.isEmpty()) {
                    toAddresses = "\nto: " + to;
                }
            }
        }
        
        // Format: smtp://host:port or host:port\nto: email
        StringBuilder result = new StringBuilder();
        if (connectorType.contains("smtp")) {
            result.append("smtp://");
        } else if (connectorType.contains("imap")) {
            result.append("imap://");
        } else if (connectorType.contains("pop3")) {
            result.append("pop3://");
        }
        
        if (host != null && !host.isEmpty()) {
            result.append(host);
        }
        if (port != null && !port.isEmpty()) {
            result.append(":").append(port);
        }
        result.append(toAddresses);
        
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
        
        String value = config.getAttributes().get(attributeName);
        if (value != null && resolver != null) {
            value = resolver.resolve(value);
        }
        return value;
    }
}
