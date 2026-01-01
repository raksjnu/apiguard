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
    private final boolean showTransitionLabels;
    private final boolean showExceptionHandlers;
    static {
        System.setProperty("PLANTUML_LIMIT_SIZE", "16384");
    }
    public TibcoDiagramGenerator() {
        this.config = ConfigurationManager.getInstance();
        String patterns = config.getProperty("tibco.connector.patterns", 
            ".jms.,.jdbc.,.file.,.soap.,.http.,.mail.,.ftp.,.tcp.,.ae.,.json.,.rv.," +
            "getsharedvariable,setsharedvariable,getjobsharedvariable,setjobsharedvariable," +
            "sleepactivity,enginecommandactivity,javaactivity,javamethodactivity,javaevent," +
            "aesubscriberactivity,aerpcserveractivity,restadapteractivity," +
            "aepublisheractivity,xmlrendereractivity,xmlparseractivity,javatoxmlactivity,xmltojavaactivity," +
            "httpsignalinactivity");
        this.connectorPatterns = Arrays.asList(patterns.split(","));
        logger.info("Loaded {} connector patterns: {}", connectorPatterns.size(), connectorPatterns);
        String excludes = config.getProperty("tibco.connector.exclude.patterns", "");
        this.excludePatterns = Arrays.asList(excludes.split(","));
        logger.info("Loaded {} exclude patterns: {}", excludePatterns.size(), excludePatterns);
        this.showTransitionLabels = "true".equalsIgnoreCase(config.getProperty("tibco.diagram.transition.labels.enabled", "true"));
        this.showExceptionHandlers = "true".equalsIgnoreCase(config.getProperty("tibco.diagram.show.exception.handlers", "false"));
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
                // !pragma maxwidth removed to allow full resolution logic
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                dbFactory.setNamespaceAware(true); 
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(processFile);
                Set<String> visited = new HashSet<>();
                visited.add(processFile.getAbsolutePath());
                CallChain initialChain = new CallChain();
                initialChain = initialChain.push(processFile.getAbsolutePath());
                sb.append("start\n");
                generateRecursiveActivityPuml(sb, doc, processFile, projectRoot, visited, relevanceCache, 0, overrideStarterLabel, initialChain);
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
                                               Set<String> visited, Map<String, Boolean> relevanceCache, int depth, String overrideStarterLabel, CallChain callChain) {
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
            // We WANT internal transitions for Integration Diagrams too, to show flow inside groups.
            // if (insideGroup) {
            //    continue; 
            // }
            String from = getTagValue(t, "pd:from");
            String to = getTagValue(t, "pd:to");
            transitions.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
        }
        
        // Collect transition labels if enabled
        Map<String, Map<String, String>> transitionLabels = new HashMap<>();
        if (this.showTransitionLabels) {
            for (int i = 0; i < transitionList.getLength(); i++) {
                Element t = (Element) transitionList.item(i);
                if (isInsideGroup(t)) continue;
                String from = getTagValue(t, "pd:from");
                String to = getTagValue(t, "pd:to");
                
                String label = null;
                String xpathDesc = getTagValue(t, "pd:xpathDescription");
                String conditionType = getTagValue(t, "pd:conditionType");
                String xpathVal = getTagValue(t, "pd:xpath");

                // 1. Determine base label
                if (xpathDesc != null && !xpathDesc.trim().isEmpty()) {
                    label = xpathDesc.trim();
                }

                // 2. Map condition types if label is still null
                if (label == null) {
                     if ("xpth".equalsIgnoreCase(conditionType) || "xpath".equalsIgnoreCase(conditionType)) {
                         // If no description, use xpath value itself, or fallback to "XPath"
                         label = (xpathVal != null && !xpathVal.trim().isEmpty()) ? xpathVal.trim() : "XPath";
                         // If we used xpathVal as the label, set xpathVal to null so we don't append it again
                         if (label.equals(xpathVal != null ? xpathVal.trim() : "")) {
                             xpathVal = null; 
                         }
                     } else if ("otherwise".equalsIgnoreCase(conditionType)) {
                         label = "Otherwise";
                     } else if ("error".equalsIgnoreCase(conditionType)) {
                         label = "Error";
                     }
                }

                // 3. Append XPath value if it exists and wasn't already used as the label
                if (xpathVal != null && !xpathVal.trim().isEmpty()) {
                    // PlantUML allows \n for multiline labels
                    if (label != null && !label.equals(xpathVal.trim())) {
                        label = label + "\\n" + xpathVal.trim();
                    } else if (label == null) {
                        label = xpathVal.trim();
                    }
                }
                
                if (label != null) {
                    transitionLabels.computeIfAbsent(from, k -> new HashMap<>()).put(to, label);
                    logger.debug("[LABEL EXTRACTION] From '{}' to '{}': label='{}'", from, to, label);
                } else {
                    logger.debug("[LABEL EXTRACTION] From '{}' to '{}': NO LABEL", from, to);
                }
            }
            logger.info("[LABEL EXTRACTION] Total transition labels extracted: {}", transitionLabels.size());
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
                        logger.debug("[FORK] Branch to '{}' HAS relevant content", successor);
                    } else {
                        logger.debug("[FORK] Branch to '{}' has NO relevant content - SKIPPED", successor);
                    }
                }
                logger.info("[INTEGRATION FORK] Starter '{}' has {} total successors, {} with relevant content", startNode, starterSuccessors.size(), branchesWithContent.size());
                if (branchesWithContent.size() > 1) {
                    sb.append("fork\n");
                    List<Set<String>> branchProcessedSets = new ArrayList<>();
                    for (int i = 0; i < branchesWithContent.size(); i++) {
                        if (i > 0) {
                            sb.append("fork again\n");
                        }
                        
                        // Add transition label if available
                        String successor = branchesWithContent.get(i);
                        logger.info("[INTEGRATION FORK] Rendering branch {} of {}: '{}'", i+1, branchesWithContent.size(), successor);
                        if (transitionLabels.containsKey(startNode) && transitionLabels.get(startNode).containsKey(successor)) {
                             String label = transitionLabels.get(startNode).get(successor).replace("\"", "\\\"");
                             logger.info("[INTEGRATION FORK] Adding label '{}' to branch '{}'", label, successor);
                             sb.append("-> \"").append(label).append("\";\n");
                        } else {
                             logger.debug("[INTEGRATION FORK] No label for branch '{}'", successor);
                             sb.append("->\n");  // Still need arrow even without label!
                        }
                        
                        Set<String> branchProcessed = new HashSet<>(processed);
                        traverseProcess(sb, branchesWithContent.get(i), activityMap, transitions, branchProcessed, projectRoot, visited, relevanceCache, depth, groups, callChain, transitionLabels);
                        branchProcessedSets.add(branchProcessed);
                    }
                    sb.append("end fork\n");
                    
                    for (Set<String> branchProcessed : branchProcessedSets) {
                        processed.addAll(branchProcessed);
                    }
                } else if (branchesWithContent.size() == 1) {
                    traverseProcess(sb, branchesWithContent.get(0), activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups, callChain, transitionLabels);
                }
            } else {
                for (String successor : starterSuccessors) {
                    traverseProcess(sb, successor, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups, callChain, transitionLabels);
                }
            }
        }
        
        // Traverse Catch/Exception Handlers (Unvisited Catch Activities)
        List<String> catchActivities = new ArrayList<>();
        for (Map.Entry<String, Element> entry : activityMap.entrySet()) {
            String name = entry.getKey();
            Element act = entry.getValue();
            String type = getTagValue(act, "pd:type");
            if (type != null && type.contains("CatchActivity") && !processed.contains(name)) {
                catchActivities.add(name);
            }
        }
        
        if (showExceptionHandlers && !catchActivities.isEmpty()) {
            sb.append("partition \"Exception Handlers\" #FFEBEE {\n");
            for (String catchName : catchActivities) {
                // Render the catch activity itself first
                Element catchAct = activityMap.get(catchName);
                if (catchAct != null) {
                    // Check if we need to render it as a connector or just a node
                    String type = getTagValue(catchAct, "pd:type");
                    String resourceType = getTagValue(catchAct, "pd:resourceType");
                    String readableType = getReadableTypeLabel(resourceType);
                    String icon = getConnectorIcon(type);
                    sb.append(":").append(icon).append(" **").append(catchName).append("**\\n").append(readableType).append(";\n");
                    processed.add(catchName); // Mark as processed
                }
                traverseProcess(sb, catchName, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups, callChain, transitionLabels);
            }
            sb.append("}\n");
        }
    }

    // Old Signature Overload
    private void traverseProcess(StringBuilder sb, String nodeName, Map<String, Element> activityMap, 
                                 Map<String, List<String>> transitions, Set<String> processed, 
                                 File projectRoot, Set<String> visited, Map<String, Boolean> relevanceCache, 
                                 int depth, NodeList groupsNodeList, CallChain callChainObj,
                                 Map<String, Map<String, String>> transitionLabels) {
        // Convert NodeList groups to Map<String, Element> groups
        Map<String, Element> groupsMap = new HashMap<>();
        if (groupsNodeList != null) {
            for (int i = 0; i < groupsNodeList.getLength(); i++) {
                Element grp = (Element) groupsNodeList.item(i);
                groupsMap.put(grp.getAttribute("name"), grp);
            }
        }

        traverseProcess(sb, nodeName, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groupsMap, callChainObj, transitionLabels, null);
    }
    
    // New Signature
    private void traverseProcess(StringBuilder sb, String nodeName, Map<String, Element> activityMap, 
                                 Map<String, List<String>> transitions, Set<String> processed, 
                                 File projectRoot, Set<String> visited, Map<String, Boolean> relevanceCache, 
                                 int depth, Map<String, Element> groups, CallChain callChain,
                                 Map<String, Map<String, String>> transitionLabels, String stopAtNode) {
        if (nodeName == null) return;
        if (stopAtNode != null && stopAtNode.equals(nodeName)) {
            return;
        }
        if (processed.contains(nodeName)) return;
        processed.add(nodeName);
        Element activity = activityMap.get(nodeName);
        if (activity == null) {
            logger.debug("[TRAVERSE] Node '{}' not found in activityMap, checking successors", nodeName);
            List<String> successors = transitions.get(nodeName);
            if (successors != null) {
                for (String successor : successors) {
                    if (transitionLabels.containsKey(nodeName) && transitionLabels.get(nodeName).containsKey(successor)) {
                         String label = transitionLabels.get(nodeName).get(successor).replace("\"", "\\\"");
                         sb.append("-> \"").append(label).append("\";\n");
                    }
                    traverseProcess(sb, successor, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups, callChain, transitionLabels, null);
                }
            }
            return;
        }
        String type = getTagValue(activity, "pd:type");
        if (type != null && (type.contains("LoopGroup") || type.contains("CriticalSectionGroup") || type.contains("CatchActivity") || type.contains("TransactionGroup"))) {
            // Extract group type from config
            String groupType = extractGroupType(activity);
            
            // Build partition label with group type if available
            String partitionLabel = nodeName;
            if (groupType != null) {
                partitionLabel = nodeName + " (" + groupType + ")";
            }
            
            sb.append("partition \"").append(partitionLabel).append("\" {\n");
            
            // Render ALL activities within the group (not just connectors) to match TIBCO IDE
            NodeList groupActivities = activity.getElementsByTagNameNS("*", "activity");
            if (groupActivities != null && groupActivities.getLength() > 0) {
                // Find start nodes within the group (nodes with no incoming transitions FROM within the group)
                Set<String> internalNodes = new HashSet<>();
                for (int i = 0; i < groupActivities.getLength(); i++) {
                    internalNodes.add(((Element) groupActivities.item(i)).getAttribute("name"));
                }
                
                Set<String> startNodes = new HashSet<>(internalNodes);
                for (String internalNode : internalNodes) {
                    List<String> successors = transitions.get(internalNode);
                    if (successors != null) {
                        for (String succ : successors) {
                             startNodes.remove(succ); // If it's a target, it's not a start
                        }
                    }
                }
                
                // If circular or all connected, fallback to first
                if (startNodes.isEmpty() && !internalNodes.isEmpty()) {
                     startNodes.add(internalNodes.iterator().next());
                }
                
                // Traverse from start nodes. 
                // IMPORTANT: We must ensure we don't accidentally traverse OUT of the group and render the whole rest of the graph inside the partition.
                // However, traverseProcess naturally follows transitions. If a transition leads out, it leads out.
                // But typically TIBCO groups merge back to a single exit point or end.
                // For valid rendering, we just start traversal. The partition bracket } closes the visual scope.
                
                // Use a separate visited/processed set for this inner traversal to avoid conflict? 
                // No, we want to mark them as processed so the main loop doesn't re-render them?
                // Actually, the main loop calls traverseProcess on the GROUP node. 
                // Then the GROUP node renders its children.
                // The children's successors might be outside. 
                // If we traverse, we might render the "End" node inside the partition if we are not careful.
                // But standard TIBCO groups usually join back.
                
                // Let's rely on standard traversal but we might need to stop if we exit the group?
                // For now, let's trust the topology. Most groups end at a join or end of process.
                
                if (startNodes.size() > 1) {
                    sb.append("fork\n");
                    int i = 0;
                    for (String startNode : startNodes) {
                        if (i > 0) sb.append("fork again\n");
                        // We do not have transition labels for implicit starts, usually
                         traverseProcess(sb, startNode, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups, callChain, transitionLabels, null);
                         i++;
                    }
                    sb.append("end fork\n");
                } else {
                    for (String startNode : startNodes) {
                         traverseProcess(sb, startNode, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups, callChain, transitionLabels, null);
                    }
                }
            }
            sb.append("}\n");
            List<String> successors = transitions.get(nodeName);
            if (successors != null) {
                if (successors.size() > 1) {
                    // Fork logic for multiple successors
                    sb.append("fork\n");
                    for (int i = 0; i < successors.size(); i++) {
                        String successor = successors.get(i);
                        if (i > 0) sb.append("fork again\n");
                        
                        // Add label if present
                        if (transitionLabels.containsKey(nodeName) && transitionLabels.get(nodeName).containsKey(successor)) {
                             String label = transitionLabels.get(nodeName).get(successor).replace("\"", "\\\"");
                             sb.append("-> \"").append(label).append("\";\n");
                        }
                        
                        // For subsequent branches, we need to be careful with 'processed' set
                        // We pass a copy for each branch so they don't block each other, 
                        // but we need to merge back to avoid infinite loops or re-processing shared merge points unnecessarily.
                        // However, simplistic branching often works best with independent checks or strictly preventing loops.
                        // Let's use the main set but be aware. Actually, simpler:
                        traverseProcess(sb, successor, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups, callChain, transitionLabels, null);
                    }
                    sb.append("end fork\n");
                } else {
                    for (String successor : successors) {
                        if (transitionLabels.containsKey(nodeName) && transitionLabels.get(nodeName).containsKey(successor)) {
                             String label = transitionLabels.get(nodeName).get(successor).replace("\"", "\\\"");
                             sb.append("-> \"").append(label).append("\";\n");
                        }
                        traverseProcess(sb, successor, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups, callChain, transitionLabels, null);
                    }
                }
            }
            return;
        }
        // Check if this is a connector activity OR a subprocess call
        boolean isConnectorActivity = type != null && isConnector(type);
        boolean isSubprocessCall = type != null && type.contains("CallProcessActivity");
        boolean isRelevant = isConnectorActivity || isSubprocessCall;
        
        logger.debug("[TRAVERSE] Activity '{}' type='{}' isConnector={} isSubproc={} isRelevant={}", 
            nodeName, type, isConnectorActivity, isSubprocessCall, isRelevant);
        
        if (isRelevant) {
            if (type.contains("CallProcessActivity")) {
                String subProcPath = getConfigValue(activity, "processName");
                String dynamicOverride = getDynamicOverride(activity);
                String spawnConfig = getConfigValue(activity, "spawn");
                boolean isSpawn = "true".equalsIgnoreCase(spawnConfig);
                if (dynamicOverride != null && !dynamicOverride.trim().isEmpty()) {
                    List<String> processPaths = extractProcessPathsFromXPath(dynamicOverride);
                    // Deduplicate paths while preserving order
                    Set<String> uniquePaths = new LinkedHashSet<>(processPaths);
                    for (String procPath : uniquePaths) {
                        String cleanPath = procPath.trim();
                        if (cleanPath.startsWith("/")) cleanPath = cleanPath.substring(1);
                        
                        // Use the full relative path for the label, not just the process name
                        String displayPath = "/" + cleanPath;
                        if (displayPath.endsWith(".process")) {
                            displayPath = displayPath.substring(0, displayPath.length() - 8);
                        }
                        
                        File subFile = new File(projectRoot, cleanPath);
                        if (subFile.exists()) {
                            // Check for circular reference
                            if (callChain.contains(subFile.getAbsolutePath())) {
                                sb.append(":⟲ ").append(callChain.getRecursionMessage(subFile.getAbsolutePath())).append(";\n");
                            } else if (!visited.contains(subFile.getAbsolutePath())) {
                                Set<String> nextVisited = new HashSet<>(visited);
                                nextVisited.add(subFile.getAbsolutePath());
                                
                                String labelPrefix = isSpawn ? "🔄⚡ Override (Spawn): " : "🔄 Override: ";
                                String partitionColor = isSpawn ? "#FFF8E1" : "#E3F2FD"; // Light yellow-ish for spawn mix, else light blue
                                
                                sb.append("partition \"").append(labelPrefix).append(displayPath).append("\" ").append(partitionColor).append(" {\n");
                                try {
                                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                                    dbf.setNamespaceAware(true);
                                    Document subDoc = dbf.newDocumentBuilder().parse(subFile);
                                    CallChain nextChain = callChain.push(subFile.getAbsolutePath());
                                    generateRecursiveActivityPuml(sb, subDoc, subFile, projectRoot, nextVisited, relevanceCache, depth + 1, null, nextChain);
                                } catch (Exception e) {
                                    sb.append(":Error loading subprocess;\n");
                                }
                                sb.append("}\n");
                            }
                        }
                    }
                } else if (isSpawn && subProcPath != null) {
                    if (subProcPath.startsWith("/")) subProcPath = subProcPath.substring(1);
                    File subFile = new File(projectRoot, subProcPath);
                    
                    // Use full relative path for display
                    String displayPath = "/" + subProcPath;
                    if (displayPath.endsWith(".process")) {
                        displayPath = displayPath.substring(0, displayPath.length() - 8);
                    }
                    
                    if (subFile.exists()) {
                        // Check for circular reference
                        if (callChain.contains(subFile.getAbsolutePath())) {
                            sb.append(":⟲ ").append(callChain.getRecursionMessage(subFile.getAbsolutePath())).append(";\\n");
                        } else if (!visited.contains(subFile.getAbsolutePath())) {
                            Set<String> nextVisited = new HashSet<>(visited);
                            nextVisited.add(subFile.getAbsolutePath());
                            sb.append("partition \"⚡ Spawn: ").append(displayPath).append("\" #FFEBCD {\n");
                            try {
                                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                                dbf.setNamespaceAware(true);
                                Document subDoc = dbf.newDocumentBuilder().parse(subFile);
                                CallChain nextChain = callChain.push(subFile.getAbsolutePath());
                                generateRecursiveActivityPuml(sb, subDoc, subFile, projectRoot, nextVisited, relevanceCache, depth + 1, null, nextChain);
                            } catch (Exception e) {
                                sb.append(":Error loading subprocess;\n");
                            }
                            sb.append("}\n");
                        }
                    }
                } else if (subProcPath != null) {
                    if (subProcPath.startsWith("/")) subProcPath = subProcPath.substring(1);
                    File subFile = new File(projectRoot, subProcPath);
                    
                    // Use full relative path for display
                    String displayPath = "/" + subProcPath;
                    if (displayPath.endsWith(".process")) {
                        displayPath = displayPath.substring(0, displayPath.length() - 8);
                    }
                    
                    if (subFile.exists()) {
                        // Check for circular reference
                        if (callChain.contains(subFile.getAbsolutePath())) {
                            sb.append(":⟲ ").append(callChain.getRecursionMessage(subFile.getAbsolutePath())).append(";\\n");
                        } else if (!visited.contains(subFile.getAbsolutePath())) {
                            Set<String> nextVisited = new HashSet<>(visited);
                            nextVisited.add(subFile.getAbsolutePath());
                            sb.append("partition \"📞 CallProcess: ").append(displayPath).append("\" {\n");
                            try {
                                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                                dbf.setNamespaceAware(true);
                                Document subDoc = dbf.newDocumentBuilder().parse(subFile);
                                CallChain nextChain = callChain.push(subFile.getAbsolutePath());
                                generateRecursiveActivityPuml(sb, subDoc, subFile, projectRoot, nextVisited, relevanceCache, depth + 1, null, nextChain);
                            } catch (Exception e) {
                                sb.append(":Error loading subprocess;\n");
                            }
                            sb.append("}\n");
                        }
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
        if (successors != null) {
            List<String> branchesToRender = new ArrayList<>();
            for (String successor : successors) {
                // Check if this branch leads to anything relevant
                // We use a fresh visited set for the check to avoid side effects
                if (hasRelevantContentInPath(successor, activityMap, transitions, projectRoot, relevanceCache, new HashSet<>())) {
                    branchesToRender.add(successor);
                }
            }
            
            logger.debug("[INTEGRATION] Node '{}' has {} total successors, {} with relevant content", nodeName, successors.size(), branchesToRender.size());

            if (branchesToRender.size() > 1) {
                // Determine Common Join Node to structure the graph as Fork/Merge blocks
                String joinNode = findCommonJoinNode(branchesToRender, transitions);
                
                sb.append("fork\n");
                for (int i = 0; i < branchesToRender.size(); i++) {
                    String successor = branchesToRender.get(i);
                    if (i > 0) sb.append("fork again\n");
                    
                    // Add transition label
                    logger.debug("[INTEGRATION] Rendering fork branch {} of {}: '{}'", i+1, branchesToRender.size(), successor);
                    if (transitionLabels.containsKey(nodeName) && transitionLabels.get(nodeName).containsKey(successor)) {
                        String label = transitionLabels.get(nodeName).get(successor).replace("\"", "\\\"");
                        logger.debug("[INTEGRATION] Adding label '{}' to branch '{}'", label, successor);
                        sb.append("-> \"").append(label).append("\";\n");
                    } else {
                        sb.append("->\n");  // Still need arrow even without label!
                    }
                    
                    traverseProcess(sb, successor, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups, callChain, transitionLabels, joinNode);
                }
                sb.append("end fork\n");
                
                if (joinNode != null) {
                     traverseProcess(sb, joinNode, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups, callChain, transitionLabels, stopAtNode);
                }
            } else if (branchesToRender.size() == 1) {
                String successor = branchesToRender.get(0);
                if (transitionLabels.containsKey(nodeName) && transitionLabels.get(nodeName).containsKey(successor)) {
                     String label = transitionLabels.get(nodeName).get(successor).replace("\"", "\\\"");
                     sb.append("-> \"").append(label).append("\";\n");
                }
                traverseProcess(sb, successor, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups, callChain, transitionLabels, stopAtNode);
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
        if (type.contains("EndActivity")) return true;
        if (type.contains("RestAdapterActivity")) return true;
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
    public String generateFlowPuml(Document doc, String processName) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("' Generated: ").append(System.currentTimeMillis()).append("\n");
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
        // !pragma maxwidth removed to allow full resolution logic
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
             traverseFlowFromNode(sb, startName, activityMap, groupMap, transitions, transitionLabels, transitionConditions, new HashSet<>(), doc, null, false, null, false); // insideFork=false (top level)
             sb.append("stop\n");
             if (showExceptionHandlers && !catchActivities.isEmpty()) {
                 sb.append("partition \"Error Handling\" #FFEBEE {\n");
                 for (String catchName : catchActivities) {
                      sb.append("\n");
                      sb.append("start\n");
                      traverseFlowFromNode(sb, catchName, activityMap, groupMap, transitions, transitionLabels, transitionConditions, new HashSet<>(), doc, null, false, null, false); // insideFork=false
                      sb.append("stop\n");
                 }
                 sb.append("}\n");
             }
        } catch (Exception e) {
            logger.error("Error generating flow diagram PUML", e);
            sb.append(":Error generating diagram;\n");
        }
        sb.append("@enduml");
        String puml = sb.toString();
        try {
            String filename = "flow_" + processName.replaceAll("[^a-zA-Z0-9]", "_") + ".puml";
            java.nio.file.Files.writeString(java.nio.file.Paths.get(filename), puml);
            logger.info("[FLOW DIAGRAM] Written to {}", filename);
        } catch (Exception ex) {
            logger.error("Failed to write debug PUML", ex);
        }
        return puml;
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
                                      boolean skipFirstNodeRender,
                                      Set<String> allowedScope,
                                      boolean insideFork) {
        if (nodeName == null) return;
        
        logger.debug("[FLOW TRAVERSAL] Visiting node: '{}', stopAtNode: {}, allowedScope: {}", nodeName, stopAtNode, allowedScope != null ? allowedScope.size() : "null");

        if (allowedScope != null && !allowedScope.contains(nodeName)) {
        	logger.debug("[FLOW TRAVERSAL] Node '{}' is outside allowed scope. Stopping.", nodeName);
        	return;
        }
        if (stopAtNode != null && stopAtNode.equals(nodeName)) {
            logger.debug("[FLOW TRAVERSAL] Reached stop node '{}'. Stopping.", nodeName);
            return;
        }
        if (processed.contains(nodeName)) {
        	logger.debug("[FLOW TRAVERSAL] Node '{}' already processed. Skipping.", nodeName);
        	return;
        }
        processed.add(nodeName);
        
        // Get successors first
        List<String> successors = transitions.get(nodeName);
        
        // Check if this is a Null activity
        boolean isNullActivity = false;
        boolean nullFork = false;
        Element currentActivity = activityMap.get(nodeName);
        if (currentActivity != null) {
            String type = getTagValue(currentActivity, "pd:type");
            if (type != null && type.contains("NullActivity")) {
                isNullActivity = true;
                nullFork = successors != null && successors.size() > 1;
            }
        }
        
        // Render the current node (unless it's a starter or we're told to skip)
        if (nullFork) {
            sb.append(":").append(nodeName).append(";\n");
        } else if (!skipFirstNodeRender) {
            if (groupMap.containsKey(nodeName)) {
                // Groups inside forks cannot use partition wrapper (PlantUML limitation)
                renderGroup(sb, groupMap.get(nodeName), doc, insideFork); // Not inside fork at top level
            } else if (activityMap.containsKey(nodeName)) {
            renderActivity(sb, activityMap.get(nodeName));
        }
        }
        
        // Handle successors
        if (successors != null && !successors.isEmpty()) {
            if (successors.size() > 1) {
                // ALWAYS use fork for multiple successors (parallel execution)
                String joinNode = findCommonJoinNode(successors, transitions);
                logger.debug("[FLOW FORK] Rendering fork from '{}' with {} branches", nodeName, successors.size());
                sb.append("fork\n");
                List<Set<String>> branchProcessedSets = new ArrayList<>();
                for (int i = 0; i < successors.size(); i++) {
                    if (i > 0) sb.append("fork again\n");
                    String successor = successors.get(i);
                    logger.debug("[FLOW FORK] Branch {} -> '{}'", i+1, successor);
                    
                    // Check scope BEFORE rendering branch transition
                     if (allowedScope != null && !allowedScope.contains(successor)) {
                        // Skip this branch if it goes out of scope? 
                        // Or render it as an end point?
                        // If we skip, the fork might be empty?
                        sb.append("stop\n"); // Terminate this fork leg gracefully
                        continue;
                    }

                    // Add transition label if available
                    if (transitionLabels != null && transitionLabels.containsKey(nodeName)) {
                        String label = transitionLabels.get(nodeName).get(successor);
                        if (label != null) {
                            label = label.replace("\"", "\\\"");
                            sb.append("-[#483D8B]->[").append(label).append("]\n");
                        } else {
                            sb.append("->\n"); 
                        }
                    } else {
                        sb.append("->\n"); 
                    }
                    
                    // Use separate processed set for each fork branch to allow duplicates
                    Set<String> branchProcessed = new HashSet<>(processed);
                    traverseFlowFromNode(sb, successor, activityMap, groupMap, transitions, transitionLabels, transitionConditions, branchProcessed, doc, joinNode, false, allowedScope, true); // insideFork=true
                    branchProcessedSets.add(branchProcessed);
                }
                sb.append("end fork\n");
                
                // Merge all branch processed sets AFTER all branches are rendered
                for (Set<String> branchProcessed : branchProcessedSets) {
                    processed.addAll(branchProcessed);
                }
                
                if (joinNode != null) {
                    // Fix: Ensure there is an arrow connecting the merge bar to the join node
                    sb.append("->\n");
                    
                    boolean isJoinNull = false;
                    Element joinEl = activityMap.get(joinNode);
                    if (joinEl != null) {
                         String t = getTagValue(joinEl, "pd:type");
                         if (t != null && t.contains("NullActivity")) isJoinNull = true;
                    }
                    traverseFlowFromNode(sb, joinNode, activityMap, groupMap, transitions, transitionLabels, transitionConditions, processed, doc, stopAtNode, isJoinNull, allowedScope, insideFork);
                }
            } else {
                // Single successor
                String successor = successors.get(0);
                
                // Avoid dangling arrows pointing to nodes outside the group/scope
                if (allowedScope != null && !allowedScope.contains(successor)) {
                	logger.debug("[FLOW TRAVERSAL] Stopping traversal from '{}' to '{}' because target is NOT in allowed scope: {}", nodeName, successor, allowedScope);
                    return; 
                }
                
                // Fix: Do not append arrow if we are about to stop at the join node
                boolean isJoinNode = (stopAtNode != null && successor.equals(stopAtNode));
                
                boolean successorExists = activityMap.containsKey(successor) || groupMap.containsKey(successor);
                if (successorExists && transitionLabels != null && transitionLabels.containsKey(nodeName)) {
                    String label = transitionLabels.get(nodeName).get(successor);
                    if (label != null) {
                        label = label.replace("\"", "\\\"");
                        sb.append("-[#483D8B]->[").append(label).append("]\n");
                    } else if (!isJoinNode) {
                         sb.append("->\n");
                    }
                } else if (!isJoinNode) {
                    sb.append("->\n");
                }
                traverseFlowFromNode(sb, successor, activityMap, groupMap, transitions, transitionLabels, transitionConditions, processed, doc, stopAtNode, false, allowedScope, insideFork);
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
    private void renderGroup(StringBuilder sb, Element group, Document doc, boolean insideFork) {
        String groupName = group.getAttribute("name");
        
        // Parse content into local maps for traversal
        Map<String, Element> localActivityMap = new HashMap<>();
        Map<String, Element> localGroupMap = new HashMap<>();
        Map<String, List<String>> localTransitions = new HashMap<>();
        Map<String, Map<String, String>> localLabels = new HashMap<>();
        Map<String, Map<String, String>> localConditions = new HashMap<>();
        Set<String> scope = new HashSet<>();

        // Activities
        NodeList groupActivities = group.getElementsByTagNameNS("*", "activity");
        for (int i = 0; i < groupActivities.getLength(); i++) {
            Element act = (Element) groupActivities.item(i);
            Node parent = act.getParentNode();
            boolean isChild = parent instanceof Element && ((Element)parent).getAttribute("name").equals(groupName);
            if (isChild) {
                String name = act.getAttribute("name");
                localActivityMap.put(name, act);
                scope.add(name);
            }
        }

        // Nested Groups
        NodeList nestedGroups = group.getElementsByTagNameNS("*", "group");
        for (int i = 0; i < nestedGroups.getLength(); i++) {
            Element subGroup = (Element) nestedGroups.item(i);
            Node parent = subGroup.getParentNode();
            boolean isChild = parent instanceof Element && ((Element)parent).getAttribute("name").equals(groupName);
            if (isChild) {
                String name = subGroup.getAttribute("name");
                localGroupMap.put(name, subGroup);
                scope.add(name);
            }
        }

        // Transitions
        NodeList transitions = group.getElementsByTagNameNS("*", "transition");
        for (int i = 0; i < transitions.getLength(); i++) {
            Element t = (Element) transitions.item(i);
            Node parent = t.getParentNode();
            boolean isChild = parent instanceof Element && ((Element)parent).getAttribute("name").equals(groupName);
            if (isChild) {
                String from = getTagValue(t, "pd:from");
                String to = getTagValue(t, "pd:to");
                
                localTransitions.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
                
                String label = "";
                String xpathDesc = getTagValue(t, "pd:xpathDescription");
                String cType = getTagValue(t, "pd:conditionType");
                
                if (xpathDesc != null && !xpathDesc.trim().isEmpty()) label = xpathDesc.trim();
                else if ("error".equalsIgnoreCase(cType)) label = "Error";
                else if ("otherwise".equalsIgnoreCase(cType)) label = "Otherwise";
                else if (getTagValue(t, "pd:xpath") != null) label = getTagValue(t, "pd:xpath").trim();

                if (!label.isEmpty()) {
                    localLabels.computeIfAbsent(from, k -> new HashMap<>()).put(to, label);
                }
            }
        }
        // --- Deep Traversal Logic ---
        
        Set<String> processed = new HashSet<>();

        // Find Implicit Start Node (node with no incoming transitions inside scope)
        Set<String> internalTargets = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : localTransitions.entrySet()) {
            // Only consider transitions where the source is in the scope (ignore 'start' token for this calculation)
            if (scope.contains(entry.getKey())) {
                internalTargets.addAll(entry.getValue());
            }
        }
        
        String startNode = null;
        for (String name : scope) {
            if (!internalTargets.contains(name)) {
                startNode = name;
                break;
            }
        }
        
        // Fallback for cycles
        if (startNode == null && !scope.isEmpty()) {
            startNode = scope.iterator().next();
        }

        // 1. Identify Entry Points from 'start'
        List<String> startTargets = localTransitions.getOrDefault("start", new ArrayList<>());
        
        // If no explicit start-to-node transitions, use the existing 'startNode' fallback (first node in scope)
        if (startTargets.isEmpty() && startNode != null) {
            startTargets.add(startNode);
        }

        // 2. Render Main Flow (handling Fork from Start)
        
        // 2. Render Main Flow (handling Fork from Start)
        // Flow Diagrams: Use same structure as Integration diagrams
        // partition contains fork (not fork contains partition)
        // BUT: if this group is being rendered inside a fork branch, skip partition wrapper
        if (!insideFork) {
            String groupType = group.getElementsByTagNameNS("*", "groupType").getLength() > 0 
                ? group.getElementsByTagNameNS("*", "groupType").item(0).getTextContent() 
                : null;
            String partitionLabel = groupName;
            if (groupType != null && !groupType.isEmpty()) {
                partitionLabel = groupName + " (" + groupType + ")";
            }
            sb.append("partition \"").append(partitionLabel).append("\" {\n");
        }
        // When insideFork=true, no wrapper or label - just render content directly        
        if (startTargets.size() > 1) {
            // Parallel Start -> Explicit Fork
            logger.debug("[GROUP] Parallel start detected in group '{}': {}", groupName, startTargets);
            sb.append("fork\n");
            
            int i = 0;
            for (String target : startTargets) {
                 if (i > 0) sb.append("fork again\n");
                 
                  // We need to render the transition label if it exists
                  String label = localLabels.getOrDefault("start", new HashMap<>()).get(target);
                  if (label != null && !label.isEmpty()) {
                      label = label.replace("\"", "\\\""); 
                      sb.append("-[#483D8B]->[").append(label).append("]\n");
                  } else {
                      sb.append("->\n");   
                  }
                  
                  traverseFlowFromNode(sb, target, localActivityMap, localGroupMap, localTransitions, localLabels, localConditions, processed, doc, null, false, scope, false); // insideFork=false (group internal)
                  i++;
             }
             sb.append("end fork\n");
         } else if (startTargets.size() == 1) {
              // Single Start
              String target = startTargets.get(0);
              String label = localLabels.getOrDefault("start", new HashMap<>()).get(target);
              if (label != null && !label.isEmpty()) {
                  label = label.replace("\"", "\\\"");
                  sb.append("-[#483D8B]->[").append(label).append("]\n");
              } else {
                  sb.append("->\n");
              }
              traverseFlowFromNode(sb, target, localActivityMap, localGroupMap, localTransitions, localLabels, localConditions, processed, doc, null, false, scope, false); // insideFork=false (group internal)
         }
         
         if (!insideFork) {
             sb.append("}\n");
         }
         

        // 3. Island Detection (Dead Nodes) - Coverage Guarantee
        // Identify any nodes in 'scope' that were NOT added to 'processed'
        Set<String> unvisited = new HashSet<>(scope);
        unvisited.removeAll(processed);
        
        if (!unvisited.isEmpty()) {
            logger.debug("[GROUP] Detected {} unvisited (island) nodes in group '{}': {}", unvisited.size(), groupName, unvisited);
            
            // Allow PlantUML to place these "floating". Or force them into a package.
            sb.append("package \"Disconnected / Unreachable\" {\n");
             
            for (String node : unvisited) {
                if (processed.contains(node)) continue; // Already picked up by a previous island iteration
                
                 boolean hasIncoming = false;
                 for (Map.Entry<String, List<String>> entry : localTransitions.entrySet()) {
                     if (unvisited.contains(entry.getKey()) && entry.getValue().contains(node)) {
                         hasIncoming = true;
                         break;
                     }
                 }
                 
                 if (!hasIncoming) {
                     traverseFlowFromNode(sb, node, localActivityMap, localGroupMap, localTransitions, localLabels, localConditions, processed, doc, null, false, scope, false); // insideFork=false (island)
                 }
            }
            
            // Sweeper: If cycles exist in islands, the above might miss them.
            for (String node : unvisited) {
                if (!processed.contains(node)) {
                     traverseFlowFromNode(sb, node, localActivityMap, localGroupMap, localTransitions, localLabels, localConditions, processed, doc, null, false, scope, false); // insideFork=false (island)
                }
            }
            
            sb.append("}\n");
        }
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
        
        logger.debug("[isConnector] Checking type: {}", type);
        
        for (String excludePattern : excludePatterns) {
            String trimmed = excludePattern.toLowerCase().trim();
            if (!trimmed.isEmpty() && t.contains(trimmed)) {
                logger.debug("[isConnector]   EXCLUDED by pattern '{}'", excludePattern);
                return false;
            }
        }
        for (String pattern : connectorPatterns) {
            String trimmed = pattern.toLowerCase().trim();
            if (!trimmed.isEmpty() && t.contains(trimmed)) {
                logger.debug("[isConnector]   MATCHED pattern '{}' -> TRUE", pattern);
                return true;
            }
        }
        logger.debug("[isConnector]   NO MATCH -> FALSE");
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
    
    /**
     * Extract the group type from a group element's config section.
     * Returns the groupType value (e.g., "transactionGroup", "inputLoop") or null if not found.
     */
    private String extractGroupType(Element groupElement) {
        Element config = (Element) groupElement.getElementsByTagName("config").item(0);
        if (config == null) {
            config = (Element) groupElement.getElementsByTagNameNS("*", "config").item(0);
        }
        if (config != null) {
            String groupType = getTagValue(config, "pd:groupType");
            if (groupType != null && !groupType.trim().isEmpty()) {
                return groupType.trim();
            }
        }
        return null;
    }
    
    /**
     * Recursively collect all activities reachable from a starting node
     */
    private void collectReachableActivities(String nodeName, Map<String, Element> activityMap, 
                                           Map<String, List<String>> transitions, 
                                           Set<String> reachable, Set<String> visited) {
        if (nodeName == null || visited.contains(nodeName)) return;
        visited.add(nodeName);
        
        Element activity = activityMap.get(nodeName);
        if (activity != null) {
            reachable.add(nodeName);
        }
        
        List<String> successors = transitions.get(nodeName);
        if (successors != null) {
            for (String successor : successors) {
                collectReachableActivities(successor, activityMap, transitions, reachable, visited);
            }
        }
    }
}
