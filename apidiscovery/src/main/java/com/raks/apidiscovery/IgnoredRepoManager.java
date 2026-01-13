package com.raks.apidiscovery;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IgnoredRepoManager {
    private static final String IGNORE_FILE_NAME = "ignore-list.json";
    private static final Gson gson = new Gson();
    private static final Set<String> ignoredSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static File ignoreFile;

    static {
        // Initialize 
        String customHome = System.getProperty("apidiscovery.home");
        File baseDir = (customHome != null && !customHome.isEmpty()) ? new File(customHome) : new File(".");
        ignoreFile = new File(baseDir, IGNORE_FILE_NAME);
        load();
    }

    private static void load() {
        if (!ignoreFile.exists()) return;
        try {
            String json = Files.readString(ignoreFile.toPath());
            List<String> list = gson.fromJson(json, new TypeToken<List<String>>(){}.getType());
            if (list != null) {
                ignoredSet.addAll(list);
            }
        } catch (IOException e) {
            System.err.println("Failed to load ignore list: " + e.getMessage());
        }
    }

    private static void save() {
        try {
            String json = gson.toJson(new ArrayList<>(ignoredSet));
            Files.writeString(ignoreFile.toPath(), json);
        } catch (IOException e) {
            System.err.println("Failed to save ignore list: " + e.getMessage());
        }
    }

    public static boolean isIgnored(String urlOrPath) {
        if (urlOrPath == null) return false;
        // Check exact match or if the path ends with the ignored item (e.g. project name)
        // Simplest: Check exact match of full URL/Path
        return ignoredSet.contains(urlOrPath.trim());
    }

    public static void add(String urlOrPath) {
        if (urlOrPath != null && !urlOrPath.trim().isEmpty()) {
            ignoredSet.add(urlOrPath.trim());
            save();
        }
    }

    public static void remove(String urlOrPath) {
        if (urlOrPath != null) {
            ignoredSet.remove(urlOrPath.trim());
            save();
        }
    }

    public static List<String> getList() {
        return new ArrayList<>(ignoredSet);
    }
}
