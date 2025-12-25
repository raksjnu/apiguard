package com.raks.raksanalyzer.generator;

import com.raks.raksanalyzer.core.config.ConfigurationManager;
import com.raks.raksanalyzer.domain.model.*;
import com.raks.raksanalyzer.domain.model.tibco.*;

import java.util.*;

/**
 * Shared helper class for TIBCO document generation.
 * Contains common logic used by both Word and PDF generators.
 */
public class TibcoDocumentHelper {
    
    private final ConfigurationManager config;
    
    public TibcoDocumentHelper() {
        this.config = ConfigurationManager.getInstance();
    }
    
    // ========== Process Type Detection ==========
    
    /**
     * Check if a flow is a starter process.
     */
    public boolean isStarterProcess(FlowInfo flow) {
        return "starter-process".equals(flow.getType());
    }
    
    /**
     * Extract raw starter type from flow description.
     */
    public String getRawStarterType(FlowInfo flow) {
        if (flow.getDescription() != null && flow.getDescription().contains("Type: ")) {
            return flow.getDescription().substring(flow.getDescription().indexOf("Type: ") + 6).trim();
        }
        return "";
    }
    
    /**
     * Convert raw starter type to friendly display name.
     */
    public String getFriendlyStarterType(String rawType) {
        if (rawType.contains("FileEventSource")) return "File Poller";
        if (rawType.contains("JMSQueueEventSource")) return "JMS Queue Receiver";
        if (rawType.contains("JMSTopicEventSource")) return "JMS Topic Subscriber";
        if (rawType.contains("TimerEventSource")) return "Timer";
        if (rawType.contains("SOAPEventSource")) return "SOAP Service";
        if (rawType.contains("AESubscriberActivity")) return "Adapter Subscriber";
        if (rawType.contains("AERPCServerActivity")) return "Adapter Service";
        if (rawType.contains("HTTPEventSource")) return "HTTP Receiver";
        if (rawType.contains("RendezvousSubscriber")) return "RV Subscriber";
        if (rawType.contains("OnStartupEventSource")) return "On-Start Up";
        if (rawType.contains("RestAdapterActivity")) return "REST Service";
        if (rawType.isEmpty()) return "Unknown";
        return rawType;
    }
    
    /**
     * Get interesting configuration keys for a specific starter type.
     * Returns null to show all keys.
     */
    public List<String> getInterestingKeysForType(String typeTitle) {
        if (typeTitle.contains("File")) {
            return Arrays.asList("fileName", "pollInterval", "mode", "encoding", "sortby", "includeCurrent", "excludePattern");
        } else if (typeTitle.contains("Timer") || typeTitle.contains("Scheduler")) {
            return Arrays.asList("StartTime", "Frequency", "TimeInterval", "FrequencyIndex");
        } else if (typeTitle.contains("JMS")) {
            return Arrays.asList("SessionAttributes.destination", "ConfigurableHeaders.JMSDeliveryMode", "ConnectionReference", "ApplicationProperties", "SessionAttributes.acknowledgeMode");
        } else if (typeTitle.contains("Adapter")) {
            return Arrays.asList("transport.type", "transport.jms.destination", "transport.jms.connectionReference", "subject", "endpoint");
        }
        return null; // Show all keys for unknown types
    }
    
    // ========== Global Variable Resolution ==========
    
    /**
     * Resolve global variables in a string value.
     * Replaces %%VarName%% with actual values from properties.
     */
    /**
     * Resolve global variables in a string value.
     * Replaces %%VarName%% with actual values from properties.
     * Supports both direct text replacement and regex for complex keys.
     */
    public String resolveGlobalVariable(String value, List<?> properties) {
        // Check if property resolution is enabled
        if (!config.getBooleanProperty("tibco.property.resolution.enabled", true)) {
            return value;
        }
        
        if (value == null || !value.contains("%%")) {
            return value;
        }
        
        // Convert to map for faster lookup
        Map<String, String> propertyMap = new HashMap<>();
        if (properties != null) {
            for (Object obj : properties) {
                if (obj instanceof TibcoGlobalVariable) {
                    TibcoGlobalVariable gv = (TibcoGlobalVariable) obj;
                    propertyMap.put(gv.getFullName(), gv.getValue());
                } else if (obj instanceof PropertyInfo) {
                    PropertyInfo prop = (PropertyInfo) obj;
                    propertyMap.put(prop.getKey(), prop.getDefaultValue());
                }
            }
        }
        
        // Use Regex to find all %%Placeholder%% patterns
        // Matches %%Group/Name%% or %%Name%%
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("%%([^%]+)%%");
        java.util.regex.Matcher matcher = pattern.matcher(value);
        
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String placeholderContent = matcher.group(1); // Content inside %%...%%
            String replacement = null;
            
            // Strategy 1: Exact Match (e.g. key="Timeouts/GLO_TimeOut", placeholder="Timeouts/GLO_TimeOut")
            if (propertyMap.containsKey(placeholderContent)) {
                replacement = propertyMap.get(placeholderContent);
            }
            
            // Strategy 2: Prefix Match (e.g. key="defaultVars/Timeouts/GLO_TimeOut", placeholder="Timeouts/GLO_TimeOut")
            if (replacement == null) {
                for (String key : propertyMap.keySet()) {
                    if (key != null && (key.endsWith("/" + placeholderContent) || key.equals("defaultVars/" + placeholderContent))) {
                        replacement = propertyMap.get(key);
                        break;
                    }
                }
            }

            // Strategy 3: Suffix/Short Match (e.g. key="Timeouts/GLO_TimeOut", placeholder="GLO_TimeOut")
            if (replacement == null) {
                 for (String key : propertyMap.keySet()) {
                    if (key != null && key.endsWith("/" + placeholderContent)) {
                        replacement = propertyMap.get(key);
                        break;
                    }
                }
            }
            
            // Strategy 4: Case-insensitive Match (Last Resort)
            if (replacement == null) {
                for (Map.Entry<String, String> entry : propertyMap.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(placeholderContent) || 
                        entry.getKey().endsWith("/" + placeholderContent) || 
                        (entry.getKey().equalsIgnoreCase("defaultVars/" + placeholderContent))) {
                        replacement = entry.getValue();
                        break;
                    }
                }
            }
            
            if (replacement != null) {
                // Escape backslashes and dollar signs for appendReplacement
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
            } else {
                // Keep original if not found
                matcher.appendReplacement(sb, "%%" + placeholderContent + "%%");
            }
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    // Legacy overload for backward compatibility
    public String resolveGlobalVariable(String value, List<PropertyInfo> globalVariables, boolean dummy) {
         return resolveGlobalVariable(value, (List<?>)globalVariables);
    }
    
    // ========== Data Formatting ==========
    
    /**
     * Format configuration values based on key and settings.
     * Handles truncation of large values like fullsource, bytecode, WADLContent.
     */
    public String formatConfigValue(String key, String value) {
        if (value == null || value.isEmpty()) return value;
        
        if ("fullsource".equals(key)) {
            boolean showDetails = config.getBooleanProperty("word.tibco.java.sourcecode.details", false);
            if (!showDetails) {
                return ""; // Blank as configured
            }
        }
        
        if ("bytecode".equals(key)) {
            boolean showDetails = config.getBooleanProperty("word.tibco.java.bytecode.details", false);
            if (!showDetails) {
                return ""; // Blank as configured
            }
        }
        
        if ("WADLContent".equals(key)) {
            boolean showDetails = config.getBooleanProperty("word.tibco.rest.wadl.details", false);
            if (!showDetails) {
                return ""; // Blank as configured
            }
        }
        
        return value;
    }
    
    /**
     * Abbreviate type names for Global Variables display.
     */
    public String abbreviateType(String type) {
        if (type == null) return "Str"; // Default
        String t = type.toLowerCase();
        if (t.startsWith("string")) return "Str";
        if (t.startsWith("password")) return "Pwd";
        if (t.startsWith("integer") || t.startsWith("int")) return "Int";
        if (t.startsWith("boolean") || t.startsWith("bool")) return "Bool";
        return type; // Fallback to original if unknown
    }
    
    // ========== Statistics Calculation ==========
    
    /**
     * Calculate process statistics for display.
     */
    public Map<String, Object> calculateProcessStatistics(AnalysisResult result) {
        Map<String, Object> stats = new HashMap<>();
        
        List<FlowInfo> flows = result.getFlows();
        if (flows == null) flows = new ArrayList<>();
        
        int total = flows.size();
        int starterCount = 0;
        int normalCount = 0;
        Map<String, Integer> starterTypeCounts = new HashMap<>();
        
        for (FlowInfo flow : flows) {
            if (isStarterProcess(flow)) {
                starterCount++;
                String rawType = getRawStarterType(flow);
                // Use exact type after last "." token
                String displayType = rawType;
                if (displayType != null && displayType.contains(".")) {
                    displayType = displayType.substring(displayType.lastIndexOf(".") + 1);
                }
                if (displayType == null || displayType.isEmpty()) {
                    displayType = "Unknown";
                }
                starterTypeCounts.put(displayType, starterTypeCounts.getOrDefault(displayType, 0) + 1);
            } else {
                normalCount++;
            }
        }
        
        stats.put("total", total);
        stats.put("starter", starterCount);
        stats.put("normal", normalCount);
        stats.put("starterTypeCounts", starterTypeCounts);
        
        return stats;
    }
    
    /**
     * Calculate activity statistics for a specific flow.
     */
    public Map<String, Object> calculateActivityStatistics(FlowInfo flow) {
        Map<String, Object> stats = new HashMap<>();
        
        List<ComponentInfo> activities = flow.getComponents();
        if (activities == null) activities = new ArrayList<>();
        
        stats.put("total", activities.size());
        
        Map<String, Integer> typeCounts = new HashMap<>();
        for (ComponentInfo c : activities) {
            String type = c.getType();
            // Simplify type (last token)
            if (type != null && type.contains(".")) {
                type = type.substring(type.lastIndexOf(".") + 1);
            }
            typeCounts.put(type, typeCounts.getOrDefault(type, 0) + 1);
        }
        
        stats.put("typeCounts", typeCounts);
        
        return stats;
    }
    
    // ========== Section Numbering ==========
    
    /**
     * Section numbering helper class.
     */
    public static class SectionNumbering {
        private final List<Integer> sectionNumbers = new ArrayList<>();
        
        public SectionNumbering() {
            this.sectionNumbers.add(0); // Initialize with level 0
        }
        
        public void incrementSection(int level) {
            while (sectionNumbers.size() <= level) {
                sectionNumbers.add(0);
            }
            sectionNumbers.set(level, sectionNumbers.get(level) + 1);
            // Reset deeper levels
            for (int i = level + 1; i < sectionNumbers.size(); i++) {
                sectionNumbers.set(i, 0);
            }
        }
        
        public void enterSubsection() {
            sectionNumbers.add(0);
            incrementSection(sectionNumbers.size() - 1);
        }
        
        public void exitSubsection() {
            if (sectionNumbers.size() > 1) {
                sectionNumbers.remove(sectionNumbers.size() - 1);
            }
        }
        
        public String getSectionNumber() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < sectionNumbers.size(); i++) {
                if (sectionNumbers.get(i) > 0) {
                    if (sb.length() > 0) sb.append(".");
                    sb.append(sectionNumbers.get(i));
                }
            }
            return sb.toString();
        }
    }
}
