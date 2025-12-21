package com.raks.raksanalyzer.core.connection;

import com.raks.raksanalyzer.domain.model.ComponentInfo;
import com.raks.raksanalyzer.domain.model.ConnectorConfig;
import com.raks.raksanalyzer.core.utils.PropertyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Factory for creating connector-specific detail extractors.
 * Reads configuration to determine which attributes to extract per connector type.
 */
public class ConnectorDetailExtractorFactory {
    private static final Logger logger = LoggerFactory.getLogger(ConnectorDetailExtractorFactory.class);
    
    private final Map<String, ConnectorDetailExtractor> extractors;
    private final ConnectorDetailExtractor defaultExtractor;
    
    /**
     * Creates a factory with configuration properties.
     * @param config Configuration properties
     */
    public ConnectorDetailExtractorFactory(Properties config) {
        this.extractors = new HashMap<>();
        this.defaultExtractor = new DefaultDetailExtractor();
        
        // Register HTTP extractors
        extractors.put("http:listener-config", new HttpDetailExtractor("http:listener-config"));
        extractors.put("http:request-config", new HttpDetailExtractor("http:request-config"));
        
        // Register Database extractors
        extractors.put("db:config", new DatabaseDetailExtractor("db:config"));
        extractors.put("db:my-sql-connection", new DatabaseDetailExtractor("db:my-sql-connection"));
        extractors.put("db:oracle-connection", new DatabaseDetailExtractor("db:oracle-connection"));
        extractors.put("db:generic-connection", new DatabaseDetailExtractor("db:generic-connection"));
        
        // Register Email extractors
        extractors.put("email:smtp-config", new EmailDetailExtractor("email:smtp-config"));
        extractors.put("email:imap-config", new EmailDetailExtractor("email:imap-config"));
        extractors.put("email:pop3-config", new EmailDetailExtractor("email:pop3-config"));
        
        // Register IBM MQ extractors
        extractors.put("ibm-mq:config", new IbmMqDetailExtractor("ibm-mq:config"));
        
        logger.info("Initialized ConnectorDetailExtractorFactory with {} extractors", extractors.size());
    }
    
    /**
     * Gets an extractor for the given connector type.
     * @param connectorType Connector type (e.g., "http:listener-config")
     * @return Connector detail extractor
     */
    public ConnectorDetailExtractor getExtractor(String connectorType) {
        if (connectorType == null) {
            return defaultExtractor;
        }
        
        ConnectorDetailExtractor extractor = extractors.get(connectorType);
        if (extractor == null) {
            logger.debug("No specific extractor for type: {}, using default", connectorType);
            return defaultExtractor;
        }
        
        return extractor;
    }
    
    /**
     * Default extractor that just returns the config-ref name.
     */
    private static class DefaultDetailExtractor implements ConnectorDetailExtractor {
        @Override
        public String extractDetails(ConnectorConfig config, ComponentInfo component, PropertyResolver propertyResolver) {
            if (config != null && config.getName() != null) {
                return "[" + config.getName() + "]";
            }
            return "";
        }
        
        @Override
        public String getConnectorType() {
            return "default";
        }
    }
}
