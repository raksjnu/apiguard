package com.raks.filesync.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Loads and validates FileSync configuration from JSON files
 */
public class ConfigLoader {
    private final Gson gson;

    public ConfigLoader() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Load configuration from JSON file
     */
    public MappingConfig loadConfig(String configPath) throws IOException {
        if (!Files.exists(Paths.get(configPath))) {
            throw new IOException("Configuration file not found: " + configPath);
        }

        try (FileReader reader = new FileReader(configPath)) {
            MappingConfig config = gson.fromJson(reader, MappingConfig.class);
            validateConfig(config);
            return config;
        }
    }

    /**
     * Validate configuration
     */
    private void validateConfig(MappingConfig config) throws IOException {
        if (config == null) {
            throw new IOException("Invalid configuration: null");
        }

        if (config.getPaths() == null) {
            throw new IOException("Invalid configuration: paths section missing");
        }

        if (config.getPaths().getSourceDirectory() == null || config.getPaths().getSourceDirectory().isEmpty()) {
            throw new IOException("Invalid configuration: sourceDirectory not specified");
        }

        if (config.getPaths().getTargetDirectory() == null || config.getPaths().getTargetDirectory().isEmpty()) {
            throw new IOException("Invalid configuration: targetDirectory not specified");
        }

        if (config.getFileMappings() == null || config.getFileMappings().isEmpty()) {
            throw new IOException("Invalid configuration: no file mappings defined");
        }

        // Validate each file mapping
        for (FileMapping fileMapping : config.getFileMappings()) {
            if (fileMapping.getSourceFile() == null || fileMapping.getSourceFile().isEmpty()) {
                throw new IOException("Invalid file mapping: sourceFile not specified");
            }
            if (fileMapping.getTargetFile() == null || fileMapping.getTargetFile().isEmpty()) {
                throw new IOException("Invalid file mapping: targetFile not specified");
            }
            if (fileMapping.getFieldMappings() == null || fileMapping.getFieldMappings().isEmpty()) {
                throw new IOException("Invalid file mapping: no field mappings for " + fileMapping.getSourceFile());
            }
        }
    }

    /**
     * Create a default configuration template
     */
    public MappingConfig createDefaultConfig(String sourceDir, String targetDir) {
        MappingConfig config = new MappingConfig();
        config.getMetadata().put("created", java.time.LocalDate.now().toString());
        config.getMetadata().put("description", "FileSync mapping configuration");
        config.getPaths().setSourceDirectory(sourceDir);
        config.getPaths().setTargetDirectory(targetDir);
        return config;
    }
}
