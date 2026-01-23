package com.raks.aegis.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class PropertyResolver {
    private static final Logger logger = LoggerFactory.getLogger(PropertyResolver.class);
    
    // Cache: ProjectRoot -> { PropertyKey -> { SourceFileName -> Value } }
    private static final Map<Path, Map<String, Map<String, String>>> projectPropertiesCache = new HashMap<>();

    private static List<Pattern> resolutionPatterns = new ArrayList<>();

    static {
        resolutionPatterns.add(Pattern.compile("\\$\\{([^}]+)}")); 
        resolutionPatterns.add(Pattern.compile("p\\(['\"]([^'\"]+)['\"]\\)")); 
    }

    public static synchronized void configure(List<String> regexPatterns) {
        if (regexPatterns == null || regexPatterns.isEmpty()) return;
        resolutionPatterns.clear();
        for (String regex : regexPatterns) {
            try {
                resolutionPatterns.add(Pattern.compile(regex));
            } catch (Exception e) {
                logger.error("Invalid property syntax regex: {}", regex, e);
            }
        }
    }

    public static synchronized void loadProperties(Path projectRoot) {
        if (projectPropertiesCache.containsKey(projectRoot)) return;

        Map<String, Map<String, String>> multiProps = new HashMap<>();
        try (Stream<Path> walk = Files.walk(projectRoot)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".properties"))
                .filter(p -> {
                    String pathStr = p.toString().replace('\\', '/');
                    return !pathStr.contains("/target/") && 
                           !pathStr.contains("/bin/") && 
                           !pathStr.contains("/build/") && 
                           !pathStr.contains("/.git/") &&
                           !pathStr.contains("/.idea/");
                })
                .forEach(p -> {
                    String sourceName = projectRoot.relativize(p).toString();
                    try (InputStream is = Files.newInputStream(p)) {
                        Properties pObj = new Properties();
                        pObj.load(is);
                        for (String name : pObj.stringPropertyNames()) {
                            multiProps.computeIfAbsent(name, k -> new TreeMap<>())
                                      .put(sourceName, pObj.getProperty(name));
                        }
                    } catch (IOException e) {
                        logger.warn("Failed to load properties from {}: {}", p, e.getMessage());
                    }
                });
        } catch (IOException e) {
            logger.error("Error walking project for properties: {}", e.getMessage());
        }

        projectPropertiesCache.put(projectRoot, multiProps);
    }

    public static String resolve(String val, Path projectRoot) {
        return resolve(val, projectRoot, null);
    }

    /**
     * The single source of truth for resolving project-level properties with priority and labeling.
     */
    public static String resolveProjectProperty(String value, Path projectRoot, Path linkedConfigPath, boolean includeLinked, List<String> resolutionCollector) {
        java.util.Set<String> all = resolveProjectPropertyAll(value, projectRoot, linkedConfigPath, includeLinked, resolutionCollector);
        return all.isEmpty() ? value : all.iterator().next(); 
    }

    /**
     * Resolves all possible unique combinations of placeholders in the value.
     */
    public static java.util.Set<String> resolveProjectPropertyAll(String value, Path projectRoot, Path linkedConfigPath, boolean includeLinked, List<String> resolutionCollector) {
        if (value == null) return java.util.Collections.emptySet();

        java.util.LinkedHashMap<Path, String> rootsWithLabels = new java.util.LinkedHashMap<>();
        if (includeLinked && linkedConfigPath != null) {
            rootsWithLabels.put(linkedConfigPath, "CONFIG - ");
        }
        if (projectRoot != null && !projectRoot.equals(linkedConfigPath)) {
            rootsWithLabels.put(projectRoot, "");
        }

        if (rootsWithLabels.isEmpty()) return java.util.Collections.singleton(value);

        // Ensure all roots are loaded
        for (Path root : rootsWithLabels.keySet()) {
            loadProperties(root);
        }

        java.util.Set<String> currentValues = new java.util.HashSet<>();
        currentValues.add(value);
        boolean modified = true;
        int safetyCounter = 0;

        while (modified && safetyCounter < 10) {
            modified = false;
            java.util.Set<String> nextValues = new java.util.HashSet<>();

            for (String val : currentValues) {
                boolean valModified = false;
                for (Pattern pattern : resolutionPatterns) {
                    Matcher m = pattern.matcher(val);
                    if (m.find()) {
                        modified = true;
                        valModified = true;
                        String fullPlaceholder = m.group(0);
                        String key = m.group(1);
                        java.util.Set<String> replacements = new java.util.HashSet<>();

                        // Search in all prioritized roots
                        for (Map.Entry<Path, String> entry : rootsWithLabels.entrySet()) {
                            Path root = entry.getKey();
                            String label = entry.getValue();
                            Map<String, Map<String, String>> multiProps = projectPropertiesCache.get(root);
                            
                            if (multiProps != null && multiProps.containsKey(key)) {
                                Map<String, String> sources = multiProps.get(key);
                                for (Map.Entry<String, String> srcEntry : sources.entrySet()) {
                                    replacements.add(srcEntry.getValue());
                                    
                                    if (resolutionCollector != null) {
                                        String sourceFilePath = srcEntry.getKey();
                                        Path fullSourcePath = root.resolve(sourceFilePath);
                                        String displayedPath;
                                        Path rootParent = root.getParent();
                                        if (rootParent != null) {
                                            displayedPath = rootParent.relativize(fullSourcePath).toString();
                                        } else {
                                            displayedPath = root.getFileName().resolve(sourceFilePath).toString();
                                        }
                                        String mapping = fullPlaceholder + " → " + srcEntry.getValue() + " (" + label + displayedPath + ")";
                                        if (!resolutionCollector.contains(mapping)) {
                                            resolutionCollector.add(mapping);
                                        }
                                    }
                                }
                            }
                        }

                        if (replacements.isEmpty()) {
                            // Mark as NOT RESOLVED if no root has this property
                            if (resolutionCollector != null) {
                                String mapping = fullPlaceholder + " → NOT RESOLVED";
                                if (!resolutionCollector.contains(mapping)) {
                                    resolutionCollector.add(mapping);
                                }
                            }
                            nextValues.add(val); 
                            valModified = false; 
                        } else {
                            for (String r : replacements) {
                                nextValues.add(val.replace(fullPlaceholder, r));
                            }
                        }
                        break; // Process one placeholder at a time per value
                    }
                }
                if (!valModified) {
                    nextValues.add(val);
                }
            }
            currentValues = nextValues;
            if (modified) safetyCounter++;
        }
        return currentValues;
    }

    // Deprecated methods linked to resolveProjectProperty to avoid code duplication
    public static String resolve(String val, Path projectRoot, List<String> resolutions) {
        return resolveProjectProperty(val, projectRoot, null, false, resolutions);
    }

    public static String resolve(String val, Map<Path, String> rootsWithLabels, List<String> resolutions) {
        if (val == null) return null;
        String resolved = val;
        for (Map.Entry<Path, String> entry : rootsWithLabels.entrySet()) {
            boolean isConfig = "CONFIG - ".equals(entry.getValue());
            resolved = resolveProjectProperty(resolved, entry.getKey(), isConfig ? entry.getKey() : null, isConfig, resolutions);
        }
        return resolved;
    }

    public static void clearCache() {
        projectPropertiesCache.clear();
    }
}
