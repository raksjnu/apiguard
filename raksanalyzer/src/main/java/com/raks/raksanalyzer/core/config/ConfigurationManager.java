package com.raks.raksanalyzer.core.config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
public class ConfigurationManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationManager.class);
    private static ConfigurationManager instance;
    private final Properties frameworkProperties;
    private final Properties mergedProperties;
    private ConfigurationManager() {
        this.frameworkProperties = loadPropertiesFile("config/defaults/framework.properties");
        this.mergedProperties = new Properties();
        mergedProperties.putAll(frameworkProperties);
        loadEnvironmentVariables();
        logger.info("Configuration loaded: {} total properties", mergedProperties.size());
    }
    public static synchronized ConfigurationManager getInstance() {
        if (instance == null) {
            instance = new ConfigurationManager();
        }
        return instance;
    }
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
    private void loadEnvironmentVariables() {
        Map<String, String> env = System.getenv();
        int count = 0;
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("RAKSANALYZER_")) {
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
    public String getProperty(String key, String defaultValue) {
        return mergedProperties.getProperty(key, defaultValue);
    }
    public String getProperty(String key) {
        return mergedProperties.getProperty(key);
    }
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
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = getProperty(key);
        if (value != null) {
            return Boolean.parseBoolean(value.trim());
        }
        return defaultValue;
    }
    public List<String> getEnvironmentNames() {
        String envNames = getProperty("environment.names", "local,dev,qa,uat,prod");
        return Arrays.asList(envNames.split(","));
    }
    public String getEnvironmentDisplayName(String envCode) {
        String key = "ui.env." + envCode.trim();
        return mergedProperties.getProperty(key, capitalize(envCode));
    }
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
    public Properties getAllProperties() {
        return new Properties(mergedProperties);
    }
    public Properties getFrameworkProperties() {
        return new Properties(frameworkProperties);
    }
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
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
