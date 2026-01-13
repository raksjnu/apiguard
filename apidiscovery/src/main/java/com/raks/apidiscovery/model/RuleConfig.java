package com.raks.apidiscovery.model;
import java.util.List;
public class RuleConfig {
    private List<Rule> rules;
    private List<MetadataRule> metadata_rules;
    public List<Rule> getRules() { return rules; }
    public void setRules(List<Rule> rules) { this.rules = rules; }
    public List<MetadataRule> getMetadataRules() { return metadata_rules; }
    public void setMetadataRules(List<MetadataRule> metadata_rules) { this.metadata_rules = metadata_rules; }
    
    // Extraction Rules
    private List<ExtractionRule> extraction_rules;
    public List<ExtractionRule> getExtractionRules() { return extraction_rules; }
    public void setExtractionRules(List<ExtractionRule> extraction_rules) { this.extraction_rules = extraction_rules; }

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
        private String content; 
        public String getFile() { return file; }
        public String getContent() { return content; }
    }
    public static class Indicator {
        private String file;
        private String content;
        private int score;
        private String description;
        private String extractionRegex;
        private String contentRegex;
        public String getFile() { return file; }
        public String getContent() { return content; }
        public int getScore() { return score; }
        public String getDescription() { return description; }
        public String getExtractionRegex() { return extractionRegex; }
        public String getContentRegex() { return contentRegex; }
    }
    public static class MetadataRule {
        private String category;    
        private String file;        
        private String content;     
        private String value;       
        private boolean caseInsensitive = true; 
        public String getCategory() { return category; }
        public String getFile() { return file; }
        public String getContent() { return content; }
        public String getValue() { return value; }
        public boolean isCaseInsensitive() { return caseInsensitive; }
    }
    public static class ExtractionRule {
        private String category;
        private String file;
        private String regex;
        private String description;
        public String getCategory() { return category; }
        public String getFile() { return file; }
        public String getRegex() { return regex; }
        public String getDescription() { return description; }
    }
}
