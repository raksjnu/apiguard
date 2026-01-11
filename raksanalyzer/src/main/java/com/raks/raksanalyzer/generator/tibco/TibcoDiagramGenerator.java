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
                logger.debug("[FLOW PNG] Successfully rendered PNG for '{}', size: {} bytes", processFile.getName(), pngBytes.length);
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
            File maybeAbsolute = new File(startProcessPath);
            File processFile;

            if (maybeAbsolute.isAbsolute() && maybeAbsolute.exists()) {
                processFile = maybeAbsolute;
            } else {
                if (startProcessPath.startsWith("/")) {
                    startProcessPath = startProcessPath.substring(1);
                }
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
        Map<String, Element> activityMap = new LinkedHashMap<>();
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
        Map<String, List<String>> transitions = new LinkedHashMap<>();
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

            String from = getTagValue(t, "pd:from");
            String to = getTagValue(t, "pd:to");
            transitions.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
        }
        

        Map<String, Map<String, String>> transitionLabels = new LinkedHashMap<>();
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


                if (xpathDesc != null && !xpathDesc.trim().isEmpty()) {
                    label = xpathDesc.trim();
                }


                if (label == null) {
                     if ("xpth".equalsIgnoreCase(conditionType) || "xpath".equalsIgnoreCase(conditionType)) {
                         label = (xpathVal != null && !xpathVal.trim().isEmpty()) ? xpathVal.trim() : "XPath";
                         
                         if (label.equals(xpathVal != null ? xpathVal.trim() : "")) {
                             xpathVal = null; 
                         }
                     } else if ("otherwise".equalsIgnoreCase(conditionType)) {
                         label = "Otherwise";
                     } else if ("error".equalsIgnoreCase(conditionType)) {
                         label = "Error";
                     }
                }


                if (xpathVal != null && !xpathVal.trim().isEmpty()) {

                    if (label != null && !label.equals(xpathVal.trim())) {
                        label = label + "\\n" + xpathVal.trim();
                    } else if (label == null) {
                        label = xpathVal.trim();
                    }
                }
                
                if (label != null) {
                    transitionLabels.computeIfAbsent(from, k -> new HashMap<>()).put(to, label);

                }
            }
            logger.debug("[LABEL EXTRACTION] Total transition labels extracted: {}", transitionLabels.size());
        }
        if (depth == 0 && starterElement != null) {
            String type = getTagValue(starterElement, "pd:type");
            String resourceType = getTagValue(starterElement, "pd:resourceType");
            String cleanType = getReadableTypeLabel(resourceType);
            String icon = getConnectorIcon(type);
            String name = starterElement.getAttribute("name");
            sb.append(":").append(icon).append(" **").append(name).append("**\\n").append(cleanType).append(";\n");
        }
        Set<String> processed = new LinkedHashSet<>();
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
                logger.debug("[INTEGRATION FORK] Starter '{}' has {} total successors, {} with relevant content", startNode, starterSuccessors.size(), branchesWithContent.size());
                if (branchesWithContent.size() > 1) {
                    sb.append("fork\n");
                    List<Set<String>> branchProcessedSets = new ArrayList<>();
                    for (int i = 0; i < branchesWithContent.size(); i++) {
                        if (i > 0) {
                            sb.append("fork again\n");
                        }
                        

                        String successor = branchesWithContent.get(i);
                        logger.debug("[INTEGRATION FORK] Rendering branch {} of {}: '{}'", i+1, branchesWithContent.size(), successor);
                        if (transitionLabels.containsKey(startNode) && transitionLabels.get(startNode).containsKey(successor)) {
                             String label = transitionLabels.get(startNode).get(successor).replace("\"", "\\\"");
                             logger.debug("[INTEGRATION FORK] Adding label '{}' to branch '{}'", label, successor);
                             sb.append("-> \"").append(label).append("\";\n");
                        } else {
                             logger.debug("[INTEGRATION FORK] No label for branch '{}'", successor);
                             sb.append("->\n");
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

                Element catchAct = activityMap.get(catchName);
                if (catchAct != null) {

                    String type = getTagValue(catchAct, "pd:type");
                    String resourceType = getTagValue(catchAct, "pd:resourceType");
                    String readableType = getReadableTypeLabel(resourceType);
                    String icon = getConnectorIcon(type);
                    sb.append(":").append(icon).append(" **").append(catchName).append("**\\n").append(readableType).append(";\n");
                    processed.add(catchName);
                }
                traverseProcess(sb, catchName, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups, callChain, transitionLabels);
            }
            sb.append("}\n");
        }
    }


    private void traverseProcess(StringBuilder sb, String nodeName, Map<String, Element> activityMap, 
                                 Map<String, List<String>> transitions, Set<String> processed, 
                                 File projectRoot, Set<String> visited, Map<String, Boolean> relevanceCache, 
                                 int depth, NodeList groupsNodeList, CallChain callChainObj,
                                 Map<String, Map<String, String>> transitionLabels) {

        Map<String, Element> groupsMap = new LinkedHashMap<>();
        if (groupsNodeList != null) {
            for (int i = 0; i < groupsNodeList.getLength(); i++) {
                Element grp = (Element) groupsNodeList.item(i);
                groupsMap.put(grp.getAttribute("name"), grp);
            }
        }

        traverseProcess(sb, nodeName, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groupsMap, callChainObj, transitionLabels, null);
    }
    

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
                          sb.append("-[#483D8B]->[").append(label).append("]\n");
                     }
                    traverseProcess(sb, successor, activityMap, transitions, processed, projectRoot, visited, relevanceCache, depth, groups, callChain, transitionLabels, null);
                }
            }
            return;
        }

        String type = getTagValue(activity, "pd:type");
        logger.debug("[SECTION 2 TRAVERSE] Processing node '{}' (type: {})", nodeName, type);
        if (type != null && (type.contains("LoopGroup") || type.contains("CriticalSectionGroup") || type.contains("CatchActivity") || type.contains("TransactionGroup"))) {

            String groupType = extractGroupType(activity);
            

            String partitionLabel = nodeName;
            if (groupType != null) {
                partitionLabel = nodeName + " (" + groupType + ")";
            }
            
            logger.debug("[SECTION 2 GROUP] Rendering partition for group '{}' (type: {})", nodeName, groupType);
            int beforeLen = sb.length();
            sb.append("partition \"").append(partitionLabel).append("\" {\n");

            

            NodeList groupActivities = activity.getElementsByTagNameNS("*", "activity");
            if (groupActivities != null && groupActivities.getLength() > 0) {

                Set<String> internalNodes = new HashSet<>();
                for (int i = 0; i < groupActivities.getLength(); i++) {
                    internalNodes.add(((Element) groupActivities.item(i)).getAttribute("name"));
                }
                
                Set<String> startNodes = new HashSet<>(internalNodes);
                for (String internalNode : internalNodes) {
                    List<String> successors = transitions.get(internalNode);
                    if (successors != null) {
                        for (String succ : successors) {
                             startNodes.remove(succ);
                        }
                    }
                }
                

                if (startNodes.isEmpty() && !internalNodes.isEmpty()) {
                     startNodes.add(internalNodes.iterator().next());
                }
                

                
                if (startNodes.size() > 1) {
                    logger.debug("[SECTION 2 GROUP] Group '{}' has {} internal start nodes - rendering internal fork", nodeName, startNodes.size());
                    sb.append("fork\n");
                    int i = 0;
                    for (String startNode : startNodes) {
                        if (i > 0) sb.append("fork again\n");

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
            int beforeLen2 = sb.length();
            sb.append("}\n");
            logger.debug("[SECTION 2 PUML] Closed partition for '{}': {}", nodeName, sb.substring(beforeLen2, sb.length()));
            List<String> successors = transitions.get(nodeName);
            if (successors != null) {
                if (successors.size() > 1) {

                    sb.append("fork\n");
                    for (int i = 0; i < successors.size(); i++) {
                        String successor = successors.get(i);
                        if (i > 0) sb.append("fork again\n");
                        

                        if (transitionLabels.containsKey(nodeName) && transitionLabels.get(nodeName).containsKey(successor)) {
                             String label = transitionLabels.get(nodeName).get(successor).replace("\"", "\\\"");
                             sb.append("-> \"").append(label).append("\";\n");
                        }
                        

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

                    Set<String> uniquePaths = new LinkedHashSet<>(processPaths);
                    for (String procPath : uniquePaths) {
                        String cleanPath = procPath.trim();
                        if (cleanPath.startsWith("/")) cleanPath = cleanPath.substring(1);
                        

                        String displayPath = "/" + cleanPath;
                        if (displayPath.endsWith(".process")) {
                            displayPath = displayPath.substring(0, displayPath.length() - 8);
                        }
                        
                        File subFile = new File(projectRoot, cleanPath);
                        if (subFile.exists()) {

                            if (callChain.contains(subFile.getAbsolutePath())) {
                                sb.append(":⟲ ").append(callChain.getRecursionMessage(subFile.getAbsolutePath())).append(";\n");
                            } else if (!visited.contains(subFile.getAbsolutePath())) {
                                Set<String> nextVisited = new HashSet<>(visited);
                                nextVisited.add(subFile.getAbsolutePath());
                                
                                String labelPrefix = isSpawn ? "🔄⚡ Override (Spawn): " : "🔄 Override: ";
                                String partitionColor = isSpawn ? "#FFF8E1" : "#E3F2FD";
                                
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
                    

                    String displayPath = "/" + subProcPath;
                    if (displayPath.endsWith(".process")) {
                        displayPath = displayPath.substring(0, displayPath.length() - 8);
                    }
                    
                    if (subFile.exists()) {

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
                    

                    String displayPath = "/" + subProcPath;
                    if (displayPath.endsWith(".process")) {
                        displayPath = displayPath.substring(0, displayPath.length() - 8);
                    }
                    
                    if (subFile.exists()) {

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

                if (hasRelevantContentInPath(successor, activityMap, transitions, projectRoot, relevanceCache, new HashSet<>())) {
                    branchesToRender.add(successor);
                }
            }
            
            logger.debug("[INTEGRATION] Node '{}' has {} total successors, {} with relevant content", nodeName, successors.size(), branchesToRender.size());

            if (branchesToRender.size() > 1) {

                String joinNode = findCommonJoinNode(branchesToRender, transitions);
                
                sb.append("fork\n");
                for (int i = 0; i < branchesToRender.size(); i++) {
                    String successor = branchesToRender.get(i);
                    if (i > 0) sb.append("fork again\n");
                    

                    logger.debug("[INTEGRATION] Rendering fork branch {} of {}: '{}'", i+1, branchesToRender.size(), successor);
                    if (transitionLabels.containsKey(nodeName) && transitionLabels.get(nodeName).containsKey(successor)) {
                        String label = transitionLabels.get(nodeName).get(successor).replace("\"", "\\\"");
                        logger.debug("[INTEGRATION] Adding label '{}' to branch '{}'", label, successor);
                        sb.append("-> \"").append(label).append("\";\n");
                    } else {
                        sb.append("->\n");
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

        try {
            Element root = doc.getDocumentElement();
            String startName = getTagValue(root, "pd:startName");
            Map<String, Element> activityMap = new LinkedHashMap<>();
            Map<String, Element> groupMap = new LinkedHashMap<>();
            Map<String, List<String>> transitions = new LinkedHashMap<>();
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
            Map<String, Map<String, String>> transitionLabels = new LinkedHashMap<>();
            Map<String, Map<String, String>> transitionConditions = new LinkedHashMap<>();
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
             traverseFlowFromNode(sb, startName, activityMap, groupMap, transitions, transitionLabels, transitionConditions, new HashSet<>(), doc, null, false, null, false, null); 

             sb.append("stop\n");
             if (showExceptionHandlers && !catchActivities.isEmpty()) {
                 sb.append("partition \"Error Handling\" #FFEBEE {\n");
                 for (String catchName : catchActivities) {
                      sb.append("\n");
                      sb.append("start\n");
                       traverseFlowFromNode(sb, catchName, activityMap, groupMap, transitions, transitionLabels, transitionConditions, new HashSet<>(), doc, null, false, null, false, null); 

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
                                      boolean insideFork,
                                      String incomingLabel) {
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
        

        List<String> successors = transitions.get(nodeName);
        

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
        

        if (nullFork) {
            sb.append(":").append(nodeName).append(";\n");
        } else if (!skipFirstNodeRender) {
            if (groupMap.containsKey(nodeName)) {
                renderGroup(sb, groupMap.get(nodeName), doc, insideFork, incomingLabel); 

            } else if (activityMap.containsKey(nodeName)) {
            renderActivity(sb, activityMap.get(nodeName));
        }
        }
        

        if (successors != null && !successors.isEmpty()) {
            if (successors.size() > 1) {

                String joinNode = findCommonJoinNode(successors, transitions);
                logger.debug("[FLOW FORK] Rendering fork from '{}' with {} branches", nodeName, successors.size());
                sb.append("fork\n");
                List<Set<String>> branchProcessedSets = new ArrayList<>();
                for (int i = 0; i < successors.size(); i++) {
                    if (i > 0) sb.append("fork again\n");
                    String successor = successors.get(i);

                    

                     if (allowedScope != null && !allowedScope.contains(successor)) {
                        sb.append("stop\n"); 
                        continue;
                    }


                    String labelToPass = null;
                    if (transitionLabels != null && transitionLabels.containsKey(nodeName) && transitionLabels.get(nodeName).containsKey(successor)) {
                        String label = transitionLabels.get(nodeName).get(successor);
                        if (label != null) {
                            labelToPass = label.replace("\"", "\\\"");
                        }
                    } 
                    
                    if (!groupMap.containsKey(successor)) {
                         if (labelToPass != null) {
                             labelToPass = labelToPass.replace("\"", "\\\"");
                             sb.append("-> \"").append(labelToPass).append("\";\n");
                         } else {
                             sb.append("->\n");
                         }
                    }

                    
                    Set<String> branchProcessed = new LinkedHashSet<>(processed);
                    traverseFlowFromNode(sb, successor, activityMap, groupMap, transitions, transitionLabels, transitionConditions, branchProcessed, doc, joinNode, false, allowedScope, false, labelToPass); 
                    branchProcessedSets.add(branchProcessed);
                }
                sb.append("end fork\n");
                

                for (Set<String> bp : branchProcessedSets) {
                    processed.addAll(bp);
                }
                
                if (joinNode != null) {

                    sb.append("->\n");
                    
                    boolean isJoinNull = false;
                    Element joinEl = activityMap.get(joinNode);
                    if (joinEl != null) {
                         String t = getTagValue(joinEl, "pd:type");
                         if (t != null && t.contains("NullActivity")) isJoinNull = true;
                    }
                    traverseFlowFromNode(sb, joinNode, activityMap, groupMap, transitions, transitionLabels, transitionConditions, processed, doc, stopAtNode, insideFork, allowedScope, false, null);
                }
            } else {

                String successor = successors.get(0);
                

                 if (allowedScope != null && !allowedScope.contains(successor)) {
                    	logger.debug("[FLOW TRAVERSAL] Stopping traversal from '{}' to '{}' because target is NOT in allowed scope: {}", nodeName, successor, allowedScope);
                        return; 
                    }
                    

                    boolean isJoinNode = (stopAtNode != null && successor.equals(stopAtNode));
                    
                    boolean successorExists = activityMap.containsKey(successor) || groupMap.containsKey(successor);
                    
                    String labelToPass = null;
                    if (successorExists && transitionLabels != null && transitionLabels.containsKey(nodeName)) {
                        String label = transitionLabels.get(nodeName).get(successor);
                        if (label != null) {
                            labelToPass = label.replace("\"", "\\\"");
                        }
                    }
                    

                    if (!groupMap.containsKey(successor)) {
                         if (labelToPass != null) {
                            sb.append("-[#483D8B]->[").append(labelToPass).append("]\n");
                         } else if (!isJoinNode) {
                             sb.append("->\n");
                         }
                    } else if (!isJoinNode && labelToPass == null) {

                    }

                    traverseFlowFromNode(sb, successor, activityMap, groupMap, transitions, transitionLabels, transitionConditions, processed, doc, stopAtNode, false, allowedScope, false, labelToPass);
            }
        }
    }
    private String findCommonJoinNode(List<String> startNodes, Map<String, List<String>> transitions) {
        if (startNodes == null || startNodes.isEmpty()) return null;
        List<Set<String>> reachableSets = new ArrayList<>();
        for (String start : startNodes) {
            Set<String> reachable = new LinkedHashSet<>();
            collectReachable(start, transitions, reachable);
            reachableSets.add(reachable);
        }
        Set<String> common = new LinkedHashSet<>(reachableSets.get(0));
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
        Set<String> visited = new LinkedHashSet<>();
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
    private void renderGroup(StringBuilder sb, Element group, Document doc, boolean insideFork, String incomingLabel) {
        String groupName = group.getAttribute("name");
        

        Map<String, Element> localActivityMap = new LinkedHashMap<>();
        Map<String, Element> localGroupMap = new LinkedHashMap<>();
        Map<String, List<String>> localTransitions = new LinkedHashMap<>();
        Map<String, Map<String, String>> localLabels = new LinkedHashMap<>();
        Set<String> scope = new LinkedHashSet<>();


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
                    localLabels.computeIfAbsent(from, k -> new LinkedHashMap<>()).put(to, label);
                }
            }
        }
        
        Set<String> processed = new LinkedHashSet<>();


        List<String> startTargets = localTransitions.getOrDefault("start", new ArrayList<>());
        

        if (startTargets.isEmpty()) {
            Set<String> internalTargets = new HashSet<>();
            for (Map.Entry<String, List<String>> entry : localTransitions.entrySet()) {
                if (scope.contains(entry.getKey())) {
                    internalTargets.addAll(entry.getValue());
                }
            }
            
            for (String name : scope) {
                if (!internalTargets.contains(name)) {
                    startTargets.add(name);
                    break;
                }
            }
            

            if (startTargets.isEmpty() && !scope.isEmpty()) {
                startTargets.add(scope.iterator().next());
            }
        }

        
    

    String groupType = group.getElementsByTagNameNS("*", "groupType").getLength() > 0 
        ? group.getElementsByTagNameNS("*", "groupType").item(0).getTextContent() 
        : null;
    String partitionLabel = groupName;
    if (groupType != null && !groupType.isEmpty()) {
        partitionLabel = groupName + " (" + groupType + ")";
    }
    
    logger.debug("[SECTION 3 GROUP] Rendering partition for group '{}'", groupName);
    int beforeLen = sb.length();
    sb.append("partition \"").append(partitionLabel).append("\" {\n");
    logger.debug("[SECTION 3 PUML] Added: {}", sb.substring(beforeLen));
        

        if (incomingLabel != null) {
             String label = incomingLabel.replace("\"", "\\\"");
             sb.append("-> \"").append(label).append("\";\n");
        } else {
             sb.append("->\n");
        }
        

        if (startTargets.size() > 1) {

            sb.append("fork\n");
            
            int i = 0;
            for (String target : startTargets) {
                 if (i > 0) sb.append("fork again\n");
                 

                  String label = localLabels.getOrDefault("start", new LinkedHashMap<>()).get(target);
                  if (label != null && !label.isEmpty()) {
                      label = label.replace("\"", "\\\""); 
                      sb.append("-> \"").append(label).append("\";\n");
                  } else {
                      sb.append("->\n");   
                  }
                  

                  traverseFlowFromNode(sb, target, localActivityMap, localGroupMap, localTransitions, localLabels, new LinkedHashMap<>(), processed, doc, null, false, scope, false, null);
                  i++;
             }
             sb.append("end fork\n");
         } else if (startTargets.size() == 1) {

              String target = startTargets.get(0);
              String label = localLabels.getOrDefault("start", new LinkedHashMap<>()).get(target);
              if (label != null && !label.isEmpty()) {
                  label = label.replace("\"", "\\\"");
                  sb.append("-> \"").append(label).append("\";\n");
              } else {
                  sb.append("->\n");
              }
              traverseFlowFromNode(sb, target, localActivityMap, localGroupMap, localTransitions, localLabels, new LinkedHashMap<>(), processed, doc, null, false, scope, false, null);
         }
         

    int beforeLen2 = sb.length();
    sb.append("}\n");
    logger.debug("[SECTION 3 PUML] Closed partition for '{}': {}", groupName, sb.substring(beforeLen2, sb.length()));

        Set<String> unvisited = new LinkedHashSet<>(scope);
        unvisited.removeAll(processed);
        
        if (!unvisited.isEmpty()) {
            logger.debug("[GROUP] Detected {} unvisited (island) nodes in group '{}': {}", unvisited.size(), groupName, unvisited);
            
            sb.append("package \"Disconnected / Unreachable\" {\n");
             
            for (String node : unvisited) {
                if (processed.contains(node)) continue;
                
                 boolean hasIncoming = false;
                 for (Map.Entry<String, List<String>> entry : localTransitions.entrySet()) {
                     if (unvisited.contains(entry.getKey()) && entry.getValue().contains(node)) {
                         hasIncoming = true;
                         break;
                     }
                 }
                 
                 if (!hasIncoming) {
                     traverseFlowFromNode(sb, node, localActivityMap, localGroupMap, localTransitions, localLabels, new LinkedHashMap<>(), processed, doc, null, false, scope, false, null);
                 }
            }
            

            for (String node : unvisited) {
                if (!processed.contains(node)) {
                  traverseFlowFromNode(sb, node, localActivityMap, localGroupMap, localTransitions, localLabels, new LinkedHashMap<>(), processed, doc, null, false, scope, false, null);
             }   }
            
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
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: TibcoDiagramGenerator <processPath> <projectRoot>");
            System.exit(1);
        }
        try {
            String processPath = args[0];
            String projectRootPath = args[1];
            File projectRoot = new File(projectRootPath);
            File processFile = new File(processPath);
            String serviceName = processFile.getName();
            if (serviceName.endsWith(".process")) {
                serviceName = serviceName.substring(0, serviceName.length() - 8);
            }
            
            TibcoDiagramGenerator generator = new TibcoDiagramGenerator();
            String puml = generator.generateIntegrationPuml(serviceName, processPath, projectRoot, null);
            System.out.println(puml);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}





