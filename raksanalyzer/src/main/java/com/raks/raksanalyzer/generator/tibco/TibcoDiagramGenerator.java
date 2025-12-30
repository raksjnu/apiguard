package com.raks.raksanalyzer.generator.tibco;
import com.raks.raksanalyzer.core.config.ConfigurationManager;
import net.sourceforge.plantuml.SourceStringReader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
public class TibcoDiagramGenerator {
    private static final Logger logger = LoggerFactory.getLogger(TibcoDiagramGenerator.class);
    private final ConfigurationManager config;
    private final List<String> connectorPatterns;
    private final List<String> excludePatterns;
    private static final Map<String, String> RESOURCE_TYPE_LABELS = new HashMap<>();
    static {
    }
    public TibcoDiagramGenerator() {
        this.config = ConfigurationManager.getInstance();
        String patterns = config.getProperty("tibco.connector.patterns", 
            ".jms.,.jdbc.,.file.,.soap.,.http.,.mail.,.ftp.,.tcp.,.ae.activities.,.json.,.rv.," +
            "getsharedvariable,setsharedvariable,getjobsharedvariable,setjobsharedvariable," +
            "sleepactivity,enginecommandactivity,javaactivity,javamethodactivity,javaevent," +
            "aesubscriberactivity,aerpcserveractivity,restadapteractivity");
        this.connectorPatterns = Arrays.asList(patterns.split(","));
        String excludes = config.getProperty("tibco.connector.exclude.patterns", "xmltojava,javatoxml");
        this.excludePatterns = Arrays.asList(excludes.split(","));
    }
    private static String getReadableTypeLabel(String resourceType) {
        if (resourceType == null || resourceType.isEmpty()) {
            return "Unknown";
        }
        if (resourceType.toLowerCase().contains("restactivity") || resourceType.contains("json.activities.RestActivity")) {
            return "REST Activity";
        }
        String lastToken = resourceType.substring(resourceType.lastIndexOf('.') + 1);
        if (RESOURCE_TYPE_LABELS.containsKey(lastToken)) {
            return RESOURCE_TYPE_LABELS.get(lastToken);
        }
        if (lastToken.endsWith("Activity")) {
            lastToken = lastToken.substring(0, lastToken.length() - "Activity".length());
        }
        return lastToken;
    }
    public byte[] generateFlowDiagram(File processFile) {
        try {
            if (!processFile.exists()) {
                logger.warn("Process file not found: {}", processFile);
                return null;
            }
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(true); 
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(processFile);
            String puml = generateFlowPuml(doc, processFile.getName());
            byte[] pngBytes = renderPng(puml);
            if (pngBytes != null && pngBytes.length > 0) {
            } else {
                logger.warn("✗ Diagram generation returned empty/null for {}", processFile.getName());
            }
            return pngBytes;
        } catch (Exception e) {
            logger.error("✗ Error generating flow diagram for {}", processFile.getName(), e);
            return null;
        }
    }
    public byte[] generateIntegrationDiagram(String serviceName, String startProcessPath, File projectRoot) {
        return generateIntegrationDiagram(serviceName, startProcessPath, projectRoot, null);
    }
    public byte[] generateIntegrationDiagram(String serviceName, String startProcessPath, File projectRoot, String overrideStarterLabel) {
        try {
            String puml = generateIntegrationPuml(serviceName, startProcessPath, projectRoot, overrideStarterLabel);
            return renderPng(puml);
        } catch (Exception e) {
            logger.error("Error generating integration diagram for {}", serviceName, e);
            return null;
        }
    }
    public String generateIntegrationPuml(String serviceName, String startProcessPath, File projectRoot, String overrideStarterLabel) {
            if (startProcessPath.startsWith("/")) startProcessPath = startProcessPath.substring(1);
            File processFile;
            File maybeAbsolute = new File(startProcessPath);
            if (maybeAbsolute.isAbsolute() && maybeAbsolute.exists()) {
                processFile = maybeAbsolute;
            } else {
                processFile = new File(projectRoot, startProcessPath);
            }
            if (!processFile.exists()) {
                return "@startuml\nrectangle \"File Not Found: " + startProcessPath + "\"\n@enduml";
            }
            try {
                Map<String, Boolean> relevanceCache = new HashMap<>();
                StringBuilder sb = new StringBuilder();
                sb.append("@startuml\n");
                sb.append("skinparam shadowing false\n");
                sb.append("skinparam activity {\n");
                sb.append("  BackgroundColor #E6E6FA\n");
                sb.append("  BorderColor #6A5ACD\n"); 
                sb.append("  ArrowColor #483D8B\n"); 
                sb.append("  StartColor #4CAF50\n");
                sb.append("  EndColor #E53935\n");
                sb.append("  FontSize 11\n");
                sb.append("}\n");
                sb.append("skinparam partition {\n");
                sb.append("  BackgroundColor #F0F0F0\n");
                sb.append("  BorderColor #999999\n");
                sb.append("  FontSize 11\n");
                sb.append("  BorderThickness 2\n");
                sb.append("}\n");
                sb.append("skinparam activityEndColor #E53935\n");
                sb.append("skinparam dpi 300\n");
                sb.append("!pragma maxwidth 600\n");
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                dbFactory.setNamespaceAware(true); 
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(processFile);
                Set<String> visited = new HashSet<>();
                visited.add(processFile.getAbsolutePath());
                sb.append("start\n");
                generateRecursiveActivityPuml(sb, doc, processFile, projectRoot, visited, relevanceCache, 0, overrideStarterLabel);
                sb.append("stop\n");
                sb.append("@enduml");
                String pumlSource = sb.toString();
                return pumlSource;
            } catch (Exception e) {
                logger.error("Error generating puml for {}", serviceName, e);
                return "@startuml\nrectangle \"Error parsing " + serviceName + "\"\n@enduml";
            }
    }
    private void generateRecursiveActivityPuml(StringBuilder sb, Document doc, File currentFile, File projectRoot, 
                                               Set<String> visited, Map<String, Boolean> relevanceCache, int depth, String overrideStarterLabel) {
        Element root = doc.getDocumentElement();
        String startName = getTagValue(root, "pd:startName");
        NodeList activities = doc.getElementsByTagNameNS("*", "activity");
        NodeList starters = doc.getElementsByTagNameNS("*", "starter");
        NodeList groups = doc.getElementsByTagNameNS("*", "group");
        Map<String, Element> activityMap = new HashMap<>();
        for (int i = 0; i < activities.getLength(); i++) {
            Element act = (Element) activities.item(i);
            activityMap.put(act.getAttribute("name"), act);
        }
        Element starterElement = null;
        if (starters != null && starters.getLength() > 0) {
            starterElement = (Element) starters.item(0);
            activityMap.put(starterElement.getAttribute("name"), starterElement);
        }
        for (int i = 0; i < groups.getLength(); i++) {
            Element grp = (Element) groups.item(i);
            activityMap.put(grp.getAttribute("name"), grp);
        }
        Map<String, List<String>> transitions = new HashMap<>();
        NodeList transitionList = doc.getElementsByTagNameNS("*", "transition");
        for (int i = 0; i < transitionList.getLength(); i++) {
            Element t = (Element) transitionList.item(i);
            Node parent = t.getParentNode();
            boolean insideGroup = false;
            while (parent != null) {
                String nodeName = parent.getNodeName();
                String localName = parent.getLocalName();
                if ("group".equals(localName) || "pd:group".equals(nodeName)) {
                    insideGroup = true;
                    break;
                }
                parent = parent.getParentNode();
            }
            if (insideGroup) {
                continue; 
            }
            String from = getTagValue(t, "pd:from");
            String to = getTagValue(t, "pd:to");
            transitions.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
        }
        if (depth == 0 && starterElement != null) {
            String type = getTagValue(starterElement, "pd:type");
            String cleanType = type.substring(type.lastIndexOf('.') + 1);
            sb.append(":**").append(cleanType).append("**;\n");
        }
        Set<String> processed = new HashSet<>();
        String startNode = starterElement != null ? starterElement.getAttribute("name") : startName;
        List<String> starterSuccessors = transitions.get(startNode);
        if (starterSuccessors != null) {
            if (starterSuccessors.size() > 1) {
                List<String> branchesWithContent = new ArrayList<>();
                for (String successor : starterSuccessors) {
                    if (hasRelevantContentInPath(successor, activityMap, transitions, projectRoot, relevanceCache, new HashSet<>())) {
                        branchesWithContent.add(successor);
                    }
                }
                if (branchesWithContent.size() > 1) {
                    sb.append("fork\n");
                    List<Set<String>> branchProcessedSets = new ArrayList<>();
                    for (int i = 0; i < branchesWithContent.size(); i++) {
                        if (i > 0) {
                            sb.append("fork again\n");
                        }
                        Set<String> branchProcessed = new HashSet<>(processed);
                        traverseProcess(sb, branchesWithContent.get(i), activityMap, transitions, branchProcessed, projectRoot, visited, relevanceCache, depth, groups);
                        branchProcessedSets.add(branchProcessed);
                    }
                    sb.append("end fork\n");
                    for (Set<String> branchProcessed : branchProcessedSets) {
                        processed.addAll(branchProcessed);
                    }
                } else if (branchesWithContent.size() == 1) {
                    traverseProcess(sb, branchesWithContent.get(0), activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups);
                }
            } else {
                for (String successor : starterSuccessors) {
                    traverseProcess(sb, successor, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups);
                }
            }
        }
    }
    private void traverseProcess(StringBuilder sb, String nodeName, Map<String, Element> activityMap,
                                 Map<String, List<String>> transitions, Set<String> processed,
                                 File projectRoot, Set<String> visited, Map<String, Boolean> relevanceCache,
                                 int depth, NodeList groups) {
        if (nodeName == null || processed.contains(nodeName)) return;
        processed.add(nodeName);
        Element activity = activityMap.get(nodeName);
        if (activity == null) {
            List<String> successors = transitions.get(nodeName);
            if (successors != null) {
                for (String successor : successors) {
                    traverseProcess(sb, successor, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups);
                }
            }
            return;
        }
        String type = getTagValue(activity, "pd:type");
        if (type != null && (type.contains("LoopGroup") || type.contains("CriticalSectionGroup") || type.contains("CatchActivity"))) {
            String resourceType = getTagValue(activity, "pd:resourceType");
            String typeLabel = getReadableTypeLabel(resourceType);
            String partitionLabel = typeLabel + ": " + nodeName;
            sb.append("partition \"").append(partitionLabel).append("\" {\n");
            NodeList groupActivities = activity.getElementsByTagNameNS("*", "activity");
            if (groupActivities != null && groupActivities.getLength() > 0) {
                for (int i = 0; i < groupActivities.getLength(); i++) {
                    Element groupAct = (Element) groupActivities.item(i);
                    String groupActName = groupAct.getAttribute("name");
                    traverseProcess(sb, groupActName, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups);
                }
            }
            sb.append("}\n");
            List<String> successors = transitions.get(nodeName);
            if (successors != null) {
                for (String successor : successors) {
                    traverseProcess(sb, successor, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups);
                }
            }
            return;
        }
        boolean isRelevant = type != null && isIntegrationRelevant(activity, projectRoot, relevanceCache);
        if (isRelevant) {
            if (type.contains("CallProcessActivity")) {
                String subProcPath = getConfigValue(activity, "processName");
                String dynamicOverride = getDynamicOverride(activity);
                String spawnConfig = getConfigValue(activity, "spawn");
                boolean isSpawn = "true".equalsIgnoreCase(spawnConfig);
                if (dynamicOverride != null && !dynamicOverride.trim().isEmpty()) {
                    List<String> processPaths = extractProcessPathsFromXPath(dynamicOverride);
                    for (String procPath : processPaths) {
                        String cleanPath = procPath.trim();
                        if (cleanPath.startsWith("/")) cleanPath = cleanPath.substring(1);
                        String processName = cleanPath.substring(cleanPath.lastIndexOf("/") + 1);
                        if (processName.endsWith(".process")) {
                            processName = processName.substring(0, processName.length() - 8);
                        }
                        File subFile = new File(projectRoot, cleanPath);
                        if (subFile.exists() && !visited.contains(subFile.getAbsolutePath())) {
                            Set<String> nextVisited = new HashSet<>(visited);
                            nextVisited.add(subFile.getAbsolutePath());
                            sb.append("partition \"<b>override-subprocess</b>\\n").append(processName).append("\" #FFF3E0 {\n");
                            try {
                                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                                dbf.setNamespaceAware(true);
                                Document subDoc = dbf.newDocumentBuilder().parse(subFile);
                                generateRecursiveActivityPuml(sb, subDoc, subFile, projectRoot, nextVisited, relevanceCache, depth + 1, null);
                            } catch (Exception e) {
                                sb.append(":Error loading subprocess;\n");
                            }
                            sb.append("}\n");
                        }
                    }
                } else if (isSpawn && subProcPath != null) {
                    if (subProcPath.startsWith("/")) subProcPath = subProcPath.substring(1);
                    File subFile = new File(projectRoot, subProcPath);
                    if (subFile.exists() && !visited.contains(subFile.getAbsolutePath())) {
                        Set<String> nextVisited = new HashSet<>(visited);
                        nextVisited.add(subFile.getAbsolutePath());
                        sb.append("partition \"<b>spawn-subprocess</b>\\n").append(nodeName).append("\" #F1F8E9 {\n");
                        try {
                            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                            dbf.setNamespaceAware(true);
                            Document subDoc = dbf.newDocumentBuilder().parse(subFile);
                            generateRecursiveActivityPuml(sb, subDoc, subFile, projectRoot, nextVisited, relevanceCache, depth + 1, null);
                        } catch (Exception e) {
                            sb.append(":Error loading subprocess;\n");
                        }
                        sb.append("}\n");
                    }
                } else if (subProcPath != null) {
                    if (subProcPath.startsWith("/")) subProcPath = subProcPath.substring(1);
                    File subFile = new File(projectRoot, subProcPath);
                    if (subFile.exists() && !visited.contains(subFile.getAbsolutePath())) {
                        Set<String> nextVisited = new HashSet<>(visited);
                        nextVisited.add(subFile.getAbsolutePath());
                        String resourceType = getTagValue(activity, "pd:resourceType");
                        String typeLabel = getReadableTypeLabel(resourceType);
                        sb.append("partition \"").append(typeLabel).append(": ").append(nodeName).append("\" {\n");
                        try {
                            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                            dbf.setNamespaceAware(true);
                            Document subDoc = dbf.newDocumentBuilder().parse(subFile);
                            generateRecursiveActivityPuml(sb, subDoc, subFile, projectRoot, nextVisited, relevanceCache, depth + 1, null);
                        } catch (Exception e) {
                            sb.append(":Error loading subprocess;\n");
                        }
                        sb.append("}\n");
                    }
                }
            } else if (isConnector(type)) {
                String resourceType = getTagValue(activity, "pd:resourceType");
                String cleanType = getReadableTypeLabel(resourceType);
                String icon = getConnectorIcon(type);
                sb.append(":").append(icon).append(" <b>").append(cleanType).append("</b>\n");
                sb.append(nodeName).append(";\n");
            }
        }
        List<String> successors = transitions.get(nodeName);
        if (successors != null && isRelevant) {
            List<String> relevantSuccessors = new ArrayList<>();
            for (String successor : successors) {
                Element successorActivity = activityMap.get(successor);
                if (successorActivity != null) {
                    boolean successorRelevant = isIntegrationRelevant(successorActivity, projectRoot, relevanceCache);
                    if (successorRelevant) {
                        relevantSuccessors.add(successor);
                    } else {
                        traverseProcess(sb, successor, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups);
                    }
                } else {
                    relevantSuccessors.add(successor);
                }
            }
            if (!relevantSuccessors.isEmpty()) {
                if (relevantSuccessors.size() > 1) {
                    List<String> branchesWithContent = new ArrayList<>();
                    for (String successor : relevantSuccessors) {
                        if (hasRelevantContentInPath(successor, activityMap, transitions, projectRoot, relevanceCache, new HashSet<>())) {
                            branchesWithContent.add(successor);
                        }
                    }
                    if (branchesWithContent.size() > 1) {
                        sb.append("fork\n");
                        for (int i = 0; i < branchesWithContent.size(); i++) {
                            if (i > 0) {
                                sb.append("fork again\n");
                            }
                            traverseProcess(sb, branchesWithContent.get(i), activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups);
                        }
                        sb.append("end fork\n");
                    } else if (branchesWithContent.size() == 1) {
                        traverseProcess(sb, branchesWithContent.get(0), activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups);
                    }
                } else {
                    for (String successor : relevantSuccessors) {
                        traverseProcess(sb, successor, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups);
                    }
                }
            }
        } else if (successors != null && !isRelevant) {
            for (String successor : successors) {
                traverseProcess(sb, successor, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups);
            }
        }
    }
    private boolean isIntegrationRelevant(Element activity, File projectRoot, Map<String, Boolean> cache) {
        String type = getTagValue(activity, "pd:type");
        if (type == null) return false;
        if (type.contains("Group") || activity.getNodeName().contains("group")) {
            NodeList groupActivities = activity.getElementsByTagNameNS("*", "activity");
            for (int i = 0; i < groupActivities.getLength(); i++) {
                Element groupAct = (Element) groupActivities.item(i);
                if (isIntegrationRelevant(groupAct, projectRoot, cache)) {
                    return true;
                }
            }
            return false;
        }
        if (isConnector(type)) return true;
        if (type.contains("CallProcessActivity")) {
             String subPath = getConfigValue(activity, "processName");
             if (subPath == null) return false;
             if (cache.containsKey(subPath)) return cache.get(subPath);
             if (subPath.startsWith("/")) subPath = subPath.substring(1);
             File subFile = new File(projectRoot, subPath);
             if (!subFile.exists()) {
                 cache.put(subPath, false);
                 return false;
             }
             try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true); 
                Document subDoc = dbf.newDocumentBuilder().parse(subFile);
                NodeList acts = subDoc.getElementsByTagNameNS("*", "activity");
                boolean hasRel = false;
                for (int i=0; i<acts.getLength(); i++) {
                    if (isIntegrationRelevant((Element)acts.item(i), projectRoot, cache)) {
                        hasRel = true;
                        break;
                    }
                }
                cache.put(subPath, hasRel);
                return hasRel;
             } catch (Exception e) {
                 return false;
             }
        }
        return false;
    }
    private boolean hasRelevantContentInPath(String nodeName, Map<String, Element> activityMap, 
                                             Map<String, List<String>> transitions, File projectRoot,
                                             Map<String, Boolean> relevanceCache, Set<String> visited) {
        if (visited.contains(nodeName)) return false;
        visited.add(nodeName);
        Element activity = activityMap.get(nodeName);
        if (activity != null && isIntegrationRelevant(activity, projectRoot, relevanceCache)) {
            return true;
        }
        List<String> successors = transitions.get(nodeName);
        if (successors != null) {
            for (String successor : successors) {
                if (hasRelevantContentInPath(successor, activityMap, transitions, projectRoot, relevanceCache, visited)) {
                    return true;
                }
            }
        }
        return false;
    }
    private String generateFlowPuml(Document doc, String processName) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("skinparam shadowing false\n");
        sb.append("skinparam activity {\n");
        sb.append("  BackgroundColor #E6E6FA\n");
        sb.append("  BorderColor #6A5ACD\n"); 
        sb.append("  ArrowColor #483D8B\n"); 
        sb.append("  StartColor #4CAF50\n");
        sb.append("  EndColor #E53935\n");
        sb.append("  FontSize 11\n");
        sb.append("}\n");
        sb.append("skinparam partition {\n");
        sb.append("  BackgroundColor #F0F0F0\n");
        sb.append("  BorderColor #999999\n");
        sb.append("  FontSize 11\n");
        sb.append("  BorderThickness 2\n");
        sb.append("}\n");
        sb.append("skinparam activityEndColor #E53935\n");
        sb.append("skinparam dpi 300\n");
        sb.append("!pragma maxwidth 600\n");
        try {
            Element root = doc.getDocumentElement();
            String startName = getTagValue(root, "pd:startName");
            Map<String, Element> activityMap = new HashMap<>();
            Map<String, Element> groupMap = new HashMap<>();
            Map<String, List<String>> transitions = new HashMap<>();
            List<String> catchActivities = new ArrayList<>();
            NodeList activities = doc.getElementsByTagNameNS("*", "activity");
            for (int i = 0; i < activities.getLength(); i++) {
                Element activity = (Element) activities.item(i);
                String name = activity.getAttribute("name");
                String type = getTagValue(activity, "pd:type");
                if (type != null && type.contains("CatchActivity")) {
                    catchActivities.add(name);
                }
                if (!isInsideGroup(activity)) {
                    activityMap.put(name, activity);
                }
            }
            NodeList starters = doc.getElementsByTagNameNS("*", "starter");
            if (starters != null && starters.getLength() > 0) {
                Element starter = (Element) starters.item(0);
                String name = starter.getAttribute("name");
                activityMap.put(name, starter);
            }
            NodeList groups = doc.getElementsByTagNameNS("*", "group");
            for (int i = 0; i < groups.getLength(); i++) {
                Element group = (Element) groups.item(i);
                groupMap.put(group.getAttribute("name"), group);
            }
            NodeList transitionList = doc.getElementsByTagNameNS("*", "transition");
            for (int i = 0; i < transitionList.getLength(); i++) {
                Element t = (Element) transitionList.item(i);
                if (isInsideGroup(t)) continue;
                String from = getTagValue(t, "pd:from");
                String to = getTagValue(t, "pd:to");
                transitions.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
                String xpathDesc = getTagValue(t, "pd:xpathDescription");
                if (xpathDesc != null && !xpathDesc.isEmpty()) {
                }
            }
            Map<String, Map<String, String>> transitionLabels = new HashMap<>();
            Map<String, Map<String, String>> transitionConditions = new HashMap<>();
            for (int i = 0; i < transitionList.getLength(); i++) {
                Element t = (Element) transitionList.item(i);
                if (isInsideGroup(t)) continue;
                String from = getTagValue(t, "pd:from");
                String to = getTagValue(t, "pd:to");
                String xpathDesc = getTagValue(t, "pd:xpathDescription");
                if (xpathDesc != null && !xpathDesc.trim().isEmpty()) {
                     transitionLabels.computeIfAbsent(from, k -> new HashMap<>()).put(to, xpathDesc.trim());
                }
                String conditionType = getTagValue(t, "pd:conditionType");
                if (conditionType != null) {
                    transitionConditions.computeIfAbsent(from, k -> new HashMap<>()).put(to, conditionType);
                    Map<String, String> fromLabels = transitionLabels.computeIfAbsent(from, k -> new HashMap<>());
                    if (!fromLabels.containsKey(to)) {
                        if ("otherwise".equalsIgnoreCase(conditionType)) {
                            fromLabels.put(to, "Otherwise");
                        } else if ("error".equalsIgnoreCase(conditionType)) {
                            fromLabels.put(to, "Error");
                        } else if ("xpath".equalsIgnoreCase(conditionType)) {
                            String xpathVal = getTagValue(t, "pd:xpath");
                            if (xpathVal != null && !xpathVal.trim().isEmpty()) {
                                fromLabels.put(to, xpathVal.trim());
                            } else {
                                fromLabels.put(to, "XPath");
                            }
                        }
                    }
                }
            }
             sb.append("start\n");
             traverseFlowFromNode(sb, startName, activityMap, groupMap, transitions, transitionLabels, transitionConditions, new HashSet<>(), doc, null, false);
             sb.append("stop\n");
             if (!catchActivities.isEmpty()) {
                 sb.append("partition \"Error Handling\" #FFEBEE {\n");
                 for (String catchName : catchActivities) {
                      sb.append("\n");
                      sb.append("start\n");
                      traverseFlowFromNode(sb, catchName, activityMap, groupMap, transitions, transitionLabels, transitionConditions, new HashSet<>(), doc, null, false);
                      sb.append("stop\n");
                 }
                 sb.append("}\n");
             }
        } catch (Exception e) {
            logger.error("Error generating flow diagram PUML", e);
            sb.append(":Error generating diagram;\n");
        }
        sb.append("@enduml");
        return sb.toString();
    }
    private void traverseFlowFromNode(StringBuilder sb, String nodeName, 
                                      Map<String, Element> activityMap,
                                      Map<String, Element> groupMap,
                                      Map<String, List<String>> transitions,
                                      Map<String, Map<String, String>> transitionLabels,
                                      Map<String, Map<String, String>> transitionConditions,
                                      Set<String> processed,
                                      Document doc,
                                      String stopAtNode,
                                      boolean skipFirstNodeRender) {
        if (nodeName == null) return;
        if (stopAtNode != null && stopAtNode.equals(nodeName)) {
            return;
        }
        if (processed.contains(nodeName)) return;
        processed.add(nodeName);
        List<String> successors = transitions.get(nodeName);
        boolean isSwitch = false;
        if (successors != null && successors.size() > 1) {
            if (transitionConditions != null && transitionConditions.containsKey(nodeName)) {
                Map<String, String> conditions = transitionConditions.get(nodeName);
                for (String s : successors) {
                    String c = conditions.get(s);
                    if (c != null && (c.equals("xpath") || c.equals("otherwise") || c.equals("error"))) {
                        isSwitch = true;
                        break;
                    }
                }
            }
        }
        if (isSwitch) {
            sb.append("switch (").append(nodeName).append(")\n");
            String joinNode = findCommonJoinNode(successors, transitions);
            for (String successor : successors) {
                String label = "";
                if (transitionLabels != null && transitionLabels.containsKey(nodeName)) {
                        String l = transitionLabels.get(nodeName).get(successor);
                        if (l != null) label = l;
                }
                label = label.replace("\"", "\\\"");
                sb.append("case ( ").append(label).append(" )\n");
                traverseFlowFromNode(sb, successor, activityMap, groupMap, transitions, transitionLabels, transitionConditions, processed, doc, joinNode, false);
            }
            sb.append("endswitch\n");
            if (joinNode != null) {
                boolean isNullActivity = false;
                Element joinEl = activityMap.get(joinNode);
                if (joinEl != null) {
                     String t = getTagValue(joinEl, "pd:type");
                     if (t != null && t.contains("NullActivity")) isNullActivity = true;
                }
                traverseFlowFromNode(sb, joinNode, activityMap, groupMap, transitions, transitionLabels, transitionConditions, processed, doc, stopAtNode, isNullActivity);
            }
        } else {
            if (!skipFirstNodeRender) {
                if (groupMap.containsKey(nodeName)) {
                    renderGroup(sb, groupMap.get(nodeName), doc);
                } else if (activityMap.containsKey(nodeName)) {
                renderActivity(sb, activityMap.get(nodeName));
            }
            }
            if (successors != null && !successors.isEmpty()) {
                if (successors.size() > 1) {
                    String joinNode = findCommonJoinNode(successors, transitions);
                    sb.append("fork\n");
                    for (int i = 0; i < successors.size(); i++) {
                        if (i > 0) sb.append("fork again\n");
                        String successor = successors.get(i);
                        if (transitionLabels != null && transitionLabels.containsKey(nodeName)) {
                            String label = transitionLabels.get(nodeName).get(successor);
                            if (label != null) {
                                label = label.replace("\"", "\\\"");
                                sb.append("-> \"").append(label).append("\"\n");
                            } else {
                                sb.append("->\n"); 
                            }
                        } else {
                            sb.append("->\n"); 
                        }
                        traverseFlowFromNode(sb, successor, activityMap, groupMap, transitions, transitionLabels, transitionConditions, processed, doc, joinNode, false);
                    }
                    sb.append("end fork\n");
                    if (joinNode != null) {
                        boolean isNullActivity = false;
                        Element joinEl = activityMap.get(joinNode);
                        if (joinEl != null) {
                             String t = getTagValue(joinEl, "pd:type");
                             if (t != null && t.contains("NullActivity")) isNullActivity = true;
                        }
                        traverseFlowFromNode(sb, joinNode, activityMap, groupMap, transitions, transitionLabels, transitionConditions, processed, doc, stopAtNode, isNullActivity);
                    }
                } else {
                    String successor = successors.get(0);
                    boolean successorExists = activityMap.containsKey(successor) || groupMap.containsKey(successor);
                    if (successorExists && transitionLabels != null && transitionLabels.containsKey(nodeName)) {
                        String label = transitionLabels.get(nodeName).get(successor);
                        if (label != null) {
                            label = label.replace("\"", "\\\"");
                             sb.append("-> \"").append(label).append("\"\n");
                        } else {
                            sb.append("->\n"); 
                        }
                    } else {
                        sb.append("->\n");
                    }
                    traverseFlowFromNode(sb, successor, activityMap, groupMap, transitions, transitionLabels, transitionConditions, processed, doc, stopAtNode, false);
                }
            }
        }
    }
    private String findCommonJoinNode(List<String> startNodes, Map<String, List<String>> transitions) {
        if (startNodes == null || startNodes.isEmpty()) return null;
        List<Set<String>> reachableSets = new ArrayList<>();
        for (String start : startNodes) {
            Set<String> reachable = new HashSet<>();
            collectReachable(start, transitions, reachable);
            reachableSets.add(reachable);
        }
        Set<String> common = new HashSet<>(reachableSets.get(0));
        for (int i = 1; i < reachableSets.size(); i++) {
            common.retainAll(reachableSets.get(i));
        }
        if (common.isEmpty()) return null;
        return findFirstCommonInBFS(startNodes.get(0), transitions, common);
    }
    private void collectReachable(String current, Map<String, List<String>> transitions, Set<String> visited) {
        if (current == null || visited.contains(current)) return;
        visited.add(current);
        List<String> nexts = transitions.get(current);
        if (nexts != null) {
            for (String next : nexts) collectReachable(next, transitions, visited);
        }
    }
    private String findFirstCommonInBFS(String start, Map<String, List<String>> transitions, Set<String> candidates) {
        Queue<String> queue = new LinkedList<>();
        queue.add(start);
        Set<String> visited = new HashSet<>();
        visited.add(start);
        while(!queue.isEmpty()) {
            String curr = queue.poll();
            if (candidates.contains(curr)) return curr;
            List<String> nexts = transitions.get(curr);
            if (nexts != null) {
                for (String next : nexts) {
                    if (!visited.contains(next)) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        return null;
    }
    private void renderActivity(StringBuilder sb, Element activity) {
        String name = activity.getAttribute("name");
        String resourceType = getTagValue(activity, "pd:resourceType");
        String type = getTagValue(activity, "pd:type");
        if (type != null && type.contains("NullActivity")) {
             sb.append("if (").append(name).append(") then\nendif\n");
             return;
        }
        if (type != null && type.contains("CallProcessActivity")) {
             String dynamicOverride = getDynamicOverride(activity);
             String spawnConfig = getConfigValue(activity, "spawn");
             boolean isSpawn = "true".equalsIgnoreCase(spawnConfig);
             if (dynamicOverride != null && !dynamicOverride.trim().isEmpty()) {
                 sb.append("partition \"<b>override-subprocess</b>\\n").append(name).append("\" #FFF3E0 {\n");
                 renderActivityInternal(sb, name, type, resourceType);
                 sb.append("}\n");
                 return;
             } else if (isSpawn) {
                 sb.append("partition \"<b>spawn-subprocess</b>\\n").append(name).append("\" #F1F8E9 {\n");
                 renderActivityInternal(sb, name, type, resourceType);
                 sb.append("}\n");
                 return;
             }
        }
        renderActivityInternal(sb, name, type, resourceType);
    }
    private void renderActivityInternal(StringBuilder sb, String name, String type, String resourceType) {
        String icon = getConnectorIcon(type);
        String readableType = getReadableTypeLabel(resourceType);
        sb.append(":").append(icon).append(" **").append(name).append("**\\n").append(readableType).append(";\n");
    }
    private void renderGroup(StringBuilder sb, Element group, Document doc) {
        String groupName = group.getAttribute("name");
        String groupResourceType = getTagValue(group, "pd:resourceType");
        String groupLabel = getReadableTypeLabel(groupResourceType);
        String groupType = "";
        Element config = (Element) group.getElementsByTagName("config").item(0);
        if(config == null) config = (Element) group.getElementsByTagNameNS("*", "config").item(0);
        if(config != null) {
            String gt = getTagValue(config, "pd:groupType");
            if(gt != null && !gt.isEmpty()) {
                groupType = " (" + gt + ")";
            }
        }
        sb.append("partition \"").append(groupLabel).append(": ").append(groupName).append(groupType).append("\" {\n");
        Map<String, String> internalTransitions = new HashMap<>(); 
        Map<String, String> internalLabels = new HashMap<>(); 
        NodeList transitions = group.getElementsByTagNameNS("*", "transition");
        for (int i = 0; i < transitions.getLength(); i++) {
            Element t = (Element) transitions.item(i);
            Node parent = t.getParentNode();
            boolean isChild = parent instanceof Element && 
                              ((Element)parent).getAttribute("name").equals(groupName);
            if (isChild) {
                String from = getTagValue(t, "pd:from");
                String to = getTagValue(t, "pd:to");
                internalTransitions.put(from, to);
                String xpath = getTagValue(t, "pd:xpathDescription");
                if (xpath != null && !xpath.trim().isEmpty()) {
                    internalLabels.put(from, xpath.trim());
                }
            }
        }
        Map<String, Element> internalNodes = new HashMap<>();
        NodeList groupActivities = group.getElementsByTagNameNS("*", "activity");
        for (int i = 0; i < groupActivities.getLength(); i++) {
            Element act = (Element) groupActivities.item(i);
            Node parent = act.getParentNode();
            boolean isChild = parent instanceof Element && 
                              ((Element)parent).getAttribute("name").equals(groupName);
            if (isChild) {
                internalNodes.put(act.getAttribute("name"), act);
            }
        }
        NodeList nestedGroups = group.getElementsByTagNameNS("*", "group");
        for (int i = 0; i < nestedGroups.getLength(); i++) {
            Element subGroup = (Element) nestedGroups.item(i);
            Node parent = subGroup.getParentNode();
            boolean isChild = parent instanceof Element && 
                              ((Element)parent).getAttribute("name").equals(groupName);
            if (isChild) {
                internalNodes.put(subGroup.getAttribute("name"), subGroup);
            }
        }
    Set<String> targets = new HashSet<>(internalTransitions.values());
    String startNode = null;
    for (String name : internalNodes.keySet()) {
        if (!targets.contains(name)) {
            startNode = name; 
            break;
        }
    }
    if (internalTransitions.containsKey("start")) {
        startNode = internalTransitions.get("start");
    }
    String current = startNode;
    int steps = 0;
    Set<String> rendered = new HashSet<>();
    while (current != null && steps < 50) {
            String next = internalTransitions.get(current);
            String label = internalLabels.get(current);
            if (internalNodes.containsKey(current)) {
                Element el = internalNodes.get(current);
                String tagName = el.getLocalName();
                if ("group".equals(tagName) || tagName.endsWith(":group")) {
                    renderGroup(sb, el, doc);
                } else {
                    renderActivity(sb, el);
                }
                rendered.add(current);
            }
            if (next != null) {
                if (label != null) {
                    sb.append("-> \"").append(label).append("\"\n");
                }
            }
            current = next;
            if ("end".equals(current)) break;
            steps++;
        }
        for (Map.Entry<String, Element> entry : internalNodes.entrySet()) {
             if (!rendered.contains(entry.getKey())) {
                 Element el = entry.getValue();
                 String tagName = el.getLocalName();
                 if ("group".equals(tagName) || tagName.endsWith(":group")) {
                    renderGroup(sb, el, doc);
                } else {
                    renderActivity(sb, el);
                }
             }
        }
        sb.append("}\n");
    }
    private boolean isInsideGroup(Element element) {
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
    private String getTagValue(Element element, String tagName) {
        NodeList nl = element.getElementsByTagName(tagName);
        if (nl != null && nl.getLength() > 0) {
            return nl.item(0).getTextContent();
        }
        if (tagName.contains(":")) {
             String local = tagName.substring(tagName.indexOf(":")+1);
             nl = element.getElementsByTagNameNS("*", local);
             if (nl != null && nl.getLength() > 0) return nl.item(0).getTextContent();
        }
        return null;
    }
    private String getConfigValue(Element activity, String configName) {
        Element config = (Element) activity.getElementsByTagName("config").item(0); 
        if (config == null) {
             config = (Element) activity.getElementsByTagNameNS("*", "config").item(0);
        }
        if (config != null) {
            return getTagValue(config, configName);
        }
        return null;
    }
    private String getDynamicOverride(Element activity) {
        return getConfigValue(activity, "processNameXPath");
    }
    private List<String> extractProcessPathsFromXPath(String xpath) {
        List<String> paths = new ArrayList<>();
        if (xpath == null || xpath.isEmpty()) return paths;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[\"']([^\"']+)[\"']");
        java.util.regex.Matcher matcher = pattern.matcher(xpath);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate.startsWith("/") || candidate.contains(".process")) {
                paths.add(candidate);
            }
        }
        return paths;
    }
    private boolean isConnector(String type) {
        if (type == null) return false;
        String t = type.toLowerCase();
        for (String excludePattern : excludePatterns) {
            if (t.contains(excludePattern.toLowerCase().trim())) {
                return false;
            }
        }
        for (String pattern : connectorPatterns) {
            if (t.contains(pattern.toLowerCase().trim())) {
                return true;
            }
        }
        return false;
    }
    private String getConnectorIcon(String type) {
        if (type == null) return "<&puzzle-piece>";
        String t = type.toLowerCase();
        if (t.contains(".jdbc.") || t.contains("database")) {
            return "<&data-transfer-download>";
        }
        if (t.contains(".jms.") || t.contains(".rv.")) {
            return "<&envelope-closed>";
        }
        if (t.contains(".file.") || t.contains(".ftp.")) {
            return "<&file>";
        }
        if (t.contains(".soap.") || t.contains(".http.")) {
            return "<&cloud>";
        }
        if (t.contains("restadapter")) {
            return "<&cloud>";
        }
        if (t.contains(".mail.")) {
            return "<&envelope-open>";
        }
        if (t.contains(".java.") && !t.contains("xmltojava") && !t.contains("javatoxml")) {
            return "<&code>";
        }
        if (t.contains("sleep")) {
            return "<&clock>";
        }
        if (t.contains("enginecommand") || t.contains("serviceagent")) {
            return "<&cog>";
        }
        if (t.contains("aesubscriber") || t.contains("aerpcserver") || t.contains(".ae.")) {
            return "<&transfer>";
        }
        if (t.contains("catch")) {
            return "<&warning>";
        }    
        if (t.contains("timer") || t.contains("schedule") || t.contains("poller")) {
            return "<&clock>";
        }    
        if (t.contains("sharedvariable")) {
            return "<&key>";
        }
        if (t.contains("log")) {
            return "<&file>";
        }
        if (t.contains("mapper")) {
             return "<&map>";
        }
        if (t.contains("null")) {
             return "<&x>"; 
        }
        if (t.contains("callprocess")) {
             return "<&cog>";
        }
        if (t.contains("rest") || t.contains("json")) {
             return "<&cloud>";
        }
        return "<&puzzle-piece>";
    }
    private byte[] renderPng(String puml) throws IOException {
        SourceStringReader reader = new SourceStringReader(puml);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            reader.outputImage(os);
            return os.toByteArray();
        }
    }
}
