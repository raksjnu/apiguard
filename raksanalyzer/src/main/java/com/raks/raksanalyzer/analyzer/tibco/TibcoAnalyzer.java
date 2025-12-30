package com.raks.raksanalyzer.analyzer.tibco;
import com.raks.raksanalyzer.domain.model.tibco.TibcoArchive;
import com.raks.raksanalyzer.domain.model.tibco.TibcoGlobalVariable;
import com.raks.raksanalyzer.util.TibcoCryptUtils;
import com.raks.raksanalyzer.core.config.ConfigurationManager;
import com.raks.raksanalyzer.core.utils.FileUtils;
import com.raks.raksanalyzer.core.config.ConfigurationManager;
import com.raks.raksanalyzer.core.utils.FileUtils;
import com.raks.raksanalyzer.core.utils.XmlUtils;
import com.raks.raksanalyzer.util.TibcoConfigParser;
import com.raks.raksanalyzer.domain.enums.ProjectType;
import com.raks.raksanalyzer.domain.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
public class TibcoAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(TibcoAnalyzer.class);
    private final Path projectPath;
    private final Path configFilePath; 
    private final List<String> environments;
    private final Map<String, List<TibcoGlobalVariable>> globalVariables = new HashMap<>();
    public TibcoAnalyzer(Path projectPath, List<String> environments) {
        this(projectPath, environments, null);
    }
    public TibcoAnalyzer(Path projectPath, List<String> environments, Path configFilePath) {
        this.projectPath = projectPath;
        this.environments = environments != null ? environments : new ArrayList<>();
        this.configFilePath = configFilePath;
        if (configFilePath != null) {
        } else {
        }
    }
    public String resolveGV(String value) {
        if (value == null || !value.startsWith("%%") || !value.endsWith("%%")) {
            return value;
        }
        String key = value.substring(2, value.length() - 2);
        for (List<TibcoGlobalVariable> vars : globalVariables.values()) {
            if (vars == null) continue;
            for (TibcoGlobalVariable gv : vars) {
                if (gv.getFullName().equals(key)) {
                    return gv.getValue();
                }
            }
            for (TibcoGlobalVariable gv : vars) {
                if (gv.getName().equals(key)) {
                    return gv.getValue();
                }
            }
        }
        return value; 
    }
    public AnalysisResult analyze() {
        logger.info("Starting Tibco BW5 project analysis: {}", projectPath);
        AnalysisResult result = new AnalysisResult();
        result.setAnalysisId(UUID.randomUUID().toString());
        result.setStartTime(java.time.LocalDateTime.now());
        try {
            ProjectInfo projectInfo = extractProjectInfo();
            result.setProjectInfo(projectInfo);
            parseGlobalVariables();
            analyzeAdaptersAndPlugins(result);
            List<Path> processFiles = FileUtils.findFilesByExtension(projectPath, "process");
            logger.info("Found {} process files", processFiles.size());
            for (Path processFile : processFiles) {
                parseProcess(processFile, result);
            }
            parseServiceAgents(result);
            parseRestServices(result);
            generateFileInventory(result);
            findAndParseResources(result);
            analyzeAdaptersAndPlugins(result);
            parseArchives(result);
            convertGlobalVariablesToProperties(result);
            result.setSuccess(true);
            logger.info("Tibco BW5 analysis completed successfully");
        } catch (Exception e) {
            logger.error("Tibco BW5 analysis failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        } finally {
            result.setEndTime(java.time.LocalDateTime.now());
        }
        return result;
    }
    private void parseArchives(AnalysisResult result) {
        try {
            List<Path> archiveFiles = FileUtils.findFilesByExtension(projectPath, "archive");
            List<TibcoArchive> archives = new ArrayList<>();
            for (Path archiveFile : archiveFiles) {
                try {
                    if (Files.size(archiveFile) == 0) {
                        logger.warn("Skipping empty archive file: {}", archiveFile);
                        continue;
                    }
                    Document doc = XmlUtils.parseXmlFile(archiveFile);
                    Element root = doc.getDocumentElement(); 
                    Element enterpriseArchive = XmlUtils.getFirstChildElement(root, "enterpriseArchive");
                    if (enterpriseArchive != null) {
                        TibcoArchive ear = new TibcoArchive();
                        ear.setName(archiveFile.getFileName().toString());
                        ear.setType("ENTERPRISE");
                        ear.setFilePath(archiveFile.toString());
                        String author = XmlUtils.getElementText(XmlUtils.getFirstChildElement(enterpriseArchive, "authorProperty"));
                        if (author != null) ear.setAuthor(author);
                        String version = XmlUtils.getElementText(XmlUtils.getFirstChildElement(enterpriseArchive, "versionProperty"));
                        if (version != null) ear.setVersion(version);
                        String location = XmlUtils.getElementText(XmlUtils.getFirstChildElement(enterpriseArchive, "fileLocationProperty"));
                        List<Element> processArchives = XmlUtils.getElementsByTagName(doc, "processArchive");
                        for (Element pa : processArchives) {
                            TibcoArchive child = new TibcoArchive();
                            child.setName(pa.getAttribute("name"));
                            child.setType("PROCESS");
                            String processAuthor = XmlUtils.getElementText(XmlUtils.getFirstChildElement(pa, "authorProperty"));
                            if (processAuthor != null && !processAuthor.isEmpty()) {
                                child.setAuthor(processAuthor);
                            }
                            String prop = XmlUtils.getElementText(XmlUtils.getFirstChildElement(pa, "processProperty"));
                            if (prop != null && !prop.isEmpty()) {
                                String[] processes = prop.split(",");
                                for (String p : processes) {
                                    child.addProcess(p.trim());
                                }
                            }
                            ear.addChild(child);
                        }
                        List<Element> sharedArchives = XmlUtils.getElementsByTagName(doc, "sharedArchive");
                        for (Element sa : sharedArchives) {
                            TibcoArchive child = new TibcoArchive();
                            child.setName(sa.getAttribute("name"));
                            child.setType("SHARED");
                            String sharedAuthor = XmlUtils.getElementText(XmlUtils.getFirstChildElement(sa, "authorProperty"));
                            if (sharedAuthor != null && !sharedAuthor.isEmpty()) {
                                child.setAuthor(sharedAuthor);
                            }
                            String resProp = XmlUtils.getElementText(XmlUtils.getFirstChildElement(sa, "resourceProperty"));
                            if (resProp != null && !resProp.isEmpty()) {
                                String[] resources = resProp.split(",");
                                for (String r : resources) {
                                    child.addSharedResource(r.trim());
                                }
                            }
                            String fileProp = XmlUtils.getElementText(XmlUtils.getFirstChildElement(sa, "fileProperty"));
                            if (fileProp != null && !fileProp.isEmpty()) {
                                String[] files = fileProp.split(",");
                                for (String f : files) {
                                    child.addFile(f.trim());
                                }
                            }
                            String jarProp = XmlUtils.getElementText(XmlUtils.getFirstChildElement(sa, "jarFileProperty"));
                            if (jarProp != null && !jarProp.isEmpty()) {
                                String[] jars = jarProp.split(",");
                                for (String j : jars) {
                                    child.addJar(j.trim());
                                }
                            }
                            ear.addChild(child);
                        }
                        List<Element> adapterArchives = XmlUtils.getElementsByTagName(doc, "adapterArchive");
                        for (Element aa : adapterArchives) {
                            TibcoArchive child = new TibcoArchive();
                            child.setName(aa.getAttribute("name"));
                            child.setType("ADAPTER");
                            ear.addChild(child);
                        }
                        archives.add(ear);
                    }
                } catch (Exception e) {
                    logger.warn("Skipping invalid archive file: {} - {}", archiveFile, e.getMessage());
                }
            }
            result.addMetadata("archives", archives);
        } catch (IOException e) {
            logger.error("Error finding archive files", e);
        }
    }
    private ProjectInfo extractProjectInfo() {
        ProjectInfo info = new ProjectInfo();
        info.setProjectType(ProjectType.TIBCO_BW5);
        Path actualProjectPath = findActualTibcoProjectFolder(projectPath);
        info.setProjectPath(actualProjectPath.toAbsolutePath().toString());
        info.setProjectName(actualProjectPath.getFileName().toString());
        info.setTibcoVersion("5.x");
        return info;
    }
    private Path findActualTibcoProjectFolder(Path startPath) {
        if (isTibcoProjectFolder(startPath)) {
            return startPath;
        }
        try {
            return Files.list(startPath)
                .filter(Files::isDirectory)
                .filter(this::isTibcoProjectFolder)
                .findFirst()
                .orElse(startPath); 
        } catch (IOException e) {
            logger.warn("Error searching for TIBCO project folder: {}", e.getMessage());
            return startPath;
        }
    }
    private boolean isTibcoProjectFolder(Path folder) {
        try {
            if (Files.exists(folder.resolve(".folder"))) {
                return true;
            }
            if (Files.exists(folder.resolve(".vcrepo"))) {
                return true;
            }
            if (Files.isDirectory(folder.resolve("AESchemas"))) {
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    private List<Path> findProcessFiles() throws IOException {
        return FileUtils.findFilesByExtension(projectPath, "process");
    }
    private void parseGlobalVariables() {
        try {
            logger.info("Parsing global variables with flexible resolution...");
            if (configFilePath != null) {
            } else {
            }
            Map<String, List<TibcoGlobalVariable>> parsedVars = 
                TibcoConfigParser.parseGlobalVariables(projectPath, configFilePath);
            if (!parsedVars.isEmpty()) {
                globalVariables.putAll(parsedVars);
                logger.info("Loaded global variables for {} environments", parsedVars.size());
            } else {
                logger.warn("No global variables found in project");
            }
        } catch (Exception e) {
            logger.error("Failed to parse global variables", e);
        }
    }
    private void parseSubstvarFile(Path substvarFile) {
        try {
            Document doc = XmlUtils.parseXmlFile(substvarFile);
            List<Element> variables = XmlUtils.getElementsByTagName(doc, "globalVariable");
            String groupName = deriveGroupName(substvarFile);
            List<TibcoGlobalVariable> groupVars = new ArrayList<>();
            for (Element varElement : variables) {
                Element nameElement = XmlUtils.getFirstChildElement(varElement, "name");
                Element valueElement = XmlUtils.getFirstChildElement(varElement, "value");
                Element typeElement = XmlUtils.getFirstChildElement(varElement, "type");
                Element depSettableInfo = XmlUtils.getFirstChildElement(varElement, "deploymentSettable");
                Element svcSettableInfo = XmlUtils.getFirstChildElement(varElement, "serviceSettable");
                if (nameElement != null && valueElement != null) {
                    String name = XmlUtils.getElementText(nameElement);
                    String value = XmlUtils.getElementText(valueElement);
                    String type = typeElement != null ? XmlUtils.getElementText(typeElement) : "String";
                    boolean depSettable = depSettableInfo != null && "true".equalsIgnoreCase(XmlUtils.getElementText(depSettableInfo));
                    boolean svcSettable = svcSettableInfo != null && "true".equalsIgnoreCase(XmlUtils.getElementText(svcSettableInfo));
                    if (value != null && value.startsWith("#!")) {
                         String decryptEnabled = ConfigurationManager.getInstance().getProperty("tibco.global.variables.decrypt.passwords");
                         if ("true".equalsIgnoreCase(decryptEnabled)) {
                             value = TibcoCryptUtils.decrypt(value);
                         }
                    }
                    TibcoGlobalVariable gv = new TibcoGlobalVariable(name, value, type, groupName);
                    gv.setDeploymentSettable(depSettable);
                    gv.setServiceSettable(svcSettable);
                    groupVars.add(gv);
                }
            }
            globalVariables.put(groupName, groupVars);
            logger.debug("Parsed {} variables from group: {}", groupVars.size(), groupName);
        } catch (IOException e) {
            logger.error("Failed to parse .substvar file: {}", substvarFile, e);
        }
    }
    private String deriveGroupName(Path substvarFile) {
        Path defaultVarsDir = projectPath.resolve("defaultVars");
        Path parent = substvarFile.getParent();
        if (parent == null || parent.equals(defaultVarsDir)) {
            return "defaultVars";
        }
        try {
            String rel = defaultVarsDir.relativize(parent).toString();
            rel = rel.replace("\\", "/");
            return rel;
        } catch (IllegalArgumentException e) {
            return parent.getFileName().toString();
        }
    }
    private void parseProcess(Path processFile, AnalysisResult result) {
        try {
            logger.debug("Parsing Tibco process: {}", processFile.getFileName());
            Document doc = XmlUtils.parseXmlFile(processFile);
            Element pdName = XmlUtils.getFirstChildElement(doc.getDocumentElement(), "pd:name");
            String processName = pdName != null ? XmlUtils.getElementText(pdName) : processFile.getFileName().toString();
            FlowInfo flowInfo = new FlowInfo();
            flowInfo.setName(processName);
            flowInfo.setFileName(processFile.toString());
            try {
                String relPath = projectPath.relativize(processFile).toString().replace("\\", "/");
                flowInfo.setRelativePath(relPath);
            } catch (IllegalArgumentException e) {
                flowInfo.setRelativePath(processFile.getFileName().toString());
            }
            Element pdStarter = XmlUtils.getFirstChildElement(doc.getDocumentElement(), "pd:starter");
            if (pdStarter != null) {
                flowInfo.setType("starter-process");
                String starterName = pdStarter.getAttribute("name");
                Element pdType = XmlUtils.getFirstChildElement(pdStarter, "pd:type");
                String starterType = "";
                if (pdType != null) {
                    String fullType = XmlUtils.getElementText(pdType);
                    if (fullType != null && fullType.contains(".")) {
                        starterType = fullType.substring(fullType.lastIndexOf(".") + 1);
                    } else {
                        starterType = fullType != null ? fullType : "";
                    }
                }
                if (starterName != null && !starterName.isEmpty()) {
                    flowInfo.setDescription("Starter: " + starterName + "|Type: " + starterType);
                } else {
                    flowInfo.setDescription("Starter: Unknown|Type: " + starterType);
                }
                Element config = XmlUtils.getFirstChildElement(pdStarter, "config");
                if (config != null) {
                    try {
                        parseGenericConfig(config, flowInfo.getStarterConfig(), "");
                        if (starterType.contains("SOAPEventSource")) {
                            String service = XmlUtils.getElementText(XmlUtils.getFirstChildElement(config, "service"));
                            String operation = XmlUtils.getElementText(XmlUtils.getFirstChildElement(config, "operation"));
                            String httpURI = XmlUtils.getElementText(XmlUtils.getFirstChildElement(config, "httpURI"));
                            String soapAction = XmlUtils.getElementText(XmlUtils.getFirstChildElement(config, "soapAction"));
                            String sharedChannel = XmlUtils.getElementText(XmlUtils.getFirstChildElement(config, "sharedChannel"));
                            if (service != null && service.contains(":")) {
                                service = service.substring(service.indexOf(":") + 1);
                            }
                            httpURI = resolveGV(httpURI);
                            soapAction = resolveGV(soapAction);
                            String serviceAddress = httpURI;
                            if (sharedChannel != null && !sharedChannel.isEmpty()) {
                                if (result.getMetadata("httpConnections") != null) {
                                    Map<String, Map<String, String>> httpConns = (Map<String, Map<String, String>>) result.getMetadata("httpConnections");
                                    String lookupPath = sharedChannel.replace("\\", "/");
                                    if (!lookupPath.startsWith("/")) lookupPath = "/" + lookupPath;
                                    Map<String, String> conn = httpConns.get(lookupPath);
                                    if (conn != null) {
                                        String host = conn.getOrDefault("host", "localhost");
                                        String port = conn.getOrDefault("port", "80");
                                        serviceAddress = "http://" + host + ":" + port + httpURI;
                                    }
                                }
                            }
                            flowInfo.addStarterConfig("service", service);
                            flowInfo.addStarterConfig("operation", operation);
                            flowInfo.addStarterConfig("httpURI", httpURI);
                            flowInfo.addStarterConfig("soapAction", soapAction);
                            flowInfo.addStarterConfig("serviceAddress", serviceAddress);
                        } else if (starterType.contains("AESubscriberActivity") || starterType.contains("AERPCServerActivity")) {
                             String endpoint = XmlUtils.getElementText(XmlUtils.getFirstChildElement(config, "tpPluginEndpointName"));
                             if (endpoint != null) flowInfo.addStarterConfig("endpoint", endpoint);
                        } else if (starterType.contains("HTTPEventSource")) {
                             String sharedChannel = XmlUtils.getElementText(XmlUtils.getFirstChildElement(config, "sharedChannel"));
                             flowInfo.addStarterConfig("sharedChannel", sharedChannel);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse starter config for {}", processName, e);
                    }
                }
            } else {
                flowInfo.setType("process");
            }
            if (flowInfo.getDescription() == null) {
                Element pdDescription = XmlUtils.getFirstChildElement(doc.getDocumentElement(), "pd:description");
                if (pdDescription != null) {
                    flowInfo.setDescription(XmlUtils.getElementText(pdDescription));
                }
            }
            parseActivities(doc, flowInfo);
            if ("starter-process".equals(flowInfo.getType())) {
                Optional<ComponentInfo> restAdapter = flowInfo.getComponents().stream()
                    .filter(c -> "com.tibco.plugin.json.rest.server.activities.RestAdapterActivity".equals(c.getType()))
                    .findFirst();
                if (restAdapter.isPresent()) {
                    String fullType = restAdapter.get().getType();
                    String simpleType = "REST Service"; 
                    if (fullType != null && fullType.contains(".")) {
                        simpleType = fullType.substring(fullType.lastIndexOf(".") + 1);
                    }
                    logger.info("Process {} detected as REST Service, type: {}", processName, simpleType);
                    String desc = flowInfo.getDescription();
                    if (desc != null) {
                        if (desc.contains("Type: ")) {
                            flowInfo.setDescription(desc.replaceAll("Type: .*", "Type: " + simpleType));
                        } else {
                            flowInfo.setDescription(desc + "|Type: " + simpleType);
                        }
                    }
                }
            }
            result.getFlows().add(flowInfo);
        } catch (IOException e) {
            logger.error("Failed to parse Tibco process: {}", processFile, e);
        }
    }
    private void parseActivities(Document doc, FlowInfo flowInfo) {
        Element starter = XmlUtils.getFirstChildElement(doc.getDocumentElement(), "pd:starter");
        if (starter != null) {
            ComponentInfo starterInfo = parseActivity(starter);
            if (starterInfo != null) {
                flowInfo.addComponent(starterInfo);
            }
        }
        List<Element> groups = XmlUtils.getElementsByTagName(doc, "pd:group");
        for (Element group : groups) {
            ComponentInfo groupInfo = parseActivity(group);
            if (groupInfo != null) {
                flowInfo.addComponent(groupInfo);
                List<Element> groupActivities = XmlUtils.getChildElements(group, "pd:activity");
                for (Element activity : groupActivities) {
                    ComponentInfo activityInfo = parseActivity(activity);
                    if (activityInfo != null) {
                        flowInfo.addComponent(activityInfo);
                    }
                }
            }
        }
        List<Element> activities = XmlUtils.getElementsByTagName(doc, "pd:activity");
        for (Element activity : activities) {
            org.w3c.dom.Node parent = activity.getParentNode();
            if (parent != null && parent.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element parentElement = (Element) parent;
                if ("pd:group".equals(parentElement.getNodeName())) {
                    continue;
                }
            }
            ComponentInfo componentInfo = parseActivity(activity);
            if (componentInfo != null) {
                flowInfo.addComponent(componentInfo);
            }
        }
    }
    private ComponentInfo parseActivity(Element activityElement) {
        ComponentInfo componentInfo = new ComponentInfo();
        String activityName = activityElement.getAttribute("name");
        if (activityName != null && !activityName.isEmpty()) {
            componentInfo.setName(activityName);
        } else {
            componentInfo.setName("Unnamed");
        }
        Element typeElement = XmlUtils.getFirstChildElement(activityElement, "pd:type");
        if (typeElement != null) {
            String type = XmlUtils.getElementText(typeElement);
            componentInfo.setType(type);
            componentInfo.setCategory(determineActivityCategory(type));
        }
        Element configElement = XmlUtils.getFirstChildElement(activityElement, "config");
        if (configElement != null) {
            parseActivityConfig(configElement, componentInfo);
        }
        Element inputBindingsElement = XmlUtils.getFirstChildElement(activityElement, "pd:inputBindings");
        if (inputBindingsElement != null) {
            parseGenericConfig(inputBindingsElement, componentInfo.getInputBindings(), "");
        }
        return componentInfo;
    }
    private void parseServiceAgents(AnalysisResult result) {
        try {
            List<Path> serviceAgentFiles = FileUtils.findFilesByExtension(projectPath, "serviceagent");
            List<Map<String, Object>> serviceAgents = new ArrayList<>();
            for (Path file : serviceAgentFiles) {
                try {
                    Document doc = XmlUtils.parseXmlFile(file);
                    Element root = doc.getDocumentElement();
                    Map<String, Object> agentInfo = new HashMap<>();
                    String name = XmlUtils.getElementText(XmlUtils.getFirstChildElement(root, "name")); 
                    if (name == null) {
                        Element config = XmlUtils.getFirstChildElement(root, "config");
                        if (config != null) name = XmlUtils.getElementText(XmlUtils.getFirstChildElement(config, "name"));
                    }
                    if (name == null) name = file.getFileName().toString();
                    agentInfo.put("name", name);
                    agentInfo.put("file", file.getFileName().toString());
                    agentInfo.put("path", projectPath.relativize(file).toString().replace("\\", "/"));
                    String wsdlLocation = "";
                    List<Element> wsdlDetails = XmlUtils.getElementsByTagName(doc, "wsdlDetail");
                    if (!wsdlDetails.isEmpty()) {
                        wsdlLocation = wsdlDetails.get(0).getAttribute("location");
                    }
                    agentInfo.put("wsdlLocation", wsdlLocation);
                    String serviceAddress = "";
                    Map<String, String> opActions = new HashMap<>();
                    Map<String, String> opImpls = new HashMap<>();
                    List<Element> rows = XmlUtils.getElementsByTagName(doc, "row");
                    for (Element row : rows) {
                        String opName = row.getAttribute("opName");
                        String opImpl = row.getAttribute("opImpl");
                        if (opName != null && !opName.isEmpty() && opImpl != null) {
                            opImpls.put(opName, opImpl);
                        }
                    }
                    String endpointPath = "";
                    List<Element> httpUris = XmlUtils.getElementsByTagName(doc, "httpURI");
                    if (!httpUris.isEmpty()) {
                        endpointPath = XmlUtils.getElementText(httpUris.get(0));
                        endpointPath = resolveGV(endpointPath);
                    }
                    String sharedChannel = "";
                    List<Element> sharedChannels = XmlUtils.getElementsByTagName(doc, "sharedChannel");
                    if (!sharedChannels.isEmpty()) {
                         sharedChannel = XmlUtils.getElementText(sharedChannels.get(0));
                    }
                    if (!sharedChannel.isEmpty() && !endpointPath.isEmpty()) {
                         if (result.getMetadata("httpConnections") != null) {
                             Map<String, Map<String, String>> httpConns = (Map<String, Map<String, String>>) result.getMetadata("httpConnections");
                             String lookupPath = sharedChannel.replace("\\", "/");
                             if (!lookupPath.startsWith("/")) lookupPath = "/" + lookupPath;
                             Map<String, String> conn = httpConns.get(lookupPath);
                             if (conn != null) {
                                 String host = conn.getOrDefault("host", "localhost");
                                 String port = conn.getOrDefault("port", "80");
                                 serviceAddress = "http://" + host + ":" + port + endpointPath;
                             } else {
                                 serviceAddress = endpointPath;
                             }
                         } else {
                             serviceAddress = endpointPath;
                         }
                    } else {
                        serviceAddress = endpointPath;
                    }
                    List<Element> ops = XmlUtils.getElementsByTagName(doc, "operation");
                    for (Element op : ops) {
                        String opName = op.getAttribute("name");
                        if (opName != null && !opName.isEmpty()) {
                            Element general = XmlUtils.getFirstChildElement(op, "general");
                            if (general != null) {
                                Element config = XmlUtils.getFirstChildElement(general, "config");
                                if (config != null) {
                                  String action = XmlUtils.getElementText(XmlUtils.getFirstChildElement(config, "soapAction"));
                                  if (action != null) opActions.put(opName, action);
                                }
                            }
                        }
                    }
                    agentInfo.put("address", serviceAddress);
                    List<Map<String, String>> operations = new ArrayList<>();
                    for (Map.Entry<String, String> entry : opImpls.entrySet()) {
                        Map<String, String> opFn = new HashMap<>();
                        opFn.put("name", entry.getKey());
                        opFn.put("implementation", entry.getValue());
                        opFn.put("soapAction", opActions.getOrDefault(entry.getKey(), ""));
                        operations.add(opFn);
                    }
                    agentInfo.put("operations", operations);
                    serviceAgents.add(agentInfo);
                } catch (Exception e) {
                    logger.warn("Failed to parse service agent: {}", file, e);
                }
            }
            if (!serviceAgents.isEmpty()) {
                result.addMetadata("serviceAgents", serviceAgents);
                logger.info("Parsed {} service agents", serviceAgents.size());
            }
        } catch (IOException e) {
            logger.error("Failed to find service agent files", e);
        }
    }
    private void parseRestServices(AnalysisResult result) {
        List<Map<String, Object>> restServices = new ArrayList<>();
        List<FlowInfo> flows = result.getFlows();
        Set<String> processedPaths = new HashSet<>(); 
        for (FlowInfo flow : flows) {
            try {
                Path processFile = Paths.get(flow.getFileName());
                if (!Files.exists(processFile)) continue;
                String normalizedPath = processFile.toString().replace("\\", "/");
                logger.info("Processing REST service file: {}, already processed: {}", normalizedPath, processedPaths.contains(normalizedPath));
                if (processedPaths.contains(normalizedPath)) {
                    logger.warn("Skipping duplicate REST service processing for: {}", normalizedPath);
                    continue;
                }
                Document doc = XmlUtils.parseXmlFile(processFile);
                if (doc == null) continue;
                List<Element> activities = XmlUtils.getElementsByTagName(doc, "pd:activity");
                for (Element activity : activities) {
                    Element typeElem = XmlUtils.getFirstChildElement(activity, "pd:type");
                    if (typeElem != null && "com.tibco.plugin.json.rest.server.activities.RestAdapterActivity".equals(XmlUtils.getElementText(typeElem))) {
                        Map<String, Object> serviceInfo = new HashMap<>();
                        serviceInfo.put("processName", flow.getName());
                        serviceInfo.put("processFile", processFile.toString()); 
                        String relativePath = projectPath.relativize(processFile).toString().replace("\\", "/");
                        String projectName = projectPath.getFileName().toString();
                        Path parent = projectPath.getParent();
                        String projectIdentifier = projectName;
                        if (parent != null && parent.getFileName() != null) {
                            projectIdentifier = parent.getFileName().toString() + "/" + projectName;
                        }
                        String displayPath = projectIdentifier + "/" + relativePath;
                        serviceInfo.put("processPath", displayPath);
                        serviceInfo.put("relativePath", relativePath); 
                        Element config = XmlUtils.getFirstChildElement(activity, "config");
                        if (config != null) {
                            String wadlRef = XmlUtils.getElementText(XmlUtils.getFirstChildElement(config, "WADLReference"));
                            serviceInfo.put("wadlSource", wadlRef);
                            Map<String, Map<String, String>> wadlMethods = parseWADL(wadlRef, processFile);
                            List<Map<String, String>> bindings = new ArrayList<>();
                            Element restService = XmlUtils.getFirstChildElement(config, "RestService");
                            if (restService != null) {
                                Element outerBinding = XmlUtils.getFirstChildElement(restService, "OuterBinding");
                                if (outerBinding != null) {
                                    List<Element> bindingElems = XmlUtils.getChildElements(outerBinding, "Binding");
                                    for (Element bindingElem : bindingElems) {
                                        String path = bindingElem.getAttribute("path"); 
                                        String implProcess = bindingElem.getAttribute("process");
                                        Map<String, String> bindingInfo = new HashMap<>();
                                        bindingInfo.put("implementationProcess", implProcess);
                                        String methodId = "";
                                        if (path != null && !path.isEmpty()) {
                                            if (path.contains("/")) {
                                                methodId = path.substring(path.lastIndexOf("/") + 1);
                                            } else {
                                                methodId = path;
                                            }
                                            logger.info("Extracted method ID '{}' from binding path '{}'", methodId, path);
                                        }
                                        logger.info("Looking up method ID '{}' in WADL map with {} entries: {}", 
                                            methodId, wadlMethods.size(), wadlMethods.keySet());
                                        Map<String, String> methodDetails = wadlMethods.get(methodId);
                                        if (methodDetails != null) {
                                            logger.info("Found method details for '{}': method={}, resourcePath={}", 
                                                methodId, methodDetails.get("method"), methodDetails.get("resourcePath"));
                                            bindingInfo.put("method", methodDetails.get("method"));
                                            bindingInfo.put("resourcePath", methodDetails.get("resourcePath"));
                                        } else {
                                            logger.warn("Could not find WADL method for ID: '{}', available IDs: {}", methodId, wadlMethods.keySet());
                                            bindingInfo.put("method", "Unknown");
                                            bindingInfo.put("resourcePath", path);
                                        }
                                        bindings.add(bindingInfo);
                                    }
                                }
                            }
                            serviceInfo.put("bindings", bindings);
                        }
                        restServices.add(serviceInfo);
                        processedPaths.add(normalizedPath); 
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to parse REST service in flow: {}", flow.getName(), e);
            }
        }
        logger.info("Parsed {} REST services", restServices.size());
        for (Map<String, Object> svc : restServices) {
            logger.info("REST Service: {} - WADL: {}, Bindings: {}", 
                svc.get("processName"), svc.get("wadlSource"), 
                svc.get("bindings") != null ? ((List<Map<String, String>>)svc.get("bindings")).size() : 0);
        }
        result.addMetadata("restServices", restServices);
    }
    private Map<String, Map<String, String>> parseWADL(String wadlRef, Path processFile) {
        Map<String, Map<String, String>> methodMap = new HashMap<>();
        if (wadlRef == null || wadlRef.isEmpty()) return methodMap;
        try {
            String cleanRef = wadlRef.startsWith("/") ? wadlRef.substring(1) : wadlRef;
            Path processDir = processFile.getParent(); 
            Path projectRoot = null;
            Path currentDir = processDir;
            int maxLevelsUp = 5; 
            for (int i = 0; i < maxLevelsUp && currentDir != null; i++) {
                Path candidateWadl = currentDir.resolve(cleanRef);
                if (Files.exists(candidateWadl)) {
                    projectRoot = currentDir;
                    break;
                }
                currentDir = currentDir.getParent();
            }
            if (projectRoot == null) {
                projectRoot = processDir != null ? processDir.getParent() : projectPath;
            }
            Path wadlFile = projectRoot.resolve(cleanRef);
            logger.info("Attempting to parse WADL: ref='{}', cleanRef='{}', processFile='{}', projectRoot='{}', resolved path='{}'", 
                wadlRef, cleanRef, processFile, projectRoot, wadlFile);
            if (!Files.exists(wadlFile)) {
                logger.warn("WADL file not found at: {}", wadlFile);
                return methodMap;
            }
            logger.info("WADL file found, parsing: {}", wadlFile);
            Document doc = XmlUtils.parseXmlFile(wadlFile);
            Element app = doc.getDocumentElement();
            Element resourcesElem = XmlUtils.getFirstChildElement(app, "resources");
            if (resourcesElem != null) {
                String base = resourcesElem.getAttribute("base");
                base = resolveGV(base);
                List<Element> resources = XmlUtils.getChildElements(resourcesElem, "resource");
                for (Element resource : resources) {
                    String resourcePath = resource.getAttribute("path");
                    String fullPath = base + resourcePath;
                    List<Element> methods = XmlUtils.getChildElements(resource, "method");
                    for (Element method : methods) {
                        String id = method.getAttribute("id");
                        String name = method.getAttribute("name"); 
                        Map<String, String> details = new HashMap<>();
                        details.put("method", name);
                        details.put("resourcePath", fullPath);
                        methodMap.put(id, details);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse WADL: {}", wadlRef, e);
        }
        logger.info("Parsed WADL {} - found {} methods: {}", wadlRef, methodMap.size(), methodMap.keySet());
        return methodMap;
    }
    private void parseActivityConfig(Element configElement, ComponentInfo componentInfo) {
        org.w3c.dom.NodeList children = configElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String tagName = element.getTagName();
                String value = XmlUtils.getElementText(element);
                value = resolveGV(value);
                componentInfo.addAttribute(tagName, value);
            }
        }
    }
    private String determineActivityCategory(String type) {
        if (type == null) {
            return "unknown";
        }
        if (type.contains("JDBC")) {
            return "database";
        } else if (type.contains("HTTP") || type.contains("SOAP")) {
            return "connector";
        } else if (type.contains("Mapper") || type.contains("Transform")) {
            return "transformer";
        } else if (type.contains("Assign") || type.contains("SetVariable")) {
            return "variable";
        } else if (type.contains("Log")) {
            return "logger";
        } else if (type.contains("Call")) {
            return "process-call";
        } else {
            return "activity";
        }
    }
    private void convertGlobalVariablesToProperties(AnalysisResult result) {
        List<String> envList = (environments != null && !environments.isEmpty()) 
            ? environments 
            : List.of("dev", "qa", "prod");
        List<PropertyInfo> props = new ArrayList<>();
        for (Map.Entry<String, List<TibcoGlobalVariable>> entry : globalVariables.entrySet()) {
            String group = entry.getKey();
            for (TibcoGlobalVariable gv : entry.getValue()) {
                PropertyInfo prop = new PropertyInfo();
                String key;
                if (group.equals("defaultVars") || group.equals("default")) {
                    key = gv.getName();
                } else {
                    key = group + "/" + gv.getName();
                }
                if (key.startsWith("/")) key = key.substring(1);
                prop.setKey(key);
                prop.setValue(gv.getValue()); 
                prop.setDescription("Type: " + gv.getType()); 
                prop.setSource(group + ".substvar");
                props.add(prop);
                result.getProjectInfo().getProperties().setProperty(key, gv.getValue());
            }
        }
        result.getProperties().addAll(props);
        result.setGlobalVariables(globalVariables);
    }
    private void generateFileInventory(AnalysisResult result) {
        try {
            Map<String, Integer> fileCountByType = new HashMap<>();
            Map<String, Long> fileSizeByType = new HashMap<>();
            List<Map<String, String>> filesList = new ArrayList<>(); 
            ConfigurationManager config = ConfigurationManager.getInstance();
            String excludedFoldersStr = config.getProperty("analyzer.tibco.files.inventory.exclude.folders", "ae,AESchemas");
            String excludedFilesStr = config.getProperty("analyzer.tibco.files.inventory.exclude.files", 
                ".folder,ae.aeschema,corba.aeschema,java.aeschema,sql.aeschema");
            Set<String> excludedFolders = new HashSet<>(Arrays.asList(excludedFoldersStr.split(",")));
            Set<String> excludedFiles = new HashSet<>(Arrays.asList(excludedFilesStr.split(",")));
            Files.walk(projectPath)
                .filter(Files::isRegularFile)
                .filter(file -> {
                    for (String excludedFolder : excludedFolders) {
                        String folderToCheck = excludedFolder.trim();
                        if (file.toString().contains(File.separator + folderToCheck + File.separator) ||
                            file.toString().contains(File.separator + folderToCheck)) {
                            return false;
                        }
                    }
                    String fileName = file.getFileName().toString();
                    return !excludedFiles.contains(fileName);
                })
                .forEach(file -> {
                    String fileName = file.getFileName().toString();
                    String extension = getFileExtension(fileName);
                    fileCountByType.merge(extension, 1, Integer::sum);
                    try {
                        long size = Files.size(file);
                        fileSizeByType.merge(extension, size, Long::sum);
                        Map<String, String> fileInfo = new HashMap<>();
                        fileInfo.put("name", fileName);
                        fileInfo.put("path", projectPath.relativize(file).toString().replace("\\", "/"));
                        fileInfo.put("extension", extension);
                        filesList.add(fileInfo);
                    } catch (IOException e) {
                        logger.warn("Failed to get size for file: {}", file, e);
                    }
                });
            result.addMetadata("fileInventory.countByType", fileCountByType);
            result.addMetadata("fileInventory.sizeByType", fileSizeByType);
            result.addMetadata("fileInventory.filesList", filesList);
            logger.info("Generated file inventory: {} file types, {} total files", 
                fileCountByType.size(), filesList.size());
        } catch (IOException e) {
            logger.error("Failed to generate file inventory", e);
        }
    }
    private void analyzeAdaptersAndPlugins(AnalysisResult result) {
        Map<String, Integer> adapterUsage = new HashMap<>();
        Map<String, Integer> pluginUsage = new HashMap<>();
        for (ComponentInfo component : result.getComponents()) {
            String type = component.getType();
            if (type != null) {
                if (type.contains("com.tibco.plugin.file")) {
                    adapterUsage.merge("File Adapter", 1, Integer::sum);
                } else if (type.contains("com.tibco.plugin.jms")) {
                    adapterUsage.merge("JMS Adapter", 1, Integer::sum);
                } else if (type.contains("com.tibco.plugin.soap")) {
                    adapterUsage.merge("SOAP Adapter", 1, Integer::sum);
                } else if (type.contains("com.tibco.plugin.http")) {
                    adapterUsage.merge("HTTP Adapter", 1, Integer::sum);
                } else if (type.contains("com.tibco.plugin.jdbc")) {
                    adapterUsage.merge("JDBC Adapter", 1, Integer::sum);
                } else if (type.contains("com.tibco.plugin.ae")) {
                    adapterUsage.merge("AE Adapter", 1, Integer::sum);
                } else if (type.contains("com.tibco.plugin.json")) {
                    pluginUsage.merge("JSON Plugin", 1, Integer::sum);
                } else if (type.contains("com.tibco.plugin.parse")) {
                    pluginUsage.merge("Parse Plugin", 1, Integer::sum);
                } else if (type.contains("com.tibco.plugin.mapper")) {
                    pluginUsage.merge("Mapper Plugin", 1, Integer::sum);
                } else if (type.contains("com.tibco.plugin.java")) {
                    pluginUsage.merge("Java Plugin", 1, Integer::sum);
                } else if (type.contains("com.tibco.plugin.timer")) {
                    pluginUsage.merge("Timer Plugin", 1, Integer::sum);
                }
            }
        }
        result.addMetadata("adapterUsage", adapterUsage);
        result.addMetadata("pluginUsage", pluginUsage);
        logger.info("Identified {} adapter types and {} plugin types", 
            adapterUsage.size(), pluginUsage.size());
    }
    private void findAndParseResources(AnalysisResult result) {
        String[] extensions = {
            "sharedhttp", "sharedjdbc", "sharedjmscon", "id", "mqcon", "sharedsalesforce", 
            "sharedtcp", "sharedftp", "rvtransport", "mftsharedconn",
            "sharedvariable", "jobsharedvariable", "dataformat", "aliaslib", "contextResource", 
            "serviceagent", "lock", "transform", "sharednotify", "oauthshared", "sharedpartner", 
            "httpProxy", "wmqprops", "wmqbody", "sharedxatmconfig", 
            "tasklist", "xslt", "wsdl", "xsd", "dtd", "cpy", "adapter", "archive", "wadl",
            "adr3", "adr3TID", "adfiles", "addb", "adldap", "adsbl", "adps", "adoa", "admq", "adtux", "adcics"
        };
        List<ResourceInfo> allResources = new ArrayList<>();
        try {
            for (String ext : extensions) {
                List<Path> files = FileUtils.findFilesByExtension(projectPath, ext);
                for (Path file : files) {
                    try {
                        String name = file.getFileName().toString();
                        String relPath = projectPath.relativize(file).toString().replace("\\", "/");
                        String type = determineResourceType(ext);
                        Map<String, String> config = new HashMap<>();
                        try {
                            Document doc = XmlUtils.parseXmlFile(file);
                            Element root = doc.getDocumentElement();
                            parseGenericConfig(root, config, "");
                        } catch (Exception e) {
                            config.put("fileSize", String.valueOf(java.nio.file.Files.size(file)));
                            config.put("parseError", "Could not parse content: " + e.getMessage());
                        }
                        ResourceInfo resource = new ResourceInfo(name, type, relPath, config);
                        allResources.add(resource);
                    } catch (Exception e) {
                        logger.error("Error processing resource file: " + file, e);
                    }
                }
            }
            allResources.sort(java.util.Comparator.comparing(ResourceInfo::getType)
                .thenComparing(ResourceInfo::getName));
            result.setResources(allResources);
            logger.info("Found and parsed {} resources/connections", allResources.size());
        } catch (IOException e) {
            logger.error("Error searching for resources", e);
        }
    }
    private String determineResourceType(String ext) {
        switch (ext) {
            case "sharedhttp": return "HTTP Connection";
            case "sharedjdbc": return "JDBC Connection";
            case "sharedjmscon": return "JMS Connection";
            case "id": return "Identity/Cert";
            case "mqcon": return "WebSphere MQ Connection";
            case "sharedsalesforce": return "Salesforce Connection";
            case "sharedtcp": return "TCP Connection";
            case "sharedftp": return "FTP Connection";
            case "rvtransport": return "Rendezvous Transport";
            case "mftsharedconn": return "MFT Connection";
            case "sharedvariable": return "Shared Variable";
            case "jobsharedvariable": return "Job Shared Variable";
            case "dataformat": return "Data Format";
            case "aliaslib": return "Alias Library";
            case "serviceagent": return "Service Agent Config";
            case "lock": return "Shared Lock";
            case "wsdl": return "WSDL";
            case "xsd": return "XSD Schema";
            case "adapter": return "Adapter Configuration";
            case "archive": return "Archive Info";
            case "wadl": return "WADL Resource";
            case "adr3": return "R3 Adapter Configuration";
            case "adr3TID": return "R3 TIDManager Configuration";
            case "adfiles": return "File Adapter Configuration";
            case "addb": return "Database Adapter Configuration";
            case "adldap": return "LDAP Adapter Configuration";
            case "adsbl": return "Siebel Adapter Configuration";
            case "adps": return "PeopleSoft Adapter Configuration";
            case "adoa": return "Oracle Applications Adapter Configuration";
            case "admq": return "MQ Adapter Configuration";
            case "adtux": return "Tuxedo Adapter Configuration";
            case "adcics": return "CICS Adapter Configuration";
            default: 
                if (ext.endsWith("shared")) return "Shared Configuration";
                return ext.toUpperCase() + " Resource";
        }
    }
    private void parseCommonConfig(Element configElement, Map<String, String> resourceInfo) {
        Element persistentElement = XmlUtils.getFirstChildElement(configElement, "persistent");
        if (persistentElement != null) {
            resourceInfo.put("persistent", XmlUtils.getElementText(persistentElement));
        }
        Element multiEngineElement = XmlUtils.getFirstChildElement(configElement, "multi-engine");
        if (multiEngineElement != null) {
            resourceInfo.put("multiEngine", XmlUtils.getElementText(multiEngineElement));
        }
    }
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "no-extension";
    }
    private void parseGenericConfig(Element element, Map<String, String> targetMap, String prefix) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element childParam = (Element) node;
                String key = childParam.getTagName();
                String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
                boolean hasChildElements = false;
                NodeList subChildren = childParam.getChildNodes();
                for (int j = 0; j < subChildren.getLength(); j++) {
                    if (subChildren.item(j).getNodeType() == Node.ELEMENT_NODE) {
                        hasChildElements = true;
                        break;
                    }
                }
                if (hasChildElements) {
                    parseGenericConfig(childParam, targetMap, fullKey);
                } else {
                    String value = XmlUtils.getElementText(childParam);
                    if (value != null && !value.trim().isEmpty()) {
                        String resolvedValue = resolveGV(value);
                        targetMap.put(fullKey, resolvedValue);
                    }
                    if (childParam.hasAttributes()) {
                        NamedNodeMap attrs = childParam.getAttributes();
                        for (int k = 0; k < attrs.getLength(); k++) {
                            Node attr = attrs.item(k);
                            String attrName = attr.getNodeName();
                            String attrValue = resolveGV(attr.getNodeValue());
                            if (attrValue != null && !attrValue.isEmpty()) {
                                if ("select".equals(attrName)) {
                                    targetMap.put(fullKey, attrValue);
                                } else {
                                    targetMap.put(fullKey + "/@" + attrName, attrValue);
                                }
                            }
                        }
                    }
                }
            } else if (node.getNodeType() == Node.TEXT_NODE) {
                String text = node.getTextContent().trim();
                if (!text.isEmpty()) {
                    String resolvedValue = resolveGV(text);
                    targetMap.put(prefix, resolvedValue);
                }
            }
        }
    }
}
