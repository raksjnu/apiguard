package com.raks.apidiscovery.model;

import java.util.List;

public class RuleConfig {
    private List<Rule> rules;
    private List<MetadataRule> metadata_rules;

    public List<Rule> getRules() { return rules; }
    public void setRules(List<Rule> rules) { this.rules = rules; }
    
    public List<MetadataRule> getMetadataRules() { return metadata_rules; }
    public void setMetadataRules(List<MetadataRule> metadata_rules) { this.metadata_rules = metadata_rules; }

    public static class Rule {
        private String technology;
        private List<Marker> project_markers;
        private List<Indicator> api_indicators;

        public String getTechnology() { return technology; }
        public List<Marker> getProjectMarkers() { return project_markers; }
        public List<Indicator> getApiIndicators() { return api_indicators; }
    }

    public static class Marker {
        private String file;
        private String content; // Optional regex/substring

        public String getFile() { return file; }
        public String getContent() { return content; }
    }

    public static class Indicator {
        private String file;
        private String content;
        private int score;
        private String description;

        public String getFile() { return file; }
        public String getContent() { return content; }
        public int getScore() { return score; }
        public String getDescription() { return description; }
    }
    
    public static class MetadataRule {
        private String category;    // e.g. "Security", "Logging"
        private String file;        // e.g. "pom.xml"
        private String content;     // e.g. "spring-security"
        private String value;       // e.g. "Implemented"
        
        public String getCategory() { return category; }
        public String getFile() { return file; }
        public String getContent() { return content; }
        public String getValue() { return value; }
    }
}
