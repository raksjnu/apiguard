package com.raks.raksanalyzer.core.utils;

import com.raks.raksanalyzer.domain.model.ConnectorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves connector config references to actual ConnectorConfig objects.
 * Builds a map of config-ref names to ConnectorConfig for quick lookup.
 */
public class ConfigResolver {
    private static final Logger logger = LoggerFactory.getLogger(ConfigResolver.class);
    
    private final Map<String, ConnectorConfig> configMap;
    
    /**
     * Creates a new ConfigResolver.
     */
    public ConfigResolver() {
        this.configMap = new HashMap<>();
    }
    
    /**
     * Adds a connector config to the resolver.
     * @param config Connector configuration
     */
    public void addConfig(ConnectorConfig config) {
        if (config != null && config.getName() != null) {
            configMap.put(config.getName(), config);
            logger.debug("Registered config: name='{}', type='{}'", config.getName(), config.getType());
        }
    }
    
    /**
     * Adds multiple connector configs.
     * @param configs List of connector configurations
     */
    public void addConfigs(List<ConnectorConfig> configs) {
        if (configs != null) {
            for (ConnectorConfig config : configs) {
                addConfig(config);
            }
        }
    }
    
    /**
     * Resolves a config-ref to its ConnectorConfig.
     * @param configRef Config reference name
     * @return ConnectorConfig or null if not found
     */
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
    
    /**
     * Checks if a config-ref exists.
     * @param configRef Config reference name
     * @return true if config exists
     */
    public boolean hasConfig(String configRef) {
        return configRef != null && configMap.containsKey(configRef);
    }
    
    /**
     * Gets all loaded configs.
     * @return Map of config name to ConnectorConfig
     */
    public Map<String, ConnectorConfig> getAllConfigs() {
        return new HashMap<>(configMap);
    }
    
    /**
     * Gets the number of loaded configs.
     * @return Config count
     */
    public int getConfigCount() {
        return configMap.size();
    }
    
    /**
     * Clears all loaded configs.
     */
    public void clear() {
        configMap.clear();
    }
}
