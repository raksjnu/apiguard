package com.raks.filesync.gui;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Theme configuration for FileSync GUI (ApiGuard Purple Theme)
 */
public class ThemeConfig {
    private static final Properties props = new Properties();
    
    // Theme Colors
    public static final Color PRIMARY_COLOR;
    public static final Color PRIMARY_DARK;
    public static final Color PRIMARY_LIGHT;
    public static final Color SECONDARY_COLOR;
    public static final Color BACKGROUND_COLOR;
    public static final Color PANEL_BACKGROUND;
    public static final Color BORDER_COLOR;
    public static final Color TEXT_PRIMARY;
    public static final Color TEXT_SECONDARY;
    public static final Color BUTTON_BACKGROUND;
    public static final Color BUTTON_TEXT;
    public static final Color BUTTON_BORDER;
    public static final Color HEADER_BACKGROUND;
    public static final Color HEADER_TEXT;
    
    static {
        try (InputStream is = ThemeConfig.class.getResourceAsStream("/ui.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            System.err.println("Failed to load ui.properties: " + e.getMessage());
        }
        
        // Initialize colors
        PRIMARY_COLOR = parseColor(props.getProperty("theme.primary.color", "#6B46C1"));
        PRIMARY_DARK = parseColor(props.getProperty("theme.primary.dark", "#553C9A"));
        PRIMARY_LIGHT = parseColor(props.getProperty("theme.primary.light", "#805AD5"));
        SECONDARY_COLOR = parseColor(props.getProperty("theme.secondary.color", "#D6BCFA"));
        BACKGROUND_COLOR = parseColor(props.getProperty("theme.background.color", "#FFFFFF"));
        PANEL_BACKGROUND = parseColor(props.getProperty("theme.panel.background", "#FAF5FF"));
        BORDER_COLOR = parseColor(props.getProperty("theme.border.color", "#6B46C1"));
        TEXT_PRIMARY = parseColor(props.getProperty("theme.text.primary", "#2D3748"));
        TEXT_SECONDARY = parseColor(props.getProperty("theme.text.secondary", "#718096"));
        BUTTON_BACKGROUND = parseColor(props.getProperty("theme.button.background", "#6B46C1"));
        BUTTON_TEXT = parseColor(props.getProperty("theme.button.text", "#FFFFFF"));
        BUTTON_BORDER = parseColor(props.getProperty("theme.button.border", "#553C9A"));
        HEADER_BACKGROUND = parseColor(props.getProperty("theme.header.background", "#6B46C1"));
        HEADER_TEXT = parseColor(props.getProperty("theme.header.text", "#FFFFFF"));
    }
    
    private static Color parseColor(String hex) {
        try {
            return Color.decode(hex);
        } catch (Exception e) {
            return Color.BLACK;
        }
    }
    
    public static String getString(String key) {
        return props.getProperty(key, key);
    }
    
    public static String getString(String key, Object... args) {
        String value = props.getProperty(key, key);
        return String.format(value.replace("{0}", "%s").replace("{1}", "%s").replace("{2}", "%s"), args);
    }
    
    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
