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
}
