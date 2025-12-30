package com.raks.raksanalyzer.analyzer.mule;
import com.raks.raksanalyzer.core.analyzer.JsonAnalyzer;
import com.raks.raksanalyzer.core.analyzer.PomAnalyzer;
import com.raks.raksanalyzer.core.analyzer.ProjectCrawler;
import com.raks.raksanalyzer.core.config.ConfigurationManager;
import com.raks.raksanalyzer.core.connection.ConnectorDetailExtractor;
import com.raks.raksanalyzer.core.connection.ConnectorDetailExtractorFactory;
import com.raks.raksanalyzer.core.utils.ConfigResolver;
import com.raks.raksanalyzer.core.utils.PropertyResolver;
import com.raks.raksanalyzer.core.utils.XmlUtils;
import com.raks.raksanalyzer.domain.enums.ProjectType;
import com.raks.raksanalyzer.domain.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
public class MuleAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(MuleAnalyzer.class);
    private final Path projectPath;
    private final List<String> environments;
    private final Path externalConfigFile;
    private final ConfigurationManager config = ConfigurationManager.getInstance();
    private final ProjectCrawler crawler;
    private final PomAnalyzer pomAnalyzer;
    private final JsonAnalyzer jsonAnalyzer;
    private final Set<String> configTags;
    private final Set<String> operationTags;
    public MuleAnalyzer(Path projectPath, List<String> environments) {
        this(projectPath, environments, null);
    }
    public MuleAnalyzer(Path projectPath, List<String> environments, Path externalConfigFile) {
        this.projectPath = projectPath;
        this.environments = environments;
        this.externalConfigFile = externalConfigFile;
        this.crawler = new ProjectCrawler();
        this.pomAnalyzer = new PomAnalyzer();
        this.jsonAnalyzer = new JsonAnalyzer();
        this.configTags = loadTags("analyzer.mule.connector.config.tags");
        this.operationTags = loadTags("analyzer.mule.connector.operation.tags");
    }
    private Set<String> loadTags(String propKey) {
        String val = config.getProperty(propKey, "");
        if (val.isEmpty()) return new HashSet<>();
        return new HashSet<>(Arrays.asList(val.split(",")));
    }
    public AnalysisResult analyze() {
        logger.info("Starting Folder-Based Mule Analysis: {}", projectPath);
        AnalysisResult result = new AnalysisResult();
        result.setAnalysisId(UUID.randomUUID().toString());
        result.setStartTime(java.time.LocalDateTime.now());
        try {
            ProjectNode root = crawler.crawl(projectPath);
            result.setProjectStructure(root);
            ProjectInfo info = new ProjectInfo();
            info.setProjectPath(projectPath.toAbsolutePath().toString());
            info.setProjectName(projectPath.getFileName().toString());
            info.setProjectType(ProjectType.MULE);
            result.setProjectInfo(info);
            processNode(root, result);
            parseProperties(result);
            enrichConnectionDetails(result);
            resolveConnectorConfigProperties(result);
            result.setSuccess(true);
            logger.info("Analysis completed successfully");
        } catch (Exception e) {
            logger.error("Analysis failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        } finally {
            result.setEndTime(java.time.LocalDateTime.now());
        }
        return result;
    }
    private void processNode(ProjectNode node, AnalysisResult result) {
        if (node.getType() == ProjectNode.NodeType.DIRECTORY) {
            for (ProjectNode child : node.getChildren()) {
                processNode(child, result);
            }
        } else {
            String filename = node.getName();
            if (filename.equals("pom.xml")) {
                analyzePom(node, result);
            } else if (filename.equals("releaseinfo.json")) {
                analyzeJson(node, result);
            } else if (filename.endsWith(".xml")) {
                if (node.getAbsolutePath().contains("src" + java.io.File.separator + "main" + java.io.File.separator + "mule") ||
                    node.getAbsolutePath().contains("src/main/mule")) {
                    analyzeMuleXml(node, result);
                }
            }
        }
    }
    private void analyzePom(ProjectNode node, AnalysisResult result) {
        PomInfo info = pomAnalyzer.analyze(Paths.get(node.getAbsolutePath()));
        if (info != null) {
            node.addMetadata("pomInfo", info);
            ProjectInfo proj = result.getProjectInfo();
            if (proj.getProjectName() == null || proj.getProjectName().equals(projectPath.getFileName().toString())) {
                 if (info.getArtifactId() != null) proj.setProjectName(info.getArtifactId());
            }
            if (info.getVersion() != null) proj.setVersion(info.getVersion());
            if (info.getDescription() != null) proj.setDescription(info.getDescription());
        }
    }
    private void analyzeJson(ProjectNode node, AnalysisResult result) {
        Map<String, Object> data = jsonAnalyzer.analyze(new java.io.File(node.getAbsolutePath()));
        if (data != null) {
            node.addMetadata("releaseInfo", data);
            ProjectInfo proj = result.getProjectInfo();
        }
    }
    private void analyzeMuleXml(ProjectNode node, AnalysisResult result) {
        try {
            Document doc = XmlUtils.parseXmlFile(Paths.get(node.getAbsolutePath()));
            Element root = doc.getDocumentElement();
            if (!root.getNodeName().equals("mule")) {
                return; 
            }
            List<FlowInfo> flows = new ArrayList<>();
            List<ConnectorConfig> configs = new ArrayList<>();
            NodeList children = root.getChildNodes();
            for (int i=0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() != Node.ELEMENT_NODE) continue;
                Element el = (Element) child;
                String tagName = XmlUtils.getLocalName(el); 
                String fullTagName = el.getTagName(); 
                if (fullTagName.endsWith("flow")) { 
                    FlowInfo flow = parseFlow(el, Paths.get(node.getAbsolutePath()));
                    flow.setFileName(node.getName());
                    flows.add(flow);
                    result.getFlows().add(flow); 
                } else if (isConfigTag(fullTagName)) {
                    ConnectorConfig cfg = parseConfig(el);
                    configs.add(cfg);
                }
            }
            node.addMetadata("muleFlows", flows);
            node.addMetadata("muleConfigs", configs);
        } catch (Exception e) {
            logger.error("Error parsing Mule XML: {}", node.getName(), e);
        }
    }
    private boolean isConfigTag(String tagName) {
        if (configTags.contains(tagName)) {
            return true;
        }
        return tagName.endsWith("-config") || tagName.endsWith(":config");
    }
    private ConnectorConfig parseConfig(Element el) {
        ConnectorConfig cfg = new ConnectorConfig();
        String type = el.getTagName();
        String name = el.getAttribute("name");
        cfg.setType(type);
        cfg.setName(name != null && !name.isEmpty() ? name : type);
        org.w3c.dom.NamedNodeMap attrs = el.getAttributes();
        for(int k=0; k<attrs.getLength(); k++) {
            Node attr = attrs.item(k);
            cfg.addAttribute(attr.getNodeName(), attr.getNodeValue());
        }
        parseNestedConfigElements(el, cfg, 0);
        return cfg;
    }
    private void parseNestedConfigElements(Element element, ConnectorConfig config, int depth) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) node;
                String tagName = el.getTagName();
                if (tagName.startsWith("doc:")) continue;
                ComponentInfo comp = new ComponentInfo();
                comp.setType(tagName);
                comp.setName(el.getAttribute("name"));
                comp.setCategory("Configuration");
                org.w3c.dom.NamedNodeMap attrs = el.getAttributes();
                for(int k=0; k<attrs.getLength(); k++) {
                    Node attr = attrs.item(k);
                    comp.addAttribute(attr.getNodeName(), attr.getNodeValue());
                }
                comp.addAttribute("_depth", String.valueOf(depth));
                String textContent = extractTextContent(el);
                if (textContent != null && !textContent.trim().isEmpty()) {
                    comp.addAttribute("_content", textContent.trim());
                }
                config.addNestedComponent(comp);
                parseNestedConfigElementsRecursive(el, config, depth + 1);
            }
        }
    }
    private void parseNestedConfigElementsRecursive(Element element, ConnectorConfig config, int depth) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) node;
                String tagName = el.getTagName();
                if (tagName.startsWith("doc:")) continue;
                ComponentInfo comp = new ComponentInfo();
                comp.setType(tagName);
                comp.setName(el.getAttribute("name"));
                comp.setCategory("Configuration");
                org.w3c.dom.NamedNodeMap attrs = el.getAttributes();
                for(int k=0; k<attrs.getLength(); k++) {
                    Node attr = attrs.item(k);
                    comp.addAttribute(attr.getNodeName(), attr.getNodeValue());
                }
                comp.addAttribute("_depth", String.valueOf(depth));
                String textContent = extractTextContent(el);
                if (textContent != null && !textContent.trim().isEmpty()) {
                    comp.addAttribute("_content", textContent.trim());
                }
                config.addNestedComponent(comp);
                parseNestedConfigElementsRecursive(el, config, depth + 1);
            }
        }
    }
    private FlowInfo parseFlow(Element flowElement, Path xmlFile) {
        FlowInfo flowInfo = new FlowInfo();
        String name = XmlUtils.getAttributeValue(flowElement, "name");
        flowInfo.setName(name);
        flowInfo.setType(flowElement.getLocalName()); 
        parseComponents(flowElement, flowInfo);
        return flowInfo;
    }
    private void parseComponents(Element flowElement, FlowInfo flowInfo) {
        parseComponentsRecursive(flowElement, flowInfo, 0, null);
    }
    private void parseComponentsRecursive(Element element, FlowInfo flowInfo, int depth, ComponentInfo parent) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) node;
                String tagName = el.getTagName();
                if (tagName.startsWith("doc:")) continue;
                ComponentInfo comp = new ComponentInfo();
                comp.setType(tagName);
                comp.setName(el.getAttribute("name"));
                if (operationTags.contains(tagName)) comp.setCategory("Operation");
                else if (isConfigTag(tagName)) comp.setCategory("Configuration");
                else comp.setCategory("Component");
                if (el.hasAttribute("config-ref")) {
                    comp.setConfigRef(el.getAttribute("config-ref"));
                }
                org.w3c.dom.NamedNodeMap attrs = el.getAttributes();
                for(int k=0; k<attrs.getLength(); k++) {
                    Node attr = attrs.item(k);
                    comp.addAttribute(attr.getNodeName(), attr.getNodeValue());
                }
                comp.addAttribute("_depth", String.valueOf(depth));
                String textContent = extractTextContent(el);
                logger.debug("Element: {}, hasContent: {}, contentLength: {}", 
                    tagName, textContent != null && !textContent.trim().isEmpty(), 
                    textContent != null ? textContent.length() : 0);
                if (textContent != null && !textContent.trim().isEmpty()) {
                    comp.addAttribute("_content", textContent.trim());
                    logger.debug("Added content for {}: {}", tagName, textContent.substring(0, Math.min(50, textContent.length())));
                }
                flowInfo.addComponent(comp); 
                if (parent != null) {
                    parent.addChild(comp);
                }
                parseComponentsRecursive(el, flowInfo, depth + 1, comp);
            }
        }
    }
    private String extractTextContent(Element element) {
        StringBuilder content = new StringBuilder();
        NodeList nodes = element.getChildNodes();
        boolean hasCDATA = false;
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.CDATA_SECTION_NODE) {
                if (!hasCDATA) {
                    content.append("<![CDATA[");
                    hasCDATA = true;
                }
                String text = node.getNodeValue();
                if (text != null) {
                    content.append(text);
                }
            } else if (node.getNodeType() == Node.TEXT_NODE) {
                String text = node.getNodeValue();
                if (text != null && !text.trim().isEmpty()) {
                    content.append(text);
                }
            }
        }
        if (hasCDATA) {
            content.append("]]>");
        }
        return content.toString();
    }
    private void parseProperties(AnalysisResult result) {
         List<ProjectNode> propFiles = new ArrayList<>();
         collectPropertiesFiles(result.getProjectStructure(), propFiles);
         logger.info("Found {} properties/yaml files to parse", propFiles.size());
         for (ProjectNode propFile : propFiles) {
             String relative = propFile.getRelativePath();
             logger.debug("Parsing properties file: {} (relative: {})", propFile.getName(), relative);
             result.getPropertyFiles().add(relative);
             parsePropertiesFile(Paths.get(propFile.getAbsolutePath()), relative, result);
         }
         logger.info("Total properties parsed: {}", result.getProperties().size());
    }
    private void collectPropertiesFiles(ProjectNode node, List<ProjectNode> result) {
        if (node.getType() == ProjectNode.NodeType.FILE) {
            String name = node.getName();
            if (name.endsWith(".properties") || name.endsWith(".yaml") || name.endsWith(".yml")) {
                result.add(node);
            }
        } else if (node.getType() == ProjectNode.NodeType.DIRECTORY) {
            for (ProjectNode child : node.getChildren()) {
                collectPropertiesFiles(child, result);
            }
        }
    }
    private void parsePropertiesFile(Path file, String relative, AnalysisResult result) {
        try {
            Properties props = new Properties();
            if (file.toString().endsWith(".properties")) {
                props.load(Files.newInputStream(file));
                logger.debug("Loaded {} properties from file: {}", props.size(), file.getFileName());
                for(String key : props.stringPropertyNames()) {
                    PropertyInfo info = findOrCreatePropertyInfo(result, key);
                    String value = props.getProperty(key);
                    if (info.getSourcePath() == null || info.getSourcePath().isEmpty()) {
                        info.setSourcePath(relative);
                    }
                    info.addEnvironmentValue(relative, value);
                    logger.trace("Added property: {} = {} (file: {})", key, value, relative);
                }
            }
        } catch(Exception e) {
            logger.error("Error parsing prop file: " + file, e);
        }
    }
    private PropertyInfo findOrCreatePropertyInfo(AnalysisResult result, String key) {
        for(PropertyInfo p : result.getProperties()) {
            if(p.getKey().equals(key)) return p;
        }
        PropertyInfo n = new PropertyInfo();
        n.setKey(key);
        result.getProperties().add(n);
        return n;
    }
    private void enrichConnectionDetails(AnalysisResult result) {
        try {
            boolean enabled = Boolean.parseBoolean(
                config.getProperty("diagram.integration.show.connection.details", "true")
            );
            if (!enabled) {
                logger.info("Connection details enrichment is disabled");
                return;
            }
            logger.info("Starting connection details enrichment");
            PropertyResolver propertyResolver = new PropertyResolver();
            int propsLoaded = 0;
            String propertyPattern = config.getProperty(
                "diagram.integration.property.files", 
                "src/main/resources/*.properties"
            );
            propsLoaded = propertyResolver.loadProperties(projectPath, propertyPattern);
            logger.info("Loaded {} property files from project", propsLoaded);
            if (externalConfigFile != null && Files.exists(externalConfigFile)) {
                logger.info("Loading external config file to override project properties: {}", externalConfigFile);
                int externalPropsLoaded = propertyResolver.loadPropertiesFromFile(externalConfigFile);
                logger.info("Loaded {} properties from external config file (overriding project properties)", externalPropsLoaded);
            }
            ConfigResolver configResolver = new ConfigResolver();
            collectAllConfigs(result.getProjectStructure(), configResolver);
            logger.info("Loaded {} connector configs", configResolver.getConfigCount());
            Properties configProps = new Properties();
            config.getAllProperties().forEach(configProps::put);
            ConnectorDetailExtractorFactory extractorFactory = 
                new ConnectorDetailExtractorFactory(configProps);
            enrichFlowComponents(result.getProjectStructure(), configResolver, propertyResolver, extractorFactory);
            logger.info("Connection details enrichment completed");
        } catch (Exception e) {
            logger.error("Failed to enrich connection details", e);
        }
    }
    private void collectAllConfigs(ProjectNode node, ConfigResolver resolver) {
        if (node == null) return;
        if (node.getType() == ProjectNode.NodeType.FILE) {
            Object configs = node.getMetadata("muleConfigs");
            if (configs instanceof List) {
                for (Object o : (List<?>)configs) {
                    if (o instanceof ConnectorConfig) {
                        resolver.addConfig((ConnectorConfig)o);
                    }
                }
            }
        }
        if (node.getChildren() != null) {
            for (ProjectNode child : node.getChildren()) {
                collectAllConfigs(child, resolver);
            }
        }
    }
    private void enrichFlowComponents(ProjectNode node, ConfigResolver configResolver, 
                                     PropertyResolver propertyResolver, 
                                     ConnectorDetailExtractorFactory extractorFactory) {
        if (node == null) return;
        if (node.getType() == ProjectNode.NodeType.FILE) {
            Object flows = node.getMetadata("muleFlows");
            if (flows instanceof List) {
                for (Object o : (List<?>)flows) {
                    if (o instanceof FlowInfo) {
                        FlowInfo flow = (FlowInfo)o;
                        if (flow.getComponents() != null) {
                            for (ComponentInfo comp : flow.getComponents()) {
                                enrichComponent(comp, configResolver, propertyResolver, extractorFactory);
                            }
                        }
                    }
                }
            }
        }
        if (node.getChildren() != null) {
            for (ProjectNode child : node.getChildren()) {
                enrichFlowComponents(child, configResolver, propertyResolver, extractorFactory);
            }
        }
    }
    private void enrichComponent(ComponentInfo comp, ConfigResolver configResolver,
                                PropertyResolver propertyResolver,
                                ConnectorDetailExtractorFactory extractorFactory) {
        if (comp == null) return;
        if (comp.getConfigRef() != null && !comp.getConfigRef().isEmpty()) {
            logger.debug("Component {} has config-ref: {}", comp.getType(), comp.getConfigRef());
            ConnectorConfig config = configResolver.resolveConfig(comp.getConfigRef());
            if (config != null) {
                logger.debug("Found config: {} (type: {})", config.getName(), config.getType());
                ConnectorDetailExtractor extractor = extractorFactory.getExtractor(config.getType());
                String details = extractor.extractDetails(config, comp, propertyResolver);
                if (details != null && !details.isEmpty()) {
                    comp.setConnectionDetails(details);
                    logger.debug("Enriched {} with details: {}", comp.getType(), details);
                }
            } else {
                logger.warn("Config not found for config-ref: {}", comp.getConfigRef());
            }
        }
        if (comp.getChildren() != null) {
            for (ComponentInfo child : comp.getChildren()) {
                enrichComponent(child, configResolver, propertyResolver, extractorFactory);
            }
        }
    }
    private void resolveConnectorConfigProperties(AnalysisResult result) {
        try {
            logger.info("Resolving properties in connector configurations");
            PropertyResolver propertyResolver = new PropertyResolver();
            String propertyPattern = config.getProperty(
                "diagram.integration.property.files", 
                "src/main/resources/*.properties"
            );
            int propsLoaded = propertyResolver.loadProperties(projectPath, propertyPattern);
            logger.info("Loaded {} properties from pattern: {}", propsLoaded, propertyPattern);
            if (propsLoaded == 0) {
                propsLoaded = propertyResolver.loadProperties(projectPath, "classes/*.properties");
                logger.info("Loaded {} properties from classes/ directory", propsLoaded);
            }
            if (propsLoaded == 0) {
                propsLoaded = propertyResolver.loadProperties(projectPath, "*.properties");
                logger.info("Loaded {} properties from root directory", propsLoaded);
            }
            if (externalConfigFile != null && Files.exists(externalConfigFile)) {
                logger.info("Loading external config to override properties: {}", externalConfigFile);
                int externalPropsLoaded = propertyResolver.loadPropertiesFromFile(externalConfigFile);
                logger.info("Loaded {} properties from external config file", externalPropsLoaded);
            }
            for (PropertyInfo propInfo : result.getProperties()) {
                String key = propInfo.getKey();
                String resolvedValue = propertyResolver.getProperty(key);
                if (resolvedValue != null) {
                    for (String envKey : new ArrayList<>(propInfo.getEnvironmentValues().keySet())) {
                        propInfo.addEnvironmentValue(envKey, resolvedValue);
                    }
                }
            }
            resolveConnectorConfigAttributesRecursive(result.getProjectStructure(), propertyResolver);
            logger.info("Property resolution completed");
        } catch (Exception e) {
            logger.error("Failed to resolve connector config properties", e);
        }
    }
    private void resolveConnectorConfigAttributesRecursive(ProjectNode node, PropertyResolver propertyResolver) {
        if (node == null) return;
        if (node.getType() == ProjectNode.NodeType.FILE) {
            Object configs = node.getMetadata("muleConfigs");
            if (configs instanceof List) {
                for (Object o : (List<?>)configs) {
                    if (o instanceof ConnectorConfig) {
                        ConnectorConfig config = (ConnectorConfig)o;
                        resolveConfigAttributes(config, propertyResolver);
                    }
                }
            }
        }
        if (node.getChildren() != null) {
            for (ProjectNode child : node.getChildren()) {
                resolveConnectorConfigAttributesRecursive(child, propertyResolver);
            }
        }
    }
    private void resolveConfigAttributes(ConnectorConfig config, PropertyResolver propertyResolver) {
        if (config.getAttributes() != null) {
            for (Map.Entry<String, String> entry : config.getAttributes().entrySet()) {
                String value = entry.getValue();
                if (value != null && (value.contains("${") || value.contains("p('"))) {
                    String resolved = propertyResolver.resolve(value);
                    config.addAttribute(entry.getKey(), resolved);
                }
            }
        }
        if (config.getNestedComponents() != null) {
            for (ComponentInfo comp : config.getNestedComponents()) {
                resolveComponentAttributes(comp, propertyResolver);
            }
        }
    }
    private void resolveComponentAttributes(ComponentInfo comp, PropertyResolver propertyResolver) {
        if (comp.getAttributes() != null) {
            for (Map.Entry<String, String> entry : comp.getAttributes().entrySet()) {
                String value = entry.getValue();
                if (value != null && (value.contains("${") || value.contains("p('"))) {
                    String resolved = propertyResolver.resolve(value);
                    comp.addAttribute(entry.getKey(), resolved);
                }
            }
        }
        if (comp.getChildren() != null) {
            for (ComponentInfo child : comp.getChildren()) {
                resolveComponentAttributes(child, propertyResolver);
            }
        }
    }
}
