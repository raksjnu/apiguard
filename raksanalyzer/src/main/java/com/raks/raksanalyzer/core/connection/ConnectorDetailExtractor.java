package com.raks.raksanalyzer.core.connection;
import com.raks.raksanalyzer.domain.model.ComponentInfo;
import com.raks.raksanalyzer.domain.model.ConnectorConfig;
import com.raks.raksanalyzer.core.utils.PropertyResolver;
public interface ConnectorDetailExtractor {
    String extractDetails(ConnectorConfig config, ComponentInfo component, PropertyResolver propertyResolver);
    String getConnectorType();
}
