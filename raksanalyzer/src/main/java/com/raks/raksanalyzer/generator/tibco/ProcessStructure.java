package com.raks.raksanalyzer.generator.tibco;

import org.w3c.dom.Element;
import java.util.*;

/**
 * Represents the complete structure of a TIBCO process for accurate diagram generation.
 * Analyzes and maintains all process elements, their relationships, and flow sequences.
 */
public class ProcessStructure {
    private final String processName;
    private final String startName;
    private final Map<String, ActivityNode> activities;
    private final Map<String, GroupNode> groups;
    private final Map<String, List<String>> transitions;
    private final List<String> catchActivities;
    private int activityCounter = 0;
    
    public ProcessStructure(String processName, String startName) {
        this.processName = processName;
        this.startName = startName;
        this.activities = new LinkedHashMap<>();
        this.groups = new LinkedHashMap<>();
        this.transitions = new LinkedHashMap<>();
        this.catchActivities = new ArrayList<>();
    }
    
    public void addActivity(ActivityNode activity) {
        activities.put(activity.getName(), activity);
        activityCounter++;
    }
    
    public void addGroup(GroupNode group) {
        groups.put(group.getName(), group);
    }
    
    public void addTransition(String from, String to) {
        transitions.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
    }
    
    public void addCatchActivity(String name) {
        catchActivities.add(name);
    }
    
    public String getProcessName() { return processName; }
    public String getStartName() { return startName; }
    public Map<String, ActivityNode> getActivities() { return activities; }
    public Map<String, GroupNode> getGroups() { return groups; }
    public Map<String, List<String>> getTransitions() { return transitions; }
    public List<String> getCatchActivities() { return catchActivities; }
    public int getActivityCount() { return activityCounter; }
    
    /**
     * Get all successors of a node (activity or group).
     */
    public List<String> getSuccessors(String nodeName) {
        return transitions.getOrDefault(nodeName, new ArrayList<>());
    }
    
    /**
     * Check if a node is inside a group.
     */
    public boolean isInsideGroup(String nodeName) {
        for (GroupNode group : groups.values()) {
            if (group.containsActivity(nodeName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get the group that contains a specific activity.
     */
    public GroupNode getContainingGroup(String nodeName) {
        for (GroupNode group : groups.values()) {
            if (group.containsActivity(nodeName)) {
                return group;
            }
        }
        return null;
    }
    
    public static class ActivityNode {
        private final String name;
        private final String type;
        private final String resourceType;
        private final Element element;
        private final boolean isCatch;
        private SubprocessInfo subprocessInfo;
        
        public ActivityNode(String name, String type, String resourceType, Element element, boolean isCatch) {
            this.name = name;
            this.type = type;
            this.resourceType = resourceType;
            this.element = element;
            this.isCatch = isCatch;
        }
        
        public String getName() { return name; }
        public String getType() { return type; }
        public String getResourceType() { return resourceType; }
        public Element getElement() { return element; }
        public boolean isCatch() { return isCatch; }
        public SubprocessInfo getSubprocessInfo() { return subprocessInfo; }
        public void setSubprocessInfo(SubprocessInfo info) { this.subprocessInfo = info; }
        
        public boolean isCallProcess() {
            return type != null && type.contains("CallProcessActivity");
        }
    }
    
    public static class GroupNode {
        private final String name;
        private final String type;
        private final String groupType;
        private final Element element;
        private final Map<String, ActivityNode> internalActivities;
        private final Map<String, List<String>> internalTransitions;
        private String internalStartName;
        private final List<GroupNode> nestedGroups;
        
        public GroupNode(String name, String type, String groupType, Element element) {
            this.name = name;
            this.type = type;
            this.groupType = groupType;
            this.element = element;
            this.internalActivities = new LinkedHashMap<>();
            this.internalTransitions = new LinkedHashMap<>();
            this.nestedGroups = new ArrayList<>();
        }
        
        public void addInternalActivity(ActivityNode activity) {
            internalActivities.put(activity.getName(), activity);
        }
        
        public void addInternalTransition(String from, String to) {
            internalTransitions.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
        }
        
        public void addNestedGroup(GroupNode group) {
            nestedGroups.add(group);
        }
        
        public void setInternalStartName(String startName) {
            this.internalStartName = startName;
        }
        
        public boolean containsActivity(String activityName) {
            return internalActivities.containsKey(activityName);
        }
        
        public String getName() { return name; }
        public String getType() { return type; }
        public String getGroupType() { return groupType; }
        public Element getElement() { return element; }
        public Map<String, ActivityNode> getInternalActivities() { return internalActivities; }
        public Map<String, List<String>> getInternalTransitions() { return internalTransitions; }
        public String getInternalStartName() { return internalStartName; }
        public List<GroupNode> getNestedGroups() { return nestedGroups; }
    }
    
    public static class SubprocessInfo {
        private final String processPath;
        private final boolean isSpawn;
        private final boolean hasOverride;
        private final String overrideXPath;
        
        public SubprocessInfo(String processPath, boolean isSpawn, boolean hasOverride, String overrideXPath) {
            this.processPath = processPath;
            this.isSpawn = isSpawn;
            this.hasOverride = hasOverride;
            this.overrideXPath = overrideXPath;
        }
        
        public String getProcessPath() { return processPath; }
        public boolean isSpawn() { return isSpawn; }
        public boolean hasOverride() { return hasOverride; }
        public String getOverrideXPath() { return overrideXPath; }
    }
}
