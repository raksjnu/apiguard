package com.raks.raksanalyzer.core.connection;
import com.raks.raksanalyzer.domain.model.ComponentInfo;
import com.raks.raksanalyzer.domain.model.ConnectorConfig;
import com.raks.raksanalyzer.core.utils.PropertyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
public class ConnectorDetailExtractorFactory {
    private static final Logger logger = LoggerFactory.getLogger(ConnectorDetailExtractorFactory.class);
    private final Map<String, ConnectorDetailExtractor> extractors;
    private final ConnectorDetailExtractor defaultExtractor;
    public ConnectorDetailExtractorFactory(Properties config) {
        this.extractors = new HashMap<>();
        this.defaultExtractor = new DefaultDetailExtractor();
        extractors.put("http:listener-config", new HttpDetailExtractor("http:listener-config"));
        extractors.put("http:request-config", new HttpDetailExtractor("http:request-config"));
        extractors.put("db:config", new DatabaseDetailExtractor("db:config"));
        extractors.put("db:my-sql-connection", new DatabaseDetailExtractor("db:my-sql-connection"));
        extractors.put("db:oracle-connection", new DatabaseDetailExtractor("db:oracle-connection"));
        extractors.put("db:generic-connection", new DatabaseDetailExtractor("db:generic-connection"));
        extractors.put("email:smtp-config", new EmailDetailExtractor("email:smtp-config"));
        extractors.put("email:imap-config", new EmailDetailExtractor("email:imap-config"));
        extractors.put("email:pop3-config", new EmailDetailExtractor("email:pop3-config"));
        extractors.put("ibm-mq:config", new IbmMqDetailExtractor("ibm-mq:config"));
        extractors.put("sockets:request-config", new SocketsDetailExtractor("sockets:request-config"));
        logger.info("Initialized ConnectorDetailExtractorFactory with {} extractors", extractors.size());
    }
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
