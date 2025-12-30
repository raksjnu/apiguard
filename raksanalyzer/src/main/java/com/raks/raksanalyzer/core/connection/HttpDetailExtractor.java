package com.raks.raksanalyzer.core.connection;
import com.raks.raksanalyzer.domain.model.ComponentInfo;
import com.raks.raksanalyzer.domain.model.ConnectorConfig;
import com.raks.raksanalyzer.core.utils.PropertyResolver;
public class HttpDetailExtractor implements ConnectorDetailExtractor {
    private final String connectorType;
    public HttpDetailExtractor(String connectorType) {
        this.connectorType = connectorType;
    }
    @Override
    public String extractDetails(ConnectorConfig config, ComponentInfo component, PropertyResolver propertyResolver) {
        if (config == null) {
            return "";
        }
        String host = extractNestedAttribute(config, "listener-connection", "host", propertyResolver);
        if (host == null) {
            host = extractNestedAttribute(config, "request-connection", "host", propertyResolver);
        }
        String port = extractNestedAttribute(config, "listener-connection", "port", propertyResolver);
        if (port == null) {
            port = extractNestedAttribute(config, "request-connection", "port", propertyResolver);
        }
        String path = "";
        if (component != null && component.getAttributes() != null) {
            String componentPath = component.getAttributes().get("path");
            if (componentPath != null && !componentPath.isEmpty()) {
                if (propertyResolver != null) {
                    componentPath = propertyResolver.resolve(componentPath);
                }
                path = componentPath.startsWith("/") ? componentPath : "/" + componentPath;
            }
        }
        StringBuilder result = new StringBuilder();
        if (host != null && !host.isEmpty()) {
            result.append(host);
        }
        if (port != null && !port.isEmpty()) {
            result.append(":").append(port);
        }
        if (!path.isEmpty()) {
            result.append(path);
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
        String value = config.getAttributes().get(attributeName);
        if (value != null && resolver != null) {
            value = resolver.resolve(value);
        }
        return value;
    }
}
