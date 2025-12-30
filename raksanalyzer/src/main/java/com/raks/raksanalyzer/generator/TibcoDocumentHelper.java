package com.raks.raksanalyzer.generator;
import com.raks.raksanalyzer.core.config.ConfigurationManager;
import com.raks.raksanalyzer.domain.model.*;
import com.raks.raksanalyzer.domain.model.tibco.*;
import java.util.*;
public class TibcoDocumentHelper {
    private final ConfigurationManager config;
    public TibcoDocumentHelper() {
        this.config = ConfigurationManager.getInstance();
    }
    public boolean isStarterProcess(FlowInfo flow) {
        return "starter-process".equals(flow.getType());
    }
    public String getRawStarterType(FlowInfo flow) {
        if (flow.getDescription() != null && flow.getDescription().contains("Type: ")) {
            return flow.getDescription().substring(flow.getDescription().indexOf("Type: ") + 6).trim();
        }
        return "";
    }
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
        return null; 
    }
    public String resolveGlobalVariable(String value, List<?> properties) {
        if (!config.getBooleanProperty("tibco.property.resolution.enabled", true)) {
            return value;
        }
        if (value == null || !value.contains("%%")) {
            return value;
        }
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
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("%%([^%]+)%%");
        java.util.regex.Matcher matcher = pattern.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String placeholderContent = matcher.group(1); 
            String replacement = null;
            if (propertyMap.containsKey(placeholderContent)) {
                replacement = propertyMap.get(placeholderContent);
            }
            if (replacement == null) {
                for (String key : propertyMap.keySet()) {
                    if (key != null && (key.endsWith("/" + placeholderContent) || key.equals("defaultVars/" + placeholderContent))) {
                        replacement = propertyMap.get(key);
                        break;
                    }
                }
            }
            if (replacement == null) {
                 for (String key : propertyMap.keySet()) {
                    if (key != null && key.endsWith("/" + placeholderContent)) {
                        replacement = propertyMap.get(key);
                        break;
                    }
                }
            }
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
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
            } else {
                matcher.appendReplacement(sb, "%%" + placeholderContent + "%%");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
    public String resolveGlobalVariable(String value, List<PropertyInfo> globalVariables, boolean dummy) {
         return resolveGlobalVariable(value, (List<?>)globalVariables);
    }
    public String formatConfigValue(String key, String value) {
        if (value == null || value.isEmpty()) return value;
        if ("fullsource".equals(key)) {
            boolean showDetails = config.getBooleanProperty("word.tibco.java.sourcecode.details", false);
            if (!showDetails) {
                return ""; 
            }
        }
        if ("bytecode".equals(key)) {
            boolean showDetails = config.getBooleanProperty("word.tibco.java.bytecode.details", false);
            if (!showDetails) {
                return ""; 
            }
        }
        if ("WADLContent".equals(key)) {
            boolean showDetails = config.getBooleanProperty("word.tibco.rest.wadl.details", false);
            if (!showDetails) {
                return ""; 
            }
        }
        return value;
    }
    public String abbreviateType(String type) {
        if (type == null) return "Str"; 
        String t = type.toLowerCase();
        if (t.startsWith("string")) return "Str";
        if (t.startsWith("password")) return "Pwd";
        if (t.startsWith("integer") || t.startsWith("int")) return "Int";
        if (t.startsWith("boolean") || t.startsWith("bool")) return "Bool";
        return type; 
    }
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
    public Map<String, Object> calculateActivityStatistics(FlowInfo flow) {
        Map<String, Object> stats = new HashMap<>();
        List<ComponentInfo> activities = flow.getComponents();
        if (activities == null) activities = new ArrayList<>();
        stats.put("total", activities.size());
        Map<String, Integer> typeCounts = new HashMap<>();
        for (ComponentInfo c : activities) {
            String type = c.getType();
            if (type != null && type.contains(".")) {
                type = type.substring(type.lastIndexOf(".") + 1);
            }
            typeCounts.put(type, typeCounts.getOrDefault(type, 0) + 1);
        }
        stats.put("typeCounts", typeCounts);
        return stats;
    }
    public static class SectionNumbering {
        private final List<Integer> sectionNumbers = new ArrayList<>();
        public SectionNumbering() {
            this.sectionNumbers.add(0); 
        }
        public void incrementSection(int level) {
            while (sectionNumbers.size() <= level) {
                sectionNumbers.add(0);
            }
            sectionNumbers.set(level, sectionNumbers.get(level) + 1);
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
