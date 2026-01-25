package com.raks.gitanalyzer.core;

import java.io.InputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.TimeZone;

public class ConfigManager {
    private static final Properties properties = new Properties();
    private static final String APP_HOME = System.getProperty("app.home", ".");

    static {
        try (InputStream input = ConfigManager.class.getClassLoader().getResourceAsStream("gitanalyzer.properties")) {
            if (input == null) {
                System.out.println("No gitanalyzer.properties found, using defaults.");
            } else {
                properties.load(input);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static String get(String key) {
        return properties.getProperty(key);
    }
    
    public static String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public static File getAppHome() {
        return new File(APP_HOME);
    }

    public static File getTempDir() {
        return new File(getAppHome(), "temp");
    }

    /**
     * Resolves a directory path.
     * If 'userPath' is provided (e.g. from CLI/UI), use it.
     * If not, check 'propertyKey' in config.
     * If not set, default to {app.home}/temp
     */
    public static File resolveOutputDir(String userPath, String propertyKey) {
        String pathStr = userPath;
        if (pathStr == null || pathStr.isEmpty()) {
            pathStr = properties.getProperty(propertyKey);
        }

        if (pathStr == null || pathStr.isEmpty()) {
            return getTempDir();
        }

        File f = new File(pathStr);
        if (f.isAbsolute()) {
            return f;
        }
        return new File(getAppHome(), pathStr);
    }

    public static String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss z");
        // Timezone defaults to system, but explicit setting can be done here if needed
        // sdf.setTimeZone(TimeZone.getTimeZone("UTC")); 
        return sdf.format(new Date());
    }
    public static java.util.Map<String, String> getPropertiesByPrefix(String prefix) {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                result.put(key, properties.getProperty(key));
            }
        }
        return result;
    }

    public static void set(String key, String value) {
        if (value != null) {
            properties.setProperty(key, value);
        }
    }

    public static void setAll(java.util.Map<String, String> map) {
        if (map != null) {
            map.forEach((k, v) -> {
                if (v != null) properties.setProperty(k, v);
            });
        }
    }

    public static boolean isCloudHub() {
        // Common CloudHub indicators
        return System.getenv("CLOUDHUB_APP_NAME") != null || 
               System.getProperty("mule.env") != null ||
               "cloudhub".equalsIgnoreCase(System.getProperty("app.target"));
    }
}
