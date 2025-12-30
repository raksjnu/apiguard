package com.raks.raksanalyzer.core.connection;
import com.raks.raksanalyzer.domain.model.ComponentInfo;
import com.raks.raksanalyzer.domain.model.ConnectorConfig;
import com.raks.raksanalyzer.core.utils.PropertyResolver;
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
        String host = extractNestedAttribute(config, "connection", "host", propertyResolver);
        String port = extractNestedAttribute(config, "connection", "port", propertyResolver);
        String database = extractNestedAttribute(config, "connection", "database", propertyResolver);
        if (database == null) {
            database = extractNestedAttribute(config, "connection", "serviceName", propertyResolver);
        }
        if (host == null) {
            String url = extractNestedAttribute(config, "connection", "url", propertyResolver);
            if (url != null) {
                return cleanDatabaseUrl(url);
            }
        }
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
        String value = config.getAttributes().get(attributeName);
        if (value != null && resolver != null) {
            value = resolver.resolve(value);
        }
        return value;
    }
    private String cleanDatabaseUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        if (url.contains(";")) {
            String[] parts = url.split(";");
            StringBuilder cleaned = new StringBuilder(parts[0]); 
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.toLowerCase().startsWith("databasename=") || 
                    part.toLowerCase().startsWith("servicename=")) {
                    cleaned.append(";").append(part);
                    break; 
                }
            }
            return cleaned.toString();
        }
        return url;
    }
}
