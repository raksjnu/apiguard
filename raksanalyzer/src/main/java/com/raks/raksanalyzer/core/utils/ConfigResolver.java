package com.raks.raksanalyzer.core.utils;
import com.raks.raksanalyzer.domain.model.ConnectorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class ConfigResolver {
    private static final Logger logger = LoggerFactory.getLogger(ConfigResolver.class);
    private final Map<String, ConnectorConfig> configMap;
    public ConfigResolver() {
        this.configMap = new HashMap<>();
    }
    public void addConfig(ConnectorConfig config) {
        if (config != null && config.getName() != null) {
            configMap.put(config.getName(), config);
            logger.debug("Registered config: name='{}', type='{}'", config.getName(), config.getType());
        }
    }
    public void addConfigs(List<ConnectorConfig> configs) {
        if (configs != null) {
            for (ConnectorConfig config : configs) {
                addConfig(config);
            }
        }
    }
    public ConnectorConfig resolveConfig(String configRef) {
        if (configRef == null || configRef.isEmpty()) {
            return null;
        }
        ConnectorConfig config = configMap.get(configRef);
        if (config == null) {
            logger.warn("Config '{}' not found. Available: {}", configRef, configMap.keySet());
        }
        return config;
    }
    public boolean hasConfig(String configRef) {
        return configRef != null && configMap.containsKey(configRef);
    }
    public Map<String, ConnectorConfig> getAllConfigs() {
        return new HashMap<>(configMap);
    }
    public int getConfigCount() {
        return configMap.size();
    }
    public void clear() {
        configMap.clear();
    }
}
