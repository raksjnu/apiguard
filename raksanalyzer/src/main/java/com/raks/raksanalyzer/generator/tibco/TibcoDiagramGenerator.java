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

/**
 * Generates PlantUML diagrams for TIBCO BusinessWorks 5.x processes.
 * Supports:
 * 1. Flow Diagrams: Visual representation of process logic (Activities, Transitions, Groups).
 * 2. Integration Diagrams: End-to-end view of service connectivity (Service -> SubProcesses -> Connectors).
 */
public class TibcoDiagramGenerator {

    private static final Logger logger = LoggerFactory.getLogger(TibcoDiagramGenerator.class);
    private final ConfigurationManager config;
    private final List<String> connectorPatterns;
    private final List<String> excludePatterns;
    
    // Map for customizing resource type labels (optional overrides)
    private static final Map<String, String> RESOURCE_TYPE_LABELS = new HashMap<>();
    static {
        // Add custom mappings here if needed
        // e.g., RESOURCE_TYPE_LABELS.put("subprocess", "SubProc");
        // By default, we'll use the last token from pd:resourceType
    }
    
    public TibcoDiagramGenerator() {
        this.config = ConfigurationManager.getInstance();
        
        // Load connector patterns from properties
        String patterns = config.getProperty("tibco.connector.patterns", 
            ".jms.,.jdbc.,.file.,.soap.,.http.,.mail.,.ftp.,.tcp.,.ae.activities.,.json.,.rv.," +
            "getsharedvariable,setsharedvariable,getjobsharedvariable,setjobsharedvariable," +
            "sleepactivity,enginecommandactivity,javaactivity,javamethodactivity,javaevent," +
            "aesubscriberactivity,aerpcserveractivity,restadapteractivity");
        this.connectorPatterns = Arrays.asList(patterns.split(","));
        // Connector patterns loaded
        
        // Load exclusion patterns from properties
        String excludes = config.getProperty("tibco.connector.exclude.patterns", "xmltojava,javatoxml");
        this.excludePatterns = Arrays.asList(excludes.split(","));
        // Exclusion patterns loaded
    }
    
    /**
     * Extract readable label from pd:resourceType.
     * Takes the last token after "." and removes "Activity" suffix if present.
     * Examples:
     *   ae.process.group → group
     *   ae.process.subprocess → subprocess
     *   ae.activities.FileCopyActivity → FileCopy
     *   ae.activities.catch → catch
     */
    private static String getReadableTypeLabel(String resourceType) {
        if (resourceType == null || resourceType.isEmpty()) {
            return "Unknown";
        }
        
        // Handle TIBCO REST Plugin specifically (often has generic or empty resourceType?)
        if (resourceType.toLowerCase().contains("restactivity") || resourceType.contains("json.activities.RestActivity")) {
            return "REST Activity";
        }
        
        // Check if we have a custom mapping
        String lastToken = resourceType.substring(resourceType.lastIndexOf('.') + 1);
        if (RESOURCE_TYPE_LABELS.containsKey(lastToken)) {
            return RESOURCE_TYPE_LABELS.get(lastToken);
        }
        
        // Remove "Activity" suffix for cleaner labels
        if (lastToken.endsWith("Activity")) {
            lastToken = lastToken.substring(0, lastToken.length() - "Activity".length());
        }
        
        return lastToken;
    }

    /**
     * Generates a Flow Diagram (Process Logic) for a specific process file.
     * @param processFile The .process file
     * @return Byte array of the generated PNG image
     */
    public byte[] generateFlowDiagram(File processFile) {
        logger.info("Generating flow diagram for: {}", processFile.getName());
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
            logger.info("Generated PlantUML for {}, length: {} chars", processFile.getName(), puml != null ? puml.length() : 0);
            
            byte[] pngBytes = renderPng(puml);
            if (pngBytes != null && pngBytes.length > 0) {
                logger.info("✓ Successfully generated diagram for {}, size: {} bytes", processFile.getName(), pngBytes.length);
            } else {
                logger.warn("✗ Diagram generation returned empty/null for {}", processFile.getName());
            }
            return pngBytes;

        } catch (Exception e) {
            logger.error("✗ Error generating flow diagram for {}", processFile.getName(), e);
            return null;
        }
    }

    /**
     * Generates an Integration Diagram (Connectivity) starting from a Service.
     * @param serviceName Name of the service or operation
     * @param startProcessPath Relative path to the starting process (e.g., /BusinessProcess/myProc.process)
     * @param projectRoot Root directory of the TIBCO project
     * @return Byte array of the generated PNG image
     */
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
            // For Integration Diagrams in Section 2, the user prefers a "Flow" style diagram
            // but ONLY containing relevant integration points (Connectors, Relevant Sub-calls).
            // Non-relevant activities (Mapper, Assign, Log) should be filtered out,
            // bridging the flow transitions to connect relevant nodes.
            // Also supports recursion: clicking into a relevant sub-process expands it inline.
            
            // Normalize path
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
                // Pre-scan cache for relevance to avoid expensive recursion checks
                Map<String, Boolean> relevanceCache = new HashMap<>();

                StringBuilder sb = new StringBuilder();
                sb.append("@startuml\n");
                sb.append("skinparam shadowing false\n");
                sb.append("skinparam activity {\n");
                sb.append("  BackgroundColor #E6E6FA\n");
                sb.append("  BorderColor #6A5ACD\n"); // SlateBlue
                sb.append("  ArrowColor #483D8B\n"); // DarkSlateBlue
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
                
                // Force End Node Color (compatibility fix)
                sb.append("skinparam activityEndColor #E53935\n");
                
                // Set DPI for sharper images (same as Section 3)
                sb.append("skinparam dpi 300\n");
                
                // Limit diagram width for better fit
                sb.append("!pragma maxwidth 600\n");
                
                // Allow Service Agent overrides (if startProcessPath is implementation, label it)
                // If serviceName is passed, we might want to show it as a start node if no other starter exists?
                
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
                // PlantUML generated
                return pumlSource;

            } catch (Exception e) {
                logger.error("Error generating puml for {}", serviceName, e);
                return "@startuml\nrectangle \"Error parsing " + serviceName + "\"\n@enduml";
            }
    }

    private void generateRecursiveActivityPuml(StringBuilder sb, Document doc, File currentFile, File projectRoot, 
                                               Set<String> visited, Map<String, Boolean> relevanceCache, int depth, String overrideStarterLabel) {
        
        // Parse process structure
        Element root = doc.getDocumentElement();
        String startName = getTagValue(root, "pd:startName");
        
        // Build activity map
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
        
        // Add Groups to activityMap so they can be traversed
        for (int i = 0; i < groups.getLength(); i++) {
            Element grp = (Element) groups.item(i);
            activityMap.put(grp.getAttribute("name"), grp);
        }
        
        // Build transitions map (ONLY process-level transitions, NOT group-internal ones)
        Map<String, List<String>> transitions = new HashMap<>();
        NodeList transitionList = doc.getElementsByTagNameNS("*", "transition");
        for (int i = 0; i < transitionList.getLength(); i++) {
            Element t = (Element) transitionList.item(i);
            
            // Skip transitions that are inside a group
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
                // Skipping group-internal transition
                continue; // Skip group-internal transitions
            }
            
            String from = getTagValue(t, "pd:from");
            String to = getTagValue(t, "pd:to");
            transitions.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
        }
        
        // Log transitions for debugging
        // Transitions map built
        
        // Render starter at depth 0
        if (depth == 0 && starterElement != null) {
            String type = getTagValue(starterElement, "pd:type");
            String cleanType = type.substring(type.lastIndexOf('.') + 1);
            sb.append(":**").append(cleanType).append("**;\n");
        }
        
        // Start traversal
        Set<String> processed = new HashSet<>();
        String startNode = starterElement != null ? starterElement.getAttribute("name") : startName;
        
        // Traverse from the starter's successors (not the starter itself, since we already rendered it)
        List<String> starterSuccessors = transitions.get(startNode);
        if (starterSuccessors != null) {
            // Check for parallel flows from starter
            if (starterSuccessors.size() > 1) {
                // Filter fork branches to only include those with relevant content
                // IMPORTANT: Use a fresh visited set for EACH branch check to avoid false negatives
                List<String> branchesWithContent = new ArrayList<>();
                for (String successor : starterSuccessors) {
                    // Create a NEW visited set for each branch to avoid cross-contamination
                    if (hasRelevantContentInPath(successor, activityMap, transitions, projectRoot, relevanceCache, new HashSet<>())) {
                        branchesWithContent.add(successor);
                    }
                }
                
                // Only create fork if we have multiple branches with content
                if (branchesWithContent.size() > 1) {
                    // Parallel flow detected
                    sb.append("fork\n");
                    
                    // Use independent processed sets for each branch to allow same nodes in multiple paths
                    // But collect all processed nodes to merge after fork completes
                    List<Set<String>> branchProcessedSets = new ArrayList<>();
                    
                    for (int i = 0; i < branchesWithContent.size(); i++) {
                        if (i > 0) {
                            sb.append("fork again\n");
                        }
                        // Create a copy of processed for this branch
                        Set<String> branchProcessed = new HashSet<>(processed);
                        traverseProcess(sb, branchesWithContent.get(i), activityMap, transitions, branchProcessed, projectRoot, visited, relevanceCache, depth, groups);
                        branchProcessedSets.add(branchProcessed);
                    }
                    
                    sb.append("end fork\n");
                    
                    // Merge all branch processed sets back into main processed set
                    for (Set<String> branchProcessed : branchProcessedSets) {
                        processed.addAll(branchProcessed);
                    }
                } else if (branchesWithContent.size() == 1) {
                    // Only one branch has content - render as normal flow
                    traverseProcess(sb, branchesWithContent.get(0), activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups);
                }
                // If no branches have content, don't render anything
            } else {
                // Single successor - normal flow
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
            // Not an activity (could be Start/End) - follow transitions
            logger.debug("Node '{}' is not an activity, following transitions", nodeName);
            List<String> successors = transitions.get(nodeName);
            if (successors != null) {
                for (String successor : successors) {
                    traverseProcess(sb, successor, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups);
                }
            }
            return;
        }
        
        String type = getTagValue(activity, "pd:type");
        logger.debug("Processing activity '{}' with type '{}'", nodeName, type);
        
        // Check if this is a Group or Catch - render as partition
        if (type != null && (type.contains("LoopGroup") || type.contains("CriticalSectionGroup") || type.contains("CatchActivity"))) {
            // Get readable label from pd:resourceType
            String resourceType = getTagValue(activity, "pd:resourceType");
            String typeLabel = getReadableTypeLabel(resourceType);
            String partitionLabel = typeLabel + ": " + nodeName;
            
            // Rendering as partition
            
            sb.append("partition \"").append(partitionLabel).append("\" {\n");
            
            // For Groups, traverse INTERNAL activities (not external transitions)
            // Get activities that are children of this Group element
            NodeList groupActivities = activity.getElementsByTagNameNS("*", "activity");
            if (groupActivities != null && groupActivities.getLength() > 0) {
                // Group has internal activities
                for (int i = 0; i < groupActivities.getLength(); i++) {
                    Element groupAct = (Element) groupActivities.item(i);
                    String groupActName = groupAct.getAttribute("name");
                    // Traversing group activity
                    traverseProcess(sb, groupActName, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups);
                }
            }
            
            sb.append("}\n");
            
            // IMPORTANT: Continue to follow the Group's EXTERNAL transitions (connectors after the Group)
            List<String> successors = transitions.get(nodeName);
            if (successors != null) {
                // Following external transitions
                for (String successor : successors) {
                    traverseProcess(sb, successor, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups);
                }
            }
            return;
        }
        
        // Check if activity is integration-relevant
        boolean isRelevant = type != null && isIntegrationRelevant(activity, projectRoot, relevanceCache);
        logger.debug("Activity '{}' is relevant: {}", nodeName, isRelevant);
        
        if (isRelevant) {
            if (type.contains("CallProcessActivity")) {
                // Sub-process - check for spawn and dynamic override
                String subProcPath = getConfigValue(activity, "processName");
                String dynamicOverride = getDynamicOverride(activity);
                String spawnConfig = getConfigValue(activity, "spawn");
                boolean isSpawn = "true".equalsIgnoreCase(spawnConfig);
                
                // Handle dynamic override - parse XPath to find process paths
                if (dynamicOverride != null && !dynamicOverride.trim().isEmpty()) {
                    List<String> processPaths = extractProcessPathsFromXPath(dynamicOverride);
                    // Process paths found
                    
                    for (String procPath : processPaths) {
                        String cleanPath = procPath.trim();
                        if (cleanPath.startsWith("/")) cleanPath = cleanPath.substring(1);
                        
                        // Extract just the process name from path
                        String processName = cleanPath.substring(cleanPath.lastIndexOf("/") + 1);
                        if (processName.endsWith(".process")) {
                            processName = processName.substring(0, processName.length() - 8);
                        }
                        
                        // Render override subprocess with partition and recursion
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
                        // Override subprocess rendered
                    }
                } else if (isSpawn && subProcPath != null) {
                    // Render spawn subprocess with partition and recursion
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
                        // Spawn subprocess rendered
                    }
                } else if (subProcPath != null) {
                    // Regular subprocess - render as partition with recursion
                    if (subProcPath.startsWith("/")) subProcPath = subProcPath.substring(1);
                    File subFile = new File(projectRoot, subProcPath);
                    
                    if (subFile.exists() && !visited.contains(subFile.getAbsolutePath())) {
                        Set<String> nextVisited = new HashSet<>(visited);
                        nextVisited.add(subFile.getAbsolutePath());
                        
                        // Get readable label from pd:resourceType
                        String resourceType = getTagValue(activity, "pd:resourceType");
                        String typeLabel = getReadableTypeLabel(resourceType);
                        
                        // CallProcess rendered as partition
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
                // Connector - render with icon, resourceType on line 1, name on line 2
                String resourceType = getTagValue(activity, "pd:resourceType");
                String cleanType = getReadableTypeLabel(resourceType);
                
                // Get icon based on connector type
                String icon = getConnectorIcon(type);
                
                // Line 1: Icon + ResourceType (bold)
                // Line 2: Activity Name
                sb.append(":").append(icon).append(" <b>").append(cleanType).append("</b>\n");
                sb.append(nodeName).append(";\n");
                
                // Connector rendered
            }
        }
        
        // Only follow transitions if current node is relevant
        // Check successor relevance to avoid rendering arrows to non-connectors
        List<String> successors = transitions.get(nodeName);
        if (successors != null && isRelevant) {
            logger.debug("Following {} transitions from relevant node '{}'", successors.size(), nodeName);
            
            // Filter successors to only include relevant ones for rendering
            List<String> relevantSuccessors = new ArrayList<>();
            for (String successor : successors) {
                Element successorActivity = activityMap.get(successor);
                if (successorActivity != null) {
                    boolean successorRelevant = isIntegrationRelevant(successorActivity, projectRoot, relevanceCache);
                    if (successorRelevant) {
                        relevantSuccessors.add(successor);
                    } else {
                        // Continue traversal through non-relevant node without rendering arrow
                        logger.debug("Skipping arrow to non-relevant successor '{}', continuing traversal", successor);
                        traverseProcess(sb, successor, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups);
                    }
                } else {
                    // Unknown successor (might be End), include it
                    relevantSuccessors.add(successor);
                }
            }
            
            // Render arrows only to relevant successors
            if (!relevantSuccessors.isEmpty()) {
                // Check for parallel flows (multiple relevant successors)
                if (relevantSuccessors.size() > 1) {
                    // Filter fork branches to only include those with relevant content
                    List<String> branchesWithContent = new ArrayList<>();
                    for (String successor : relevantSuccessors) {
                        if (hasRelevantContentInPath(successor, activityMap, transitions, projectRoot, relevanceCache, new HashSet<>())) {
                            branchesWithContent.add(successor);
                        }
                    }
                    
                    // Only create fork if we have multiple branches with content
                    if (branchesWithContent.size() > 1) {
                        // Parallel flow detected
                        sb.append("fork\n");
                        
                        for (int i = 0; i < branchesWithContent.size(); i++) {
                            if (i > 0) {
                                sb.append("fork again\n");
                            }
                            traverseProcess(sb, branchesWithContent.get(i), activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups);
                        }
                        
                        sb.append("end fork\n");
                    } else if (branchesWithContent.size() == 1) {
                        // Only one branch has content - render as normal flow
                        traverseProcess(sb, branchesWithContent.get(0), activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups);
                    }
                    // If no branches have content, don't render anything
                } else {
                    // Single relevant successor - normal flow
                    for (String successor : relevantSuccessors) {
                        traverseProcess(sb, successor, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups);
                    }
                }
            }
        } else if (successors != null && !isRelevant) {
            // Non-relevant node - skip rendering but continue traversal to find next relevant node
            logger.debug("Skipping arrows from non-relevant node '{}', continuing traversal", nodeName);
            for (String successor : successors) {
                traverseProcess(sb, successor, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups);
            }
        }
    }
    
    private boolean isStarter(String name, List<Element> starters) {
        for(Element s : starters) if(s.getAttribute("name").equals(name)) return true;
        return false;
    }

    private List<String> findRelevantSuccessors(String current, Map<String, List<String>> adj, Set<String> relevantNodes, Set<String> visited) {
        List<String> result = new ArrayList<>();
        if (visited.contains(current)) return result;
        visited.add(current);
        
        List<String> neighbors = adj.get(current);
        if (neighbors == null) return result;
        
        for (String next : neighbors) {
            if (relevantNodes.contains(next)) {
                result.add(next);
            } else {
                // Recursively search
                result.addAll(findRelevantSuccessors(next, adj, relevantNodes, visited));
            }
        }
        return result;
    }

    private boolean isIntegrationRelevant(Element activity, File projectRoot, Map<String, Boolean> cache) {
        String name = activity.getAttribute("name");
        
        // 1. Direct Connectors
        String type = getTagValue(activity, "pd:type");
        if (type == null) return false;
        
        // Check if this is a Group containing connectors
        if (type.contains("Group") || activity.getNodeName().contains("group")) {
            // Check if group contains any connector activities
            NodeList groupActivities = activity.getElementsByTagNameNS("*", "activity");
            for (int i = 0; i < groupActivities.getLength(); i++) {
                Element groupAct = (Element) groupActivities.item(i);
                if (isIntegrationRelevant(groupAct, projectRoot, cache)) {
                    logger.debug("Group '{}' contains relevant activities", activity.getAttribute("name"));
                    return true;
                }
            }
            // Group doesn't contain connectors
            return false;
        }
        
        if (isConnector(type)) return true;
        
        // 2. Structural/Grouping that might contain integration?
        // In this flat node list approach, groups are just visual boundaries usually.
        // We will ignore groups for filtering but maybe include them if they wrap relevant nodes?
        // Simplification: We look at activities. Group structure is secondary in graph.
        
        // 3. Sub-Process Calls (Recursive check)
        if (type.contains("CallProcessActivity")) {
             String subPath = getConfigValue(activity, "processName");
             if (subPath == null) return false;
             
             if (cache.containsKey(subPath)) return cache.get(subPath);
             
             // Check existence
             if (subPath.startsWith("/")) subPath = subPath.substring(1);
             File subFile = new File(projectRoot, subPath);
             if (!subFile.exists()) {
                 cache.put(subPath, false);
                 return false;
             }
             
             try {
                // Quick parse
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true); // Fix: Must be NS aware to find pd:activity
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
    
    /**
     * Recursively checks if a path starting from the given node contains any integration-relevant activities.
     * Used to filter out fork branches that lead only to excluded activities.
     */
    private boolean hasRelevantContentInPath(String nodeName, Map<String, Element> activityMap, 
                                             Map<String, List<String>> transitions, File projectRoot,
                                             Map<String, Boolean> relevanceCache, Set<String> visited) {
        // Avoid infinite loops
        if (visited.contains(nodeName)) return false;
        visited.add(nodeName);
        
        // Check if this node itself is relevant
        Element activity = activityMap.get(nodeName);
        if (activity != null && isIntegrationRelevant(activity, projectRoot, relevanceCache)) {
            return true;
        }
        
        // Check successors recursively
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

    // --- Flow Diagram Logic ---

    private String generateFlowPuml(Document doc, String processName) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("skinparam shadowing false\n");
        sb.append("skinparam activity {\n");
        sb.append("  BackgroundColor #E6E6FA\n");
        sb.append("  BorderColor #6A5ACD\n"); // SlateBlue
        sb.append("  ArrowColor #483D8B\n"); // DarkSlateBlue
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
        
        // Force End Node Color (compatibility fix)
        sb.append("skinparam activityEndColor #E53935\n");
        
        sb.append("skinparam dpi 300\n");
        sb.append("!pragma maxwidth 600\n");

        try {
            Element root = doc.getDocumentElement();
            String startName = getTagValue(root, "pd:startName");
            
            // Build maps
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
            
            // Add Starter to activityMap (Crucial for Section 3 to show Start Event)
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
            
            // Build transition map AND label map
            NodeList transitionList = doc.getElementsByTagNameNS("*", "transition");
            for (int i = 0; i < transitionList.getLength(); i++) {
                Element t = (Element) transitionList.item(i);
                if (isInsideGroup(t)) continue;
                String from = getTagValue(t, "pd:from");
                String to = getTagValue(t, "pd:to");
                transitions.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
                
                String xpathDesc = getTagValue(t, "pd:xpathDescription");
                if (xpathDesc != null && !xpathDesc.isEmpty()) {
                    // Store label: from -> to -> label
                    // We need a nested map structure for this
                }
            }
            
            // Re-parsing transition list to populate labels and conditions
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
                    
                    // Fallback Label Logic: If no label exists, use conditionType
                    Map<String, String> fromLabels = transitionLabels.computeIfAbsent(from, k -> new HashMap<>());
                    if (!fromLabels.containsKey(to)) {
                        // Capitalize for better readability (outcome-based)
                        if ("otherwise".equalsIgnoreCase(conditionType)) {
                            fromLabels.put(to, "Otherwise");
                        } else if ("error".equalsIgnoreCase(conditionType)) {
                            fromLabels.put(to, "Error");
                        } else if ("xpath".equalsIgnoreCase(conditionType)) {
                            // Try to get actual xpath
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
            
             // 1. Render Main Flow
             sb.append("start\n");
             traverseFlowFromNode(sb, startName, activityMap, groupMap, transitions, transitionLabels, transitionConditions, new HashSet<>(), doc, null, false);
             sb.append("stop\n");
             
             // 2. Render Catch Flows
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
        
        // Stop if we reached the merge point (join node)
        if (stopAtNode != null && stopAtNode.equals(nodeName)) {
            return;
        }

        if (processed.contains(nodeName)) return;
        processed.add(nodeName);
        
        List<String> successors = transitions.get(nodeName);
        
        // Check if this node is a Conditional Switch
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
        
        // Render Node
        if (isSwitch) {
            // Switch rendering handles its own visual (Switch Diamond), so we usually don't skip it
            // unless we strictly want to merge-and-split without a node. 
            // But Switch implies a decision, so rendering it is correct.
            sb.append("switch (").append(nodeName).append(")\n");
            
            // Handle Switch Branches
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
            
            // Continue from join node
            if (joinNode != null) {
                // If the join node is a NullActivity, we skip rendering it to avoid "Double Diamond"
                // The 'endswitch' diamond serves as the visual merge point.
                boolean isNullActivity = false;
                Element joinEl = activityMap.get(joinNode);
                if (joinEl != null) {
                     String t = getTagValue(joinEl, "pd:type");
                     if (t != null && t.contains("NullActivity")) isNullActivity = true;
                }
                traverseFlowFromNode(sb, joinNode, activityMap, groupMap, transitions, transitionLabels, transitionConditions, processed, doc, stopAtNode, isNullActivity);
            }
            
        } else {
            // Standard Rendering (Single or Fork)
            // Skip rendering if requested (e.g. for NullActivity merge nodes)
            if (!skipFirstNodeRender) {
                if (groupMap.containsKey(nodeName)) {
                    renderGroup(sb, groupMap.get(nodeName), doc);
                } else if (activityMap.containsKey(nodeName)) {
                // Just calling renderActivity is enough now as logic handles styling
                renderActivity(sb, activityMap.get(nodeName));
            }
            }
            
            // Follow transitions
            if (successors != null && !successors.isEmpty()) {
                if (successors.size() > 1) {
                    // Parallel Fork
                    // Attempt to find a common Join Node where these branches merge
                    String joinNode = findCommonJoinNode(successors, transitions);
                    
                    sb.append("fork\n");
                    for (int i = 0; i < successors.size(); i++) {
                        if (i > 0) sb.append("fork again\n");
                        
                        String successor = successors.get(i);
                        // Render Label on arrow
                        if (transitionLabels != null && transitionLabels.containsKey(nodeName)) {
                            String label = transitionLabels.get(nodeName).get(successor);
                            if (label != null) {
                                label = label.replace("\"", "\\\"");
                                sb.append("-> \"").append(label).append("\"\n");
                            } else {
                                sb.append("->\n"); // No label, just an arrow
                            }
                        } else {
                            sb.append("->\n"); // No labels map, just an arrow
                        }
    
                        traverseFlowFromNode(sb, successor, activityMap, groupMap, transitions, transitionLabels, transitionConditions, processed, doc, joinNode, false);
                    }
                    sb.append("end fork\n");
                    
                    // Now continue traversal from the join node (single path)
                    if (joinNode != null) {
                         // Check NullActivity Logic for Forks too
                        boolean isNullActivity = false;
                        Element joinEl = activityMap.get(joinNode);
                        if (joinEl != null) {
                             String t = getTagValue(joinEl, "pd:type");
                             if (t != null && t.contains("NullActivity")) isNullActivity = true;
                        }
                        traverseFlowFromNode(sb, joinNode, activityMap, groupMap, transitions, transitionLabels, transitionConditions, processed, doc, stopAtNode, isNullActivity);
                    }
                } else {
                    // Single path
                    String successor = successors.get(0);
                    
                    // Render Label on arrow (only if successor is a real activity/group)
                    boolean successorExists = activityMap.containsKey(successor) || groupMap.containsKey(successor);
                    
                    if (successorExists && transitionLabels != null && transitionLabels.containsKey(nodeName)) {
                        String label = transitionLabels.get(nodeName).get(successor);
                        if (label != null) {
                            label = label.replace("\"", "\\\"");
                             sb.append("-> \"").append(label).append("\"\n");
                        } else {
                            sb.append("->\n"); // No label, just arrow
                        }
                    } else {
                        // Successor doesn't exist or no label - just output arrow
                        // This handles transitions to "End" or other non-existent nodes
                        sb.append("->\n");
                    }
                    
                    traverseFlowFromNode(sb, successor, activityMap, groupMap, transitions, transitionLabels, transitionConditions, processed, doc, stopAtNode, false);
                }
            }
        }
    }
    
    // Find the first common node reachable from all startNodes
    private String findCommonJoinNode(List<String> startNodes, Map<String, List<String>> transitions) {
        if (startNodes == null || startNodes.isEmpty()) return null;
        
        // Get reachable sets for each branch
        List<Set<String>> reachableSets = new ArrayList<>();
        for (String start : startNodes) {
            Set<String> reachable = new HashSet<>();
            collectReachable(start, transitions, reachable);
            reachableSets.add(reachable);
        }
        
        // Find intersection (nodes present in ALL branches)
        Set<String> common = new HashSet<>(reachableSets.get(0));
        for (int i = 1; i < reachableSets.size(); i++) {
            common.retainAll(reachableSets.get(i));
        }
        
        if (common.isEmpty()) return null;
        
        // Find the "earliest" common node
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
        
        // Special Handling for NullActivity (Join/Split) -> Render as Diamond (Decision)
        if (type != null && type.contains("NullActivity")) {
             // In PlantUML Activity Beta, 'if ( ) then' renders a diamond
             // We use empty parens if we don't want a label inside, or name if we do.
             sb.append("if (").append(name).append(") then\nendif\n");
             return;
        }

        // Special Styling for Spawn/Override (Moved here to work inside groups too)
        boolean renderedSpecial = false;
        if (type != null && type.contains("CallProcessActivity")) {
             String dynamicOverride = getDynamicOverride(activity);
             String spawnConfig = getConfigValue(activity, "spawn");
             boolean isSpawn = "true".equalsIgnoreCase(spawnConfig);
             
             if (dynamicOverride != null && !dynamicOverride.trim().isEmpty()) {
                 sb.append("partition \"<b>override-subprocess</b>\\n").append(name).append("\" #FFF3E0 {\n");
                 // Recursive call to render the internal node (icon+name)
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
        // Simplified rendering: Icon \n Bold Name \n Type
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
        
        // Parse internal transitions AND labels
        Map<String, String> internalTransitions = new HashMap<>(); 
        Map<String, String> internalLabels = new HashMap<>(); // From -> Label
        
        NodeList transitions = group.getElementsByTagNameNS("*", "transition");
        for (int i = 0; i < transitions.getLength(); i++) {
            Element t = (Element) transitions.item(i);
            // Ensure transition is a direct child of this group
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
        
        // Collect Direct Children (Activities and Groups)
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
        
        // Order by transition
    // Find Group Start Node: A node that is in internalNodes but NOT a target in internalTransitions
    Set<String> targets = new HashSet<>(internalTransitions.values());
    String startNode = null;
    for (String name : internalNodes.keySet()) {
        if (!targets.contains(name)) {
            startNode = name; // Found a start node (no incoming transitions inside group)
            break;
        }
    }
    
    // If explicit start "start" exists in transitions? 
    // Usually TIBCO groups often have transition from "start" to first activity.
    // If 'start' maps to something, that something is the first real activity.
    if (internalTransitions.containsKey("start")) {
        startNode = internalTransitions.get("start");
    }

    String current = startNode;
    
    int steps = 0;
    Set<String> rendered = new HashSet<>();
    while (current != null && steps < 50) {
            String next = internalTransitions.get(current);
            String label = internalLabels.get(current);
            
            // Output label for arrow to NEXT if next exists
            // Wait, PlantUML renders arrows implicitly between sequential activities.
            // If I print "-> label", it attaches to the arrow coming OUT of the PREVIOUS activity rendered.
            // But here we are about to render 'current'.
            // Actually, we render 'current', then if 'next' comes, the arrow is between current and next.
            // So we should print '-> label' AFTER rendering current, but BEFORE rendering next.
            // BUT, the loop renders 'current'. Then loops to 'next'.
            
            // Logic:
            // Render Current Node
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
            
            // Now prepare for next
            if (next != null) {
                if (label != null) {
                    sb.append("-> \"").append(label).append("\"\n");
                }
            }
            
            current = next;
            if ("end".equals(current)) break;
            steps++;
        }
        
        // Fallback for disconnected nodes
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

    // --- Integration Diagram Logic ---

    private void discoverConnections(File processFile, String parentId, File projectRoot, 
                                   Set<String> visitedProcesses, Set<String> nodes, Set<String> edges, int depth) {
        if (!processFile.exists() || visitedProcesses.contains(processFile.getAbsolutePath())) return;
        visitedProcesses.add(processFile.getAbsolutePath());
        
        // Limit depth to avoid massive diagrams? User said "end to end", so maybe full depth.
        if (depth > 10) return; 

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(true);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(processFile);
            
            NodeList activities = doc.getElementsByTagNameNS("*", "activity");
            for (int i = 0; i < activities.getLength(); i++) {
                Element activity = (Element) activities.item(i);
                String type = getTagValue(activity, "pd:type");
                String name = activity.getAttribute("name");
                
                // 1. Sub-Process Call
                if (type.contains("CallProcessActivity")) {
                    String subProcessPath = getConfigValue(activity, "processName");
                    if (subProcessPath != null) {
                         if (subProcessPath.startsWith("/")) subProcessPath = subProcessPath.substring(1);
                         
                         // Create Edge
                         String subProcId = sanitizeId(subProcessPath);
                         nodes.add("rectangle \"" + new File(subProcessPath).getName() + "\" as " + subProcId + " <<Process>>");
                         edges.add(parentId + " ..> " + subProcId); // Dashed arrow for call
                         
                         // Recurse
                         File subFile = new File(projectRoot, subProcessPath);
                         discoverConnections(subFile, subProcId, projectRoot, visitedProcesses, nodes, edges, depth + 1);
                    }
                }
                // 2. Connector (Loose detection based on type)
                else if (isConnector(type)) {
                    String connId = sanitizeId(name + "_" + depth + "_" + i);
                    String stereotype = getConnectorStereotype(type);
                    
                    nodes.add("database \"" + name + "\" as " + connId + " " + stereotype);
                    edges.add(parentId + " --> " + connId);
                }
            }
            
        } catch (Exception e) {
            logger.warn("Failed to parse for integration: {}", processFile.getName());
        }
    }

    // --- Helpers ---

    private String getTagValue(Element element, String tagName) {
        NodeList nl = element.getElementsByTagName(tagName);
        if (nl != null && nl.getLength() > 0) {
            return nl.item(0).getTextContent();
        }
        // Try without prefix if namespaced lookup fails or vice versa?
        // DOM getElementsByTagNameNS("*", "localName") is safer but verbose.
        // For simplicity assuming standard "pd:" prefix or match.
        if (tagName.contains(":")) {
             String local = tagName.substring(tagName.indexOf(":")+1);
             nl = element.getElementsByTagNameNS("*", local);
             if (nl != null && nl.getLength() > 0) return nl.item(0).getTextContent();
        }
        return null;
    }

    private String getConfigValue(Element activity, String configName) {
        // <config><processName>...</processName></config>
        Element config = (Element) activity.getElementsByTagName("config").item(0); // or ns aware
        if (config == null) {
             config = (Element) activity.getElementsByTagNameNS("*", "config").item(0);
        }
        if (config != null) {
            return getTagValue(config, configName);
        }
        return null;
    }

    private String getDynamicOverride(Element activity) {
        // <config><processNameXPath>...</processNameXPath></config>
        return getConfigValue(activity, "processNameXPath");
    }

    /**
     * Extract process paths from XPath expression.
     * Looks for quoted strings that look like process paths (start with / or contain .process)
     */
    private List<String> extractProcessPathsFromXPath(String xpath) {
        List<String> paths = new ArrayList<>();
        if (xpath == null || xpath.isEmpty()) return paths;
        
        // Match quoted strings in XPath (both single and double quotes)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[\"']([^\"']+)[\"']");
        java.util.regex.Matcher matcher = pattern.matcher(xpath);
        
        while (matcher.find()) {
            String candidate = matcher.group(1);
            // Check if it looks like a process path
            if (candidate.startsWith("/") || candidate.contains(".process")) {
                paths.add(candidate);
                logger.debug("Extracted process path from XPath: {}", candidate);
            }
        }
        
        return paths;
    }

    private boolean isConnector(String type) {
        if (type == null) return false;
        String t = type.toLowerCase();
        
        // First check exclusion patterns (if matched, NOT a connector)
        for (String excludePattern : excludePatterns) {
            if (t.contains(excludePattern.toLowerCase().trim())) {
                logger.debug("isConnector('{}') = false (matched exclusion pattern: '{}')", type, excludePattern);
                return false;
            }
        }
        
        // Then check inclusion patterns
        for (String pattern : connectorPatterns) {
            if (t.contains(pattern.toLowerCase().trim())) {
                logger.debug("isConnector('{}') = true (matched pattern: '{}')", type, pattern);
                return true;
            }
        }
        
        logger.debug("isConnector('{}') = false (no pattern match)", type);
        return false;
    }
    
    /**
     * Get PlantUML icon for connector type.
     * Returns appropriate icon based on activity type.
     */
    private String getConnectorIcon(String type) {
        if (type == null) return "<&puzzle-piece>";
        String t = type.toLowerCase();
        
        // Database connectors
        if (t.contains(".jdbc.") || t.contains("database")) {
            return "<&data-transfer-download>";
        }
        
        // Messaging connectors
        if (t.contains(".jms.") || t.contains(".rv.")) {
            return "<&envelope-closed>";
        }
        
        // File connectors
        if (t.contains(".file.") || t.contains(".ftp.")) {
            return "<&file>";
        }
        
        // Web service connectors
        if (t.contains(".soap.") || t.contains(".http.")) {
            return "<&cloud>";
        }
        
        // REST Adapter
        if (t.contains("restadapter")) {
            return "<&cloud>";
        }
        
        // Email connectors
        if (t.contains(".mail.")) {
            return "<&envelope-open>";
        }
        
        // Java connectors
        if (t.contains(".java.") && !t.contains("xmltojava") && !t.contains("javatoxml")) {
            return "<&code>";
        }
        
        // Sleep/Timer
        if (t.contains("sleep")) {
            return "<&clock>";
        }
        
        // Engine Command / Service Agent
        if (t.contains("enginecommand") || t.contains("serviceagent")) {
            return "<&cog>";
        }
        
        // AE Adapters
        if (t.contains("aesubscriber") || t.contains("aerpcserver") || t.contains(".ae.")) {
            return "<&transfer>";
        }
        
        // Catch / Error Handler
        if (t.contains("catch")) {
            return "<&warning>";
        }    
        
        // Timer / Scheduler
        if (t.contains("timer") || t.contains("schedule") || t.contains("poller")) {
            return "<&clock>";
        }    
        
        // Shared Variables
        if (t.contains("sharedvariable")) {
            return "<&key>";
        }
        
        // Default
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
    
    private String getConnectorStereotype(String type) {
        // Extract token after last '.'
        if (type == null || type.isEmpty()) return "<<Activity>>";
        
        String simpleName = type;
        if (type.contains(".")) {
            simpleName = type.substring(type.lastIndexOf('.') + 1);
        }
        
        return "<<" + simpleName + ">>";
    }

    private String sanitizeId(String name) {
        if (name == null) return "null_id";
        return name.replaceAll("[^a-zA-Z0-9]", "_");
    }

    private byte[] renderPng(String puml) throws IOException {
        SourceStringReader reader = new SourceStringReader(puml);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            reader.outputImage(os);
            return os.toByteArray();
        }
    }
}
