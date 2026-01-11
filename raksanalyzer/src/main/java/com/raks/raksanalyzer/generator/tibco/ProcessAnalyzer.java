package com.raks.raksanalyzer.generator.tibco;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Analyzes TIBCO process XML and builds a complete structural model.
 * Identifies all groups, activities, transitions, and their relationships.
 */
public class ProcessAnalyzer {
    
    /**
     * Analyze a process document and build its complete structure.
     */
    public static ProcessStructure analyze(Document doc, String processName) {
        Element root = doc.getDocumentElement();
        String startName = getTagValue(root, "pd:startName");
        
        ProcessStructure structure = new ProcessStructure(processName, startName);
        

        NodeList groups = doc.getElementsByTagNameNS("*", "group");
        for (int i = 0; i < groups.getLength(); i++) {
            Element groupElement = (Element) groups.item(i);
            if (!isInsideGroup(groupElement)) {

                ProcessStructure.GroupNode groupNode = analyzeGroup(groupElement, doc);
                structure.addGroup(groupNode);
            }
        }
        

        NodeList activities = doc.getElementsByTagNameNS("*", "activity");
        for (int i = 0; i < activities.getLength(); i++) {
            Element activityElement = (Element) activities.item(i);
            if (!isInsideGroup(activityElement)) {
                ProcessStructure.ActivityNode activityNode = analyzeActivity(activityElement);
                structure.addActivity(activityNode);
                
                if (activityNode.isCatch()) {
                    structure.addCatchActivity(activityNode.getName());
                }
            }
        }
        

        NodeList starters = doc.getElementsByTagNameNS("*", "starter");
        if (starters != null && starters.getLength() > 0) {
            Element starter = (Element) starters.item(0);
            String name = starter.getAttribute("name");
            String type = getTagValue(starter, "pd:type");
            String resourceType = getTagValue(starter, "pd:resourceType");
            ProcessStructure.ActivityNode starterNode = new ProcessStructure.ActivityNode(name, type, resourceType, starter, false);
            structure.addActivity(starterNode);
        }
        

        NodeList transitions = doc.getElementsByTagNameNS("*", "transition");
        for (int i = 0; i < transitions.getLength(); i++) {
            Element transition = (Element) transitions.item(i);
            if (!isInsideGroup(transition)) {
                String from = getTagValue(transition, "pd:from");
                String to = getTagValue(transition, "pd:to");
                structure.addTransition(from, to);
            }
        }
        
        return structure;
    }
    
    /**
     * Analyze a group and its internal structure.
     */
    private static ProcessStructure.GroupNode analyzeGroup(Element groupElement, Document doc) {
        String name = groupElement.getAttribute("name");
        String type = getTagValue(groupElement, "pd:type");
        String groupType = extractGroupType(groupElement);
        
        ProcessStructure.GroupNode groupNode = new ProcessStructure.GroupNode(name, type, groupType, groupElement);
        

        NodeList internalTransitions = groupElement.getElementsByTagNameNS("*", "transition");
        for (int i = 0; i < internalTransitions.getLength(); i++) {
            Element t = (Element) internalTransitions.item(i);
            if (isDirectChild(t, groupElement)) {
                String from = getTagValue(t, "pd:from");
                if ("start".equals(from)) {
                    String to = getTagValue(t, "pd:to");
                    groupNode.setInternalStartName(to);
                }
                String fromNode = getTagValue(t, "pd:from");
                String toNode = getTagValue(t, "pd:to");
                groupNode.addInternalTransition(fromNode, toNode);
            }
        }
        

        NodeList internalActivities = groupElement.getElementsByTagNameNS("*", "activity");
        for (int i = 0; i < internalActivities.getLength(); i++) {
            Element actElement = (Element) internalActivities.item(i);
            if (isDirectChild(actElement, groupElement)) {
                ProcessStructure.ActivityNode actNode = analyzeActivity(actElement);
                groupNode.addInternalActivity(actNode);
            }
        }
        

        NodeList nestedGroups = groupElement.getElementsByTagNameNS("*", "group");
        for (int i = 0; i < nestedGroups.getLength(); i++) {
            Element nestedGroupElement = (Element) nestedGroups.item(i);
            if (isDirectChild(nestedGroupElement, groupElement)) {
                ProcessStructure.GroupNode nestedGroup = analyzeGroup(nestedGroupElement, doc);
                groupNode.addNestedGroup(nestedGroup);
            }
        }
        
        return groupNode;
    }
    
    /**
     * Analyze an activity and extract subprocess information if applicable.
     */
    private static ProcessStructure.ActivityNode analyzeActivity(Element activityElement) {
        String name = activityElement.getAttribute("name");
        String type = getTagValue(activityElement, "pd:type");
        String resourceType = getTagValue(activityElement, "pd:resourceType");
        boolean isCatch = type != null && type.contains("CatchActivity");
        
        ProcessStructure.ActivityNode actNode = new ProcessStructure.ActivityNode(name, type, resourceType, activityElement, isCatch);
        

        if (type != null && type.contains("CallProcessActivity")) {
            Element config = (Element) activityElement.getElementsByTagName("config").item(0);
            if (config == null) {
                config = (Element) activityElement.getElementsByTagNameNS("*", "config").item(0);
            }
            
            if (config != null) {
                String processPath = getTagValue(config, "processName");
                String spawnStr = getTagValue(config, "spawn");
                boolean isSpawn = "true".equalsIgnoreCase(spawnStr);
                String overrideXPath = getTagValue(config, "processNameXPath");
                boolean hasOverride = overrideXPath != null && !overrideXPath.trim().isEmpty();
                
                ProcessStructure.SubprocessInfo subInfo = new ProcessStructure.SubprocessInfo(
                    processPath, isSpawn, hasOverride, overrideXPath
                );
                actNode.setSubprocessInfo(subInfo);
            }
        }
        
        return actNode;
    }
    
    /**
     * Extract group type from config element.
     */
    private static String extractGroupType(Element groupElement) {
        Element config = (Element) groupElement.getElementsByTagName("config").item(0);
        if (config == null) {
            config = (Element) groupElement.getElementsByTagNameNS("*", "config").item(0);
        }
        if (config != null) {
            return getTagValue(config, "pd:groupType");
        }
        return null;
    }
    
    /**
     * Check if an element is inside a group.
     */
    private static boolean isInsideGroup(Element element) {
        Node parent = element.getParentNode();
        while (parent != null) {
            String nodeName = parent.getNodeName();
            String localName = parent.getLocalName();
            if ("group".equals(localName) || "pd:group".equals(nodeName)) {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }
    
    /**
     * Check if an element is a direct child of a parent element.
     */
    private static boolean isDirectChild(Element child, Element parent) {
        Node childParent = child.getParentNode();
        return childParent != null && childParent.equals(parent);
    }
    
    /**
     * Get tag value from element.
     */
    private static String getTagValue(Element element, String tagName) {
        NodeList nl = element.getElementsByTagName(tagName);
        if (nl != null && nl.getLength() > 0) {
            return nl.item(0).getTextContent();
        }
        if (tagName.contains(":")) {
            String local = tagName.substring(tagName.indexOf(":") + 1);
            nl = element.getElementsByTagNameNS("*", local);
            if (nl != null && nl.getLength() > 0) {
                return nl.item(0).getTextContent();
            }
        }
        return null;
    }
}
