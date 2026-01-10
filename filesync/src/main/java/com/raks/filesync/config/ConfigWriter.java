package com.raks.filesync.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileWriter;
import java.io.IOException;

/**
 * Writes FileSync configuration to JSON files
 */
public class ConfigWriter {
    private final Gson gson;

    public ConfigWriter() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Save configuration to JSON file
     */
    public void saveConfig(MappingConfig config, String configPath) throws IOException {
        try (FileWriter writer = new FileWriter(configPath)) {
            gson.toJson(config, writer);
        }
    }

    /**
     * Convert configuration to JSON string
     */
    public String toJson(MappingConfig config) {
        return gson.toJson(config);
    }
}
