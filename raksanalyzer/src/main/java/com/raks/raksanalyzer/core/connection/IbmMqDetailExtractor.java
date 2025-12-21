package com.raks.raksanalyzer.core.connection;

import com.raks.raksanalyzer.domain.model.ComponentInfo;
import com.raks.raksanalyzer.domain.model.ConnectorConfig;
import com.raks.raksanalyzer.core.utils.PropertyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts connection details for IBM MQ connectors.
 * MQ configs have: queueManager, channel, host, port (in connection-mode)
 * MQ components have: destination attribute
 */
public class IbmMqDetailExtractor implements ConnectorDetailExtractor {
    private static final Logger logger = LoggerFactory.getLogger(IbmMqDetailExtractor.class);
    
    private final String connectorType;
    
    public IbmMqDetailExtractor(String connectorType) {
        this.connectorType = connectorType;
    }
    
    @Override
    public String extractDetails(ConnectorConfig config, ComponentInfo component, PropertyResolver propertyResolver) {
        logger.debug("IBM MQ Extractor called - config: {}, component: {}", 
            config != null ? config.getName() : "null",
            component != null ? component.getType() : "null");
            
        if (config == null) {
            logger.warn("Config is null for IBM MQ extractor");
            return "";
        }
        
        // Extract from nested connection-mode
        String queueManager = extractNestedAttribute(config, "connection-mode", "queueManager", propertyResolver);
        String channel = extractNestedAttribute(config, "connection-mode", "channel", propertyResolver);
        String host = extractNestedAttribute(config, "connection-mode", "host", propertyResolver);
        String port = extractNestedAttribute(config, "connection-mode", "port", propertyResolver);
        
        // Also try client element for all attributes (as they are often on ibm-mq:client)
        if (queueManager == null) {
            queueManager = extractNestedAttribute(config, "client", "queueManager", propertyResolver);
        }
        if (channel == null) {
            channel = extractNestedAttribute(config, "client", "channel", propertyResolver);
        }
        if (host == null) {
            host = extractNestedAttribute(config, "client", "host", propertyResolver);
        }
        if (port == null) {
            port = extractNestedAttribute(config, "client", "port", propertyResolver);
        }
        
        // Get destination from component attribute (not from config!)
        String destination = "";
        if (component != null && component.getAttributes() != null) {
            String dest = component.getAttributes().get("destination");
            if (dest != null && !dest.isEmpty()) {
                // Handle DataWeave expressions like #[vars.queuename default ""]
                if (dest.startsWith("#[")) {
                    // Extract variable name or show as dynamic
                    if (dest.contains("vars.")) {
                        int start = dest.indexOf("vars.") + 5;
                        int end = dest.indexOf(" ", start);
                        if (end == -1) end = dest.indexOf("]", start);
                        if (end > start) {
                            String varName = dest.substring(start, end);
                            destination = "${" + varName + "}";
                        } else {
                            destination = "[dynamic]";
                        }
                    } else {
                        destination = "[dynamic]";
                    }
                } else {
                    // Static destination or property placeholder
                    if (propertyResolver != null) {
                        dest = propertyResolver.resolve(dest);
                    }
                    destination = dest;
                }
            }
        }
        
        // Format: host:port | QM:qmgr | CH:channel | Q:destination
        StringBuilder result = new StringBuilder();
        
        if (host != null && !host.isEmpty()) {
            result.append(host);
            if (port != null && !port.isEmpty()) {
                result.append(":").append(port);
            }
        }
        
        if (queueManager != null && !queueManager.isEmpty()) {
            if (result.length() > 0) result.append(" | ");
            result.append("QM:").append(queueManager);
        }
        
        if (channel != null && !channel.isEmpty()) {
            if (result.length() > 0) result.append(" | ");
            result.append("CH:").append(channel);
        }
        
        if (!destination.isEmpty()) {
            if (result.length() > 0) result.append(" | ");
            result.append("Q:").append(destination);
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
}
