package com.raks.raksanalyzer.core.connection;

import com.raks.raksanalyzer.domain.model.ComponentInfo;
import com.raks.raksanalyzer.domain.model.ConnectorConfig;
import com.raks.raksanalyzer.core.utils.PropertyResolver;

/**
 * Interface for extracting connection details from connector configurations.
 * Each connector type (HTTP, DB, Email, etc.) has its own implementation.
 */
public interface ConnectorDetailExtractor {
    
    /**
     * Extracts connection details from a connector config.
     * 
     * @param config The connector configuration
     * @param component The component using this config (may have additional attributes)
     * @param propertyResolver Resolver for property placeholders
     * @return Formatted connection details string (e.g., "localhost:8081/api")
     */
    String extractDetails(ConnectorConfig config, ComponentInfo component, PropertyResolver propertyResolver);
    
    /**
     * Gets the connector type this extractor handles.
     * @return Connector type (e.g., "http:listener-config", "db:config")
     */
    String getConnectorType();
}
