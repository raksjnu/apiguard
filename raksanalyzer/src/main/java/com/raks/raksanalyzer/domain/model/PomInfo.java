package com.raks.raksanalyzer.domain.model;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class PomInfo {
    private String modelVersion;
    private String groupId;
    private String artifactId;
    private String version;
    private String name;
    private String description;
    private String packaging;
    private String appRuntime;
    private ParentInfo parent;
    private Map<String, String> properties = new HashMap<>();
    private List<DependencyInfo> dependencies = new ArrayList<>();
    private List<PluginInfo> plugins = new ArrayList<>();
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPackaging() { return packaging; }
    public void setPackaging(String packaging) { this.packaging = packaging; }
    public String getAppRuntime() { return appRuntime; }
    public void setAppRuntime(String appRuntime) { this.appRuntime = appRuntime; }
    public ParentInfo getParent() { return parent; }
    public void setParent(ParentInfo parent) { this.parent = parent; }
    public Map<String, String> getProperties() { return properties; }
    public void setProperties(Map<String, String> properties) { this.properties = properties; }
    public List<DependencyInfo> getDependencies() { return dependencies; }
    public void setDependencies(List<DependencyInfo> dependencies) { this.dependencies = dependencies; }
    public List<PluginInfo> getPlugins() { return plugins; }
    public void setPlugins(List<PluginInfo> plugins) { this.plugins = plugins; }
    public static class ParentInfo {
        private String groupId;
        private String artifactId;
        private String version;
        private String relativePath;
        public ParentInfo(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }
        public String getGroupId() { return groupId; }
        public String getArtifactId() { return artifactId; }
        public String getVersion() { return version; }
        public String getRelativePath() { return relativePath; }
        public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
    }
    public static class DependencyInfo {
        private String groupId;
        private String artifactId;
        private String version;
        private String classifier;
        public DependencyInfo(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }
        public String getGroupId() { return groupId; }
        public String getArtifactId() { return artifactId; }
        public String getVersion() { return version; }
        public String getClassifier() { return classifier; }
        public void setClassifier(String classifier) { this.classifier = classifier; }
    }
    public static class PluginInfo {
        private String groupId;
        private String artifactId;
        private String version;
        public PluginInfo(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }
        public String getGroupId() { return groupId; }
        public String getArtifactId() { return artifactId; }
        public String getVersion() { return version; }
    }
}
