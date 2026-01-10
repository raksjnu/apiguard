package com.raks.filesync.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root configuration class for FileSync mappings
 */
public class MappingConfig {
    private String version;
    private Map<String, String> metadata;
    private PathConfig paths;
    private List<FileMapping> fileMappings;
    private List<Object> rules; // For future rule engine

    public MappingConfig() {
        this.version = "1.0";
        this.metadata = new HashMap<>();
        this.paths = new PathConfig();
        this.fileMappings = new ArrayList<>();
        this.rules = new ArrayList<>();
    }

    // Getters and Setters
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public PathConfig getPaths() {
        return paths;
    }

    public void setPaths(PathConfig paths) {
        this.paths = paths;
    }

    public List<FileMapping> getFileMappings() {
        return fileMappings;
    }

    public void setFileMappings(List<FileMapping> fileMappings) {
        this.fileMappings = fileMappings;
    }

    public List<Object> getRules() {
        return rules;
    }

    public void setRules(List<Object> rules) {
        this.rules = rules;
    }

    /**
     * Nested class for path configuration
     */
    public static class PathConfig {
        private String sourceDirectory;
        private String targetDirectory;

        public PathConfig() {
            this.sourceDirectory = "";
            this.targetDirectory = "";
        }

        public String getSourceDirectory() {
            return sourceDirectory;
        }

        public void setSourceDirectory(String sourceDirectory) {
            this.sourceDirectory = sourceDirectory;
        }

        public String getTargetDirectory() {
            return targetDirectory;
        }

        public void setTargetDirectory(String targetDirectory) {
            this.targetDirectory = targetDirectory;
        }
    }
}
