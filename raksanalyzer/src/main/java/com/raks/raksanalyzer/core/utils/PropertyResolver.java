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
public class PropertyResolver {
    private static final Logger logger = LoggerFactory.getLogger(PropertyResolver.class);
    private static final Pattern DOLLAR_BRACE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final Pattern P_FUNCTION_PATTERN = Pattern.compile("p\\('([^']+)'\\)");
    private final Map<String, String> properties;
    private final Set<String> resolvingKeys; 
    public PropertyResolver() {
        this.properties = new LinkedHashMap<>();
        this.resolvingKeys = new HashSet<>();
    }
    public PropertyResolver(Map<String, String> properties) {
        this.properties = new LinkedHashMap<>(properties);
        this.resolvingKeys = new HashSet<>();
    }
    public int loadProperties(Path projectRoot, String pattern) {
        int filesLoaded = 0;
        try {
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
    public boolean loadPropertiesFile(Path propertyFile) {
        try (InputStream is = Files.newInputStream(propertyFile)) {
            Properties props = new Properties();
            props.load(is);
            props.forEach((key, value) -> properties.put(key.toString(), value.toString()));
            return true;
        } catch (IOException e) {
            logger.error("Failed to load properties file: {}", propertyFile, e);
            return false;
        }
    }
    public int loadPropertiesFromFile(Path propertyFile) {
        int beforeCount = properties.size();
        if (loadPropertiesFile(propertyFile)) {
            int afterCount = properties.size();
            return afterCount - beforeCount;
        }
        return 0;
    }
    public void setProperty(String key, String value) {
        properties.put(key, value);
    }
    public void setProperties(Map<String, String> props) {
        properties.putAll(props);
    }
    public String getProperty(String key) {
        return properties.get(key);
    }
    public String getProperty(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }
    public String resolve(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        resolvingKeys.clear();
        return resolveInternal(value);
    }
    private String resolveInternal(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String result = value;
        result = resolvePlaceholders(result, DOLLAR_BRACE_PATTERN);
        result = resolvePlaceholders(result, P_FUNCTION_PATTERN);
        return result;
    }
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
    private String resolvePropertyKey(String key) {
        if (resolvingKeys.contains(key)) {
            logger.warn("Circular property reference detected for key: {}", key);
            return "${" + key + "}"; 
        }
        String value = properties.get(key);
        if (value == null) {
            logger.debug("Property not found: {}", key);
            return "${" + key + "}"; 
        }
        if (value.contains("${") || value.contains("p('")) {
            resolvingKeys.add(key);
            try {
                value = resolveInternal(value); 
            } finally {
                resolvingKeys.remove(key);
            }
        }
        return value;
    }
    public boolean hasUnresolvedPlaceholders(String value) {
        if (value == null) return false;
        return value.contains("${") || value.contains("p('");
    }
    public Map<String, String> getAllProperties() {
        return Collections.unmodifiableMap(properties);
    }
    public int getPropertyCount() {
        return properties.size();
    }
    public void clear() {
        properties.clear();
        resolvingKeys.clear();
    }
}
