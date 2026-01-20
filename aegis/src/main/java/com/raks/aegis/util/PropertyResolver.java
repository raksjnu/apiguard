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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class PropertyResolver {
    private static final Logger logger = LoggerFactory.getLogger(PropertyResolver.class);
    private static final Map<Path, Map<String, String>> projectPropertiesCache = new HashMap<>();

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

        Map<String, String> props = new HashMap<>();
        try (Stream<Path> walk = Files.walk(projectRoot)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".properties"))
                .forEach(p -> {
                    try (InputStream is = Files.newInputStream(p)) {
                        Properties pObj = new Properties();
                        pObj.load(is);
                        for (String name : pObj.stringPropertyNames()) {
                            props.put(name, pObj.getProperty(name));
                        }
                    } catch (IOException e) {
                        logger.warn("Failed to load properties from {}: {}", p, e.getMessage());
                    }
                });
        } catch (IOException e) {
            logger.error("Error walking project for properties: {}", e.getMessage());
        }

        projectPropertiesCache.put(projectRoot, props);
    }

    public static String resolve(String val, Path projectRoot) {
        return resolve(val, projectRoot, null);
    }

    public static String resolve(String val, Path projectRoot, List<String> resolutions) {
        if (val == null) return null;

        loadProperties(projectRoot);
        Map<String, String> props = projectPropertiesCache.get(projectRoot);
        if (props == null || props.isEmpty()) {
            return val;
        }

        String currentVal = val;
        boolean modified = true;
        int safetyCounter = 0; 

        while (modified && safetyCounter < 10) {
            modified = false;
            for (Pattern pattern : resolutionPatterns) {
                 Matcher m = pattern.matcher(currentVal);
                 StringBuffer sb = new StringBuffer();
                 while (m.find()) {
                     String key = m.group(1);
                     if (props.containsKey(key)) {
                         String replacement = props.get(key);
                         
                         if (resolutions != null) {
                             String mapping = m.group(0) + " → " + replacement;
                             if (!resolutions.contains(mapping)) {
                                 resolutions.add(mapping);
                             }
                         }

                         m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                         modified = true;
                     } else {
                         m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                     }
                 }
                 m.appendTail(sb);
                 currentVal = sb.toString();
                 if (modified) break; 
            }
            if (modified) safetyCounter++;
        }
        return currentVal;
    }

    public static void clearCache() {
        projectPropertiesCache.clear();
    }
}
