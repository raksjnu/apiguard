package com.raks.filesync.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/**
 * Manages user preferences and session persistence
 */
public class UserPreferences {
    private static final Preferences prefs = Preferences.userNodeForPackage(UserPreferences.class);
    
    // Preference keys
    private static final String KEY_LAST_SOURCE_DIR = "last.source.directory";
    private static final String KEY_LAST_TARGET_DIR = "last.target.directory";
    private static final String KEY_LAST_CONFIG_FILE = "last.config.file";
    private static final String KEY_WINDOW_WIDTH = "window.width";
    private static final String KEY_WINDOW_HEIGHT = "window.height";
    private static final String KEY_WINDOW_X = "window.x";
    private static final String KEY_WINDOW_Y = "window.y";
    private static final String KEY_TARGET_FILE_HISTORY = "target.file.history";
    private static final String KEY_TARGET_FIELD_HISTORY = "target.field.history";
    
    private static final String DELIMITER = "|||";
    private static final int MAX_HISTORY_SIZE = 50;
    
    /**
     * Save last used source directory
     */
    public static void setLastSourceDirectory(String path) {
        if (path != null && !path.trim().isEmpty()) {
            prefs.put(KEY_LAST_SOURCE_DIR, path);
        }
    }
    
    /**
     * Get last used source directory
     */
    public static String getLastSourceDirectory() {
        return prefs.get(KEY_LAST_SOURCE_DIR, "");
    }
    
    /**
     * Save last used target directory
     */
    public static void setLastTargetDirectory(String path) {
        if (path != null && !path.trim().isEmpty()) {
            prefs.put(KEY_LAST_TARGET_DIR, path);
        }
    }
    
    /**
     * Get last used target directory
     */
    public static String getLastTargetDirectory() {
        return prefs.get(KEY_LAST_TARGET_DIR, "");
    }
    
    /**
     * Save last used configuration file
     */
    public static void setLastConfigFile(String path) {
        if (path != null && !path.trim().isEmpty()) {
            prefs.put(KEY_LAST_CONFIG_FILE, path);
        }
    }
    
    /**
     * Get last used configuration file
     */
    public static String getLastConfigFile() {
        return prefs.get(KEY_LAST_CONFIG_FILE, "");
    }
    
    /**
     * Add a target file name to history
     */
    public static void addTargetFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return;
        }
        addToHistory(KEY_TARGET_FILE_HISTORY, fileName);
    }
    
    /**
     * Get target file name history
     */
    public static List<String> getTargetFileHistory() {
        return getHistory(KEY_TARGET_FILE_HISTORY);
    }
    
    /**
     * Add a target field name to history
     */
    public static void addTargetFieldName(String fieldName) {
        if (fieldName == null || fieldName.trim().isEmpty()) {
            return;
        }
        addToHistory(KEY_TARGET_FIELD_HISTORY, fieldName);
    }
    
    /**
     * Get target field name history
     */
    public static List<String> getTargetFieldHistory() {
        return getHistory(KEY_TARGET_FIELD_HISTORY);
    }
    
    /**
     * Add an item to history (generic method)
     */
    private static void addToHistory(String key, String item) {
        List<String> history = getHistory(key);
        
        // Remove if already exists (to move to front)
        history.remove(item);
        
        // Add to front
        history.add(0, item);
        
        // Limit size
        if (history.size() > MAX_HISTORY_SIZE) {
            history = history.subList(0, MAX_HISTORY_SIZE);
        }
        
        // Save
        String joined = String.join(DELIMITER, history);
        prefs.put(key, joined);
    }
    
    /**
     * Get history list (generic method)
     */
    private static List<String> getHistory(String key) {
        String stored = prefs.get(key, "");
        if (stored.isEmpty()) {
            return new ArrayList<>();
        }
        
        return Arrays.stream(stored.split(DELIMITER))
                .filter(s -> !s.trim().isEmpty())
                .collect(Collectors.toList());
    }
    
    /**
     * Save window size
     */
    public static void setWindowSize(int width, int height) {
        prefs.putInt(KEY_WINDOW_WIDTH, width);
        prefs.putInt(KEY_WINDOW_HEIGHT, height);
    }
    
    /**
     * Get window width
     */
    public static int getWindowWidth(int defaultWidth) {
        return prefs.getInt(KEY_WINDOW_WIDTH, defaultWidth);
    }
    
    /**
     * Get window height
     */
    public static int getWindowHeight(int defaultHeight) {
        return prefs.getInt(KEY_WINDOW_HEIGHT, defaultHeight);
    }
    
    /**
     * Save window position
     */
    public static void setWindowPosition(int x, int y) {
        prefs.putInt(KEY_WINDOW_X, x);
        prefs.putInt(KEY_WINDOW_Y, y);
    }
    
    /**
     * Get window X position
     */
    public static int getWindowX(int defaultX) {
        return prefs.getInt(KEY_WINDOW_X, defaultX);
    }
    
    /**
     * Get window Y position
     */
    public static int getWindowY(int defaultY) {
        return prefs.getInt(KEY_WINDOW_Y, defaultY);
    }
    
    /**
     * Clear all preferences
     */
    public static void clearAll() {
        try {
            prefs.clear();
        } catch (Exception e) {
            System.err.println("Failed to clear preferences: " + e.getMessage());
        }
    }
}
