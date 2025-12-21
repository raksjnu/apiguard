package com.raks.raksanalyzer.core.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Utility class for resolving Mule property placeholders.
 * Supports both ${property} and p('property') formats.
 * Can load properties from multiple .properties files with configurable patterns.
 * Thread-safe and reusable across different contexts.
 */
public class PropertyResolver {
    private static final Logger logger = LoggerFactory.getLogger(PropertyResolver.class);
    
    // Regex patterns for property placeholders
    private static final Pattern DOLLAR_BRACE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final Pattern P_FUNCTION_PATTERN = Pattern.compile("p\\('([^']+)'\\)");
    
    private final Map<String, String> properties;
    private final Set<String> resolvingKeys; // For circular reference detection
    
    /**
     * Creates a new PropertyResolver with empty properties.
     */
    public PropertyResolver() {
        this.properties = new LinkedHashMap<>();
        this.resolvingKeys = new HashSet<>();
    }
    
    /**
     * Creates a new PropertyResolver with the given properties.
     * @param properties Initial properties map
     */
    public PropertyResolver(Map<String, String> properties) {
        this.properties = new LinkedHashMap<>(properties);
        this.resolvingKeys = new HashSet<>();
    }
    
    /**
     * Loads properties from all .properties files matching the given pattern.
     * Pattern examples:
     * - "src/main/resources/*.properties" - All .properties in resources
     * - "src/main/resources/config/*.properties" - All .properties in config folder
     * 
     * @param projectRoot Root directory of the project
     * @param pattern Glob pattern for property files (relative to projectRoot)
     * @return Number of property files loaded
     */
    public int loadProperties(Path projectRoot, String pattern) {
        int filesLoaded = 0;
        
        try {
            // Convert glob pattern to path and filename pattern
            Path searchDir = projectRoot;
            String filePattern = pattern;
            
            if (pattern.contains("/")) {
                int lastSlash = pattern.lastIndexOf('/');
                String dirPath = pattern.substring(0, lastSlash);
                filePattern = pattern.substring(lastSlash + 1);
                searchDir = projectRoot.resolve(dirPath);
            }
            
            if (!Files.exists(searchDir)) {
                logger.warn("Property directory does not exist: {}", searchDir);
                return 0;
            }
            
            // Find all matching .properties files
            final String finalPattern = filePattern.replace("*", ".*");
            try (Stream<Path> paths = Files.walk(searchDir, 1)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> p.getFileName().toString().matches(finalPattern))
                     .forEach(propFile -> {
                         if (loadPropertiesFile(propFile)) {
                             logger.info("Loaded properties from: {}", propFile);
                         }
                     });
                filesLoaded++;
            }
            
        } catch (IOException e) {
            logger.error("Error loading properties from pattern: {}", pattern, e);
        }
        
        return filesLoaded;
    }
    
    /**
     * Loads properties from a single .properties file.
     * @param propertyFile Path to the .properties file
     * @return true if loaded successfully
     */
    public boolean loadPropertiesFile(Path propertyFile) {
        try (InputStream is = Files.newInputStream(propertyFile)) {
            Properties props = new Properties();
            props.load(is);
            
            // Merge into existing properties (later files override earlier ones)
            props.forEach((key, value) -> properties.put(key.toString(), value.toString()));
            
            return true;
        } catch (IOException e) {
            logger.error("Failed to load properties file: {}", propertyFile, e);
            return false;
        }
    }
    
    /**
     * Adds a single property.
     * @param key Property key
     * @param value Property value
     */
    public void setProperty(String key, String value) {
        properties.put(key, value);
    }
    
    /**
     * Adds multiple properties.
     * @param props Properties to add
     */
    public void setProperties(Map<String, String> props) {
        properties.putAll(props);
    }
    
    /**
     * Gets a property value by key.
     * @param key Property key
     * @return Property value or null if not found
     */
    public String getProperty(String key) {
        return properties.get(key);
    }
    
    /**
     * Gets a property value with a default.
     * @param key Property key
     * @param defaultValue Default value if property not found
     * @return Property value or default
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }
    
    /**
     * Resolves all property placeholders in the given value.
     * Supports both ${property} and p('property') formats.
     * Handles nested placeholders and circular references.
     * 
     * @param value Value containing property placeholders
     * @return Resolved value with all placeholders replaced
     */
    public String resolve(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        
        // Clear resolving keys for new resolution
        resolvingKeys.clear();
        
        return resolveInternal(value);
    }
    
    /**
     * Internal resolution method with circular reference detection.
     */
    private String resolveInternal(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        
        String result = value;
        
        // Resolve ${property} placeholders
        result = resolvePlaceholders(result, DOLLAR_BRACE_PATTERN);
        
        // Resolve p('property') placeholders
        result = resolvePlaceholders(result, P_FUNCTION_PATTERN);
        
        return result;
    }
    
    /**
     * Resolves placeholders matching the given pattern.
     */
    private String resolvePlaceholders(String value, Pattern pattern) {
        Matcher matcher = pattern.matcher(value);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String propertyKey = matcher.group(1);
            String replacement = resolvePropertyKey(propertyKey);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    /**
     * Resolves a single property key with circular reference detection.
     */
    private String resolvePropertyKey(String key) {
        // Check for circular reference
        if (resolvingKeys.contains(key)) {
            logger.warn("Circular property reference detected for key: {}", key);
            return "${" + key + "}"; // Return unresolved
        }
        
        String value = properties.get(key);
        
        if (value == null) {
            logger.debug("Property not found: {}", key);
            return "${" + key + "}"; // Return unresolved placeholder
        }
        
        // Check if value itself contains placeholders
        if (value.contains("${") || value.contains("p('")) {
            resolvingKeys.add(key);
            try {
                value = resolveInternal(value); // Recursive resolution
            } finally {
                resolvingKeys.remove(key);
            }
        }
        
        return value;
    }
    
    /**
     * Checks if a value contains any unresolved placeholders.
     * @param value Value to check
     * @return true if contains unresolved placeholders
     */
    public boolean hasUnresolvedPlaceholders(String value) {
        if (value == null) return false;
        return value.contains("${") || value.contains("p('");
    }
    
    /**
     * Gets all loaded properties (read-only view).
     * @return Unmodifiable map of all properties
     */
    public Map<String, String> getAllProperties() {
        return Collections.unmodifiableMap(properties);
    }
    
    /**
     * Gets the number of loaded properties.
     * @return Property count
     */
    public int getPropertyCount() {
        return properties.size();
    }
    
    /**
     * Clears all loaded properties.
     */
    public void clear() {
        properties.clear();
        resolvingKeys.clear();
    }
}
