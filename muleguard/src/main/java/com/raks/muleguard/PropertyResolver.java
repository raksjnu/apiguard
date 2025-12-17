package com.raks.muleguard;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class PropertyResolver {

    private final Properties properties = new Properties();

    /**
     * Scans src/main/resources for .properties files and loads them.
     * 
     * @param projectDir The root directory of the Mule project.
     */
    public PropertyResolver(Path projectDir) {
        Path resourcesDir = projectDir.resolve("src/main/resources");
        if (!Files.isDirectory(resourcesDir)) {
            return; // No properties to load
        }

        try (Stream<Path> files = Files.walk(resourcesDir)) {
            files.filter(file -> file.toString().toLowerCase().endsWith(".properties"))
                    .forEach(this::loadPropertiesFromFile);
        } catch (IOException e) {
            System.err.println("Warning: Could not scan for property files: " + e.getMessage());
        }
    }

    private void loadPropertiesFromFile(Path propertyFile) {
        try (InputStream input = new FileInputStream(propertyFile.toFile())) {
            properties.load(input);
        } catch (IOException e) {
            System.err.println("Warning: Could not load property file: " + propertyFile + ". Error: " + e.getMessage());
        }
    }

    /**
     * Resolves a value, which can be a direct value or a property placeholder like
     * ${key}.
     * 
     * @param value The string value to resolve.
     * @return The resolved property value, or the original value if it's not a
     *         placeholder.
     */
    public String resolve(String value) {
        if (value == null) {
            return null;
        }

        // Pattern for ${property.name}
        Pattern dollarPattern = Pattern.compile("^\\$\\{([^}]+)\\}$");
        Matcher dollarMatcher = dollarPattern.matcher(value.trim());
        if (dollarMatcher.matches()) {
            String key = dollarMatcher.group(1);
            String resolved = properties.getProperty(key);
            // Return null if property not found (instead of returning the placeholder)
            return resolved;
        }

        // Pattern for #[p('property.name')] or #[p("property.name")] with optional
        // whitespace
        Pattern pFunctionPattern = Pattern.compile("^#\\[\\s*p\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)\\s*\\]$");
        Matcher pFunctionMatcher = pFunctionPattern.matcher(value.trim());
        if (pFunctionMatcher.matches()) {
            String key = pFunctionMatcher.group(1);
            String resolved = properties.getProperty(key);
            // Return null if property not found (instead of returning the placeholder)
            return resolved;
        }

        return value; // Not a placeholder, return original value
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }
}