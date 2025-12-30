package com.raks.raksanalyzer.core.connection;
import com.raks.raksanalyzer.domain.model.ComponentInfo;
import com.raks.raksanalyzer.domain.model.ConnectorConfig;
import com.raks.raksanalyzer.core.utils.PropertyResolver;
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
        String host = extractNestedAttribute(config, "tcp-requester-connection", "host", propertyResolver);
        if (host == null) {
            host = extractNestedAttribute(config, "tcp-listener-connection", "host", propertyResolver);
        }
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
        StringBuilder result = new StringBuilder();
        if (host != null && !host.isEmpty()) {
            result.append(host);
        }
        if (port != null && !port.isEmpty()) {
            if (result.length() > 0) result.append(":");
            result.append(port);
        }
        return result.toString();
    }
    @Override
    public String getConnectorType() {
        return connectorType;
    }
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
