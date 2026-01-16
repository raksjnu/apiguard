package com.raks.aegis;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class PropertyResolver {
    private static final Logger logger = LoggerFactory.getLogger(PropertyResolver.class);
    private final Properties properties = new Properties();
    public PropertyResolver(Path projectDir) {
        Path resourcesDir = projectDir.resolve("src/main/resources");
        if (!Files.isDirectory(resourcesDir)) {
            return; 
        }
        try (Stream<Path> files = Files.walk(resourcesDir)) {
            files.filter(file -> file.toString().toLowerCase().endsWith(".properties"))
                    .forEach(this::loadPropertiesFromFile);
        } catch (IOException e) {
            logger.warn("Could not scan for property files: {}", e.getMessage());
        }
    }
    private void loadPropertiesFromFile(Path propertyFile) {
        try (InputStream input = new FileInputStream(propertyFile.toFile())) {
            properties.load(input);
        } catch (IOException e) {
            logger.warn("Could not load property file: {}. Error: {}", propertyFile, e.getMessage());
        }
    }
    public String resolve(String value) {
        if (value == null) {
            return null;
        }
        Pattern dollarPattern = Pattern.compile("^\\$\\{([^}]+)\\}$");
        Matcher dollarMatcher = dollarPattern.matcher(value.trim());
        if (dollarMatcher.matches()) {
            String key = dollarMatcher.group(1);
            String resolved = properties.getProperty(key);
            return resolved;
        }
        Pattern pFunctionPattern = Pattern.compile("^#\\[\\s*p\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)\\s*\\]$");
        Matcher pFunctionMatcher = pFunctionPattern.matcher(value.trim());
        if (pFunctionMatcher.matches()) {
            String key = pFunctionMatcher.group(1);
            String resolved = properties.getProperty(key);
            return resolved;
        }
        return value; 
    }
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
}
