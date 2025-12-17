package com.raks.raksanalyzer.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Central configuration manager for RaksAnalyzer.
 * 
 * Loads and manages configuration from multiple sources with precedence:
 * 1. Command-line arguments (highest)
 * 2. Custom config file
 * 3. Environment variables
 * 4. Default configuration files (lowest)
 * 
 * Configuration files:
 * - framework.properties: Internal framework settings
 * - ui-labels.properties: User-facing UI text
 */
public class ConfigurationManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationManager.class);
    
    private static ConfigurationManager instance;
    
    private final Properties frameworkProperties;
    private final Properties uiLabelsProperties;
    private final Properties mergedProperties;
    
    private ConfigurationManager() {
        this.frameworkProperties = loadPropertiesFile("config/defaults/framework.properties");
        this.uiLabelsProperties = loadPropertiesFile("config/defaults/ui-labels.properties");
        this.mergedProperties = new Properties();
        
        // Merge all properties (UI labels can override framework if needed)
        mergedProperties.putAll(frameworkProperties);
        mergedProperties.putAll(uiLabelsProperties);
        
        // Load environment variables with RAKSANALYZER_ prefix
        loadEnvironmentVariables();
        
        logger.info("Configuration loaded: {} framework properties, {} UI labels", 
            frameworkProperties.size(), uiLabelsProperties.size());
    }
    
    /**
     * Get singleton instance.
     */
    public static synchronized ConfigurationManager getInstance() {
        if (instance == null) {
            instance = new ConfigurationManager();
        }
        return instance;
    }
    
    /**
     * Load properties file from classpath.
     */
    private Properties loadPropertiesFile(String resourcePath) {
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (input != null) {
                props.load(input);
                logger.debug("Loaded {} properties from: {}", props.size(), resourcePath);
            } else {
                logger.warn("Properties file not found: {}", resourcePath);
            }
        } catch (IOException e) {
            logger.error("Error loading properties from: {}", resourcePath, e);
        }
        return props;
    }
    
    /**
     * Load environment variables with RAKSANALYZER_ prefix.
     * Example: RAKSANALYZER_SERVER_PORT -> framework.server.port
     */
    private void loadEnvironmentVariables() {
        Map<String, String> env = System.getenv();
        int count = 0;
        
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("RAKSANALYZER_")) {
                // Convert RAKSANALYZER_SERVER_PORT to framework.server.port
                String propKey = key.substring("RAKSANALYZER_".length())
                    .toLowerCase()
                    .replace('_', '.');
                
                mergedProperties.setProperty(propKey, entry.getValue());
                count++;
            }
        }
        
        if (count > 0) {
            logger.info("Loaded {} properties from environment variables", count);
        }
    }
    
    /**
     * Get property value with default.
     */
    public String getProperty(String key, String defaultValue) {
        return mergedProperties.getProperty(key, defaultValue);
    }
    
    /**
     * Get property value (returns null if not found).
     */
    public String getProperty(String key) {
        return mergedProperties.getProperty(key);
    }
    
    /**
     * Get property as integer.
     */
    public int getIntProperty(String key, int defaultValue) {
        String value = getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid integer value for {}: {}", key, value);
            }
        }
        return defaultValue;
    }
    
    /**
     * Get property as boolean.
     */
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = getProperty(key);
        if (value != null) {
            return Boolean.parseBoolean(value.trim());
        }
        return defaultValue;
    }
    
    /**
     * Get list of environment names from configuration.
     */
    public List<String> getEnvironmentNames() {
        String envNames = getProperty("environment.names", "local,dev,qa,uat,prod");
        return Arrays.asList(envNames.split(","));
    }
    
    /**
     * Get environment display name from UI labels.
     */
    public String getEnvironmentDisplayName(String envCode) {
        String key = "ui.env." + envCode.trim();
        return uiLabelsProperties.getProperty(key, capitalize(envCode));
    }
    
    /**
     * Get all environment configurations.
     */
    public List<EnvironmentConfig> getEnvironments() {
        List<String> envCodes = getEnvironmentNames();
        List<EnvironmentConfig> environments = new ArrayList<>();
        
        for (String code : envCodes) {
            String trimmedCode = code.trim();
            String displayName = getEnvironmentDisplayName(trimmedCode);
            environments.add(new EnvironmentConfig(trimmedCode, displayName));
        }
        
        return environments;
    }
    
    /**
     * Get all properties (defensive copy).
     */
    public Properties getAllProperties() {
        return new Properties(mergedProperties);
    }
    
    /**
     * Get framework properties only.
     */
    public Properties getFrameworkProperties() {
        return new Properties(frameworkProperties);
    }
    
    /**
     * Get UI label properties only.
     */
    public Properties getUILabelProperties() {
        return new Properties(uiLabelsProperties);
    }
    
    /**
     * Capitalize first letter of a string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * Environment configuration holder.
     */
    public static class EnvironmentConfig {
        private final String code;
        private final String displayName;
        
        public EnvironmentConfig(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        @Override
        public String toString() {
            return displayName + " (" + code + ")";
        }
    }
}
