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

/**
 * Analyzer for Mule 4.x projects.
 * Updated to support folder-based analysis and specific Tag identification.
 */
public class MuleAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(MuleAnalyzer.class);
    
    private final Path projectPath;
    private final List<String> environments;
    private final Path externalConfigFile;
    private final ConfigurationManager config = ConfigurationManager.getInstance();
    
    // Analyzers
    private final ProjectCrawler crawler;
    private final PomAnalyzer pomAnalyzer;
    private final JsonAnalyzer jsonAnalyzer;
    
    // Configured Tags
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
        
        // Load tags
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
            // 1. Crawl Project Structure
            ProjectNode root = crawler.crawl(projectPath);
            result.setProjectStructure(root);
            
            // 2. Initialize Project Info
            ProjectInfo info = new ProjectInfo();
            info.setProjectPath(projectPath.toAbsolutePath().toString());
            info.setProjectName(projectPath.getFileName().toString());
            info.setProjectType(ProjectType.MULE);
            result.setProjectInfo(info);
            
            // 3. Process the entire tree
            processNode(root, result);
            
            // 4. Legacy: Properties Parsing (still useful to do global crawl for properties)
            // But actually we should parse them via the tree now. 
            // We will stick to the existing global property parsing for robustness as "src/main/resources" is standard.
            parseProperties(result);
            
            // 5. Enrich components with connection details (if enabled)
            enrichConnectionDetails(result);
            
            // 6. Resolve properties in connector configurations
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
            // It's a file
            String filename = node.getName();
            
            if (filename.equals("pom.xml")) {
                analyzePom(node, result);
            } else if (filename.equals("releaseinfo.json")) {
                analyzeJson(node, result);
            } else if (filename.endsWith(".xml")) {
                // Check if it's a Mule XML (simple heuristic: contains <mule>)
                // Or just try to parse it if in known folder.
                // We will attempt parse on ALL xmls in source folders.
                // But specifically src/main/mule is the target for flows.
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
            // Enrich global project info
            ProjectInfo proj = result.getProjectInfo();
            if (proj.getProjectName() == null || proj.getProjectName().equals(projectPath.getFileName().toString())) {
                 if (info.getArtifactId() != null) proj.setProjectName(info.getArtifactId());
            }
            if (info.getVersion() != null) proj.setVersion(info.getVersion());
            // Add description if available
            if (info.getDescription() != null) proj.setDescription(info.getDescription());
        }
    }
    
    private void analyzeJson(ProjectNode node, AnalysisResult result) {
        Map<String, Object> data = jsonAnalyzer.analyze(new java.io.File(node.getAbsolutePath()));
        if (data != null) {
            node.addMetadata("releaseInfo", data);
            // Enrich project info if keys exist
            ProjectInfo proj = result.getProjectInfo();
            // TODO: Map specific JSON keys to ProjectInfo if we expand ProjectInfo fields
        }
    }
    
    private void analyzeMuleXml(ProjectNode node, AnalysisResult result) {
        try {
            Document doc = XmlUtils.parseXmlFile(Paths.get(node.getAbsolutePath()));
            Element root = doc.getDocumentElement();
            if (!root.getNodeName().equals("mule")) {
                return; // Not a mule file
            }
            
            List<FlowInfo> flows = new ArrayList<>();
            List<ConnectorConfig> configs = new ArrayList<>();
            
            // Scan children
            NodeList children = root.getChildNodes();
            for (int i=0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() != Node.ELEMENT_NODE) continue;
                
                Element el = (Element) child;
                String tagName = XmlUtils.getLocalName(el); // e.g. "flow" or "listener-config"
                // Need full tag name potentially if namespace prefix involved? 
                // XmlUtils.getTagName() gives prefix:local. 
                // My config properties usually use "prefix:local" format (e.g. http:listener-config).
                String fullTagName = el.getTagName(); 
                
                if (fullTagName.endsWith("flow")) { // flow or sub-flow
                    FlowInfo flow = parseFlow(el, Paths.get(node.getAbsolutePath()));
                    flow.setFileName(node.getName());
                    flows.add(flow);
                    result.getFlows().add(flow); // Global list backward compatibility
                } else if (isConfigTag(fullTagName)) {
                    ConnectorConfig cfg = parseConfig(el);
                    configs.add(cfg);
                    // Add to global components? No, distinct concept.
                }
            }
            
            node.addMetadata("muleFlows", flows);
            node.addMetadata("muleConfigs", configs);
            
        } catch (Exception e) {
            logger.error("Error parsing Mule XML: {}", node.getName(), e);
        }
    }
    
    private boolean isConfigTag(String tagName) {
        // Check explicit list first
        if (configTags.contains(tagName)) {
            return true;
        }
        
        // Check for common config patterns:
        // - http:listener-config (ends with -config)
        // - ibm-mq:config (ends with :config)
        // - db:config (ends with :config)
        return tagName.endsWith("-config") || tagName.endsWith(":config");
    }
    
    private ConnectorConfig parseConfig(Element el) {
        ConnectorConfig cfg = new ConnectorConfig();
        String type = el.getTagName();
        String name = el.getAttribute("name");
        
        cfg.setType(type);
        // If name is empty, use type as fallback
        cfg.setName(name != null && !name.isEmpty() ? name : type);
        
        // Attributes
        org.w3c.dom.NamedNodeMap attrs = el.getAttributes();
        for(int k=0; k<attrs.getLength(); k++) {
            Node attr = attrs.item(k);
            cfg.addAttribute(attr.getNodeName(), attr.getNodeValue());
        }
        
        // Parse nested elements (like email:smtp-connection under email:smtp-config)
        parseNestedConfigElements(el, cfg, 0);
        
        return cfg;
    }
    
    /**
     * Recursively parse nested elements within a connector config.
     */
    private void parseNestedConfigElements(Element element, ConnectorConfig config, int depth) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) node;
                String tagName = el.getTagName();
                
                // Skip documentation elements
                if (tagName.startsWith("doc:")) continue;
                
                ComponentInfo comp = new ComponentInfo();
                comp.setType(tagName);
                comp.setName(el.getAttribute("name"));
                comp.setCategory("Configuration");
                
                // Attributes
                org.w3c.dom.NamedNodeMap attrs = el.getAttributes();
                for(int k=0; k<attrs.getLength(); k++) {
                    Node attr = attrs.item(k);
                    comp.addAttribute(attr.getNodeName(), attr.getNodeValue());
                }
                
                // Store depth
                comp.addAttribute("_depth", String.valueOf(depth));
                
                // Capture CDATA content
                String textContent = extractTextContent(el);
                if (textContent != null && !textContent.trim().isEmpty()) {
                    comp.addAttribute("_content", textContent.trim());
                }
                
                config.addNestedComponent(comp);
                
                // Recursively parse nested elements
                parseNestedConfigElementsRecursive(el, config, depth + 1);
            }
        }
    }
    
    /**
     * Helper to continue recursive parsing of nested config elements.
     */
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

    // Existing methods optimized (parseFlow, parseComponents, parseProperties)
    // NOTE: parseFlow needs to identify Operations using the new operationTags set.
    
    private FlowInfo parseFlow(Element flowElement, Path xmlFile) {
        FlowInfo flowInfo = new FlowInfo();
        String name = XmlUtils.getAttributeValue(flowElement, "name");
        flowInfo.setName(name);
        flowInfo.setType(flowElement.getLocalName()); // flow or sub-flow
        
        parseComponents(flowElement, flowInfo);
        
        return flowInfo;
    }
    
    private void parseComponents(Element flowElement, FlowInfo flowInfo) {
        parseComponentsRecursive(flowElement, flowInfo, 0, null);
    }
    
    /**
     * Recursively parse components and their nested children.
     * @param element Parent element
     * @param flowInfo Flow to add components to (flat list)
     * @param depth Current nesting depth (for hierarchy)
     * @param parent Parent component (for tree structure)
     */
    private void parseComponentsRecursive(Element element, FlowInfo flowInfo, int depth, ComponentInfo parent) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) node;
                String tagName = el.getTagName();
                
                // Skip documentation elements
                if (tagName.startsWith("doc:")) continue;
                
                ComponentInfo comp = new ComponentInfo();
                comp.setType(tagName);
                comp.setName(el.getAttribute("name"));
                
                // Categorize
                if (operationTags.contains(tagName)) comp.setCategory("Operation");
                else if (isConfigTag(tagName)) comp.setCategory("Configuration");
                else comp.setCategory("Component");
                
                if (el.hasAttribute("config-ref")) {
                    comp.setConfigRef(el.getAttribute("config-ref"));
                }
                
                // Attributes
                org.w3c.dom.NamedNodeMap attrs = el.getAttributes();
                for(int k=0; k<attrs.getLength(); k++) {
                    Node attr = attrs.item(k);
                    comp.addAttribute(attr.getNodeName(), attr.getNodeValue());
                }
                
                // Store nesting depth for hierarchical display
                comp.addAttribute("_depth", String.valueOf(depth));
                
                // Capture CDATA or text content
                String textContent = extractTextContent(el);
                logger.debug("Element: {}, hasContent: {}, contentLength: {}", 
                    tagName, textContent != null && !textContent.trim().isEmpty(), 
                    textContent != null ? textContent.length() : 0);
                
                if (textContent != null && !textContent.trim().isEmpty()) {
                    comp.addAttribute("_content", textContent.trim());
                    logger.debug("Added content for {}: {}", tagName, textContent.substring(0, Math.min(50, textContent.length())));
                }
                
                flowInfo.addComponent(comp); // Add to flat list
                
                // Add to parent for tree structure
                if (parent != null) {
                    parent.addChild(comp);
                }
                
                // Recursively parse nested components, passing 'comp' as the new parent
                parseComponentsRecursive(el, flowInfo, depth + 1, comp);
            }
        }
    }
    
    /**
     * Extract text content including CDATA from an element.
     * Preserves CDATA markers to show exact XML content.
     */
    private String extractTextContent(Element element) {
        StringBuilder content = new StringBuilder();
        NodeList nodes = element.getChildNodes();
        boolean hasCDATA = false;
        
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.CDATA_SECTION_NODE) {
                // Preserve CDATA markers
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
        
        // Close CDATA if we opened it
        if (hasCDATA) {
            content.append("]]>");
        }
        
        return content.toString();
    }
    
    // Legacy properties parsing logic (kept as is for now)
    private void parseProperties(AnalysisResult result) {
         // Instead of hardcoding src/main/resources, search within the crawled structure
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
                    
                    // Set source path if not already set
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

    /**
     * Enriches components with connection details by resolving config-ref and properties.
     */
    private void enrichConnectionDetails(AnalysisResult result) {
        try {
            // Check if feature is enabled
            boolean enabled = Boolean.parseBoolean(
                config.getProperty("diagram.integration.show.connection.details", "true")
            );
            
            if (!enabled) {
                logger.info("Connection details enrichment is disabled");
                return;
            }
            
            logger.info("Starting connection details enrichment");
            
            // 1. Load properties from project first (base properties)
            PropertyResolver propertyResolver = new PropertyResolver();
            int propsLoaded = 0;
            
            // Always load project properties as base
            String propertyPattern = config.getProperty(
                "diagram.integration.property.files", 
                "src/main/resources/*.properties"
            );
            propsLoaded = propertyResolver.loadProperties(projectPath, propertyPattern);
            logger.info("Loaded {} property files from project", propsLoaded);
            
            // If external config file is provided, load it to override project properties
            if (externalConfigFile != null && Files.exists(externalConfigFile)) {
                logger.info("Loading external config file to override project properties: {}", externalConfigFile);
                int externalPropsLoaded = propertyResolver.loadPropertiesFromFile(externalConfigFile);
                logger.info("Loaded {} properties from external config file (overriding project properties)", externalPropsLoaded);
            }
            
            // 2. Build config resolver from parsed connector configs
            ConfigResolver configResolver = new ConfigResolver();
            collectAllConfigs(result.getProjectStructure(), configResolver);
            logger.info("Loaded {} connector configs", configResolver.getConfigCount());
            
            // 3. Create extractor factory
            Properties configProps = new Properties();
            config.getAllProperties().forEach(configProps::put);
            ConnectorDetailExtractorFactory extractorFactory = 
                new ConnectorDetailExtractorFactory(configProps);
            
            // 4. Enrich all components in all flows
            enrichFlowComponents(result.getProjectStructure(), configResolver, propertyResolver, extractorFactory);
            
            logger.info("Connection details enrichment completed");
            
        } catch (Exception e) {
            logger.error("Failed to enrich connection details", e);
            // Don't fail the entire analysis, just log the error
        }
    }
    
    /**
     * Recursively collects all connector configs from project structure.
     */
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
    
    /**
     * Recursively enriches components in all flows.
     */
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
    
    /**
     * Recursively enriches a component and its children with connection details.
     */
    private void enrichComponent(ComponentInfo comp, ConfigResolver configResolver,
                                PropertyResolver propertyResolver,
                                ConnectorDetailExtractorFactory extractorFactory) {
        if (comp == null) return;
        
        // If component has config-ref, extract connection details
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
        
        // Recursively enrich children
        if (comp.getChildren() != null) {
            for (ComponentInfo child : comp.getChildren()) {
                enrichComponent(child, configResolver, propertyResolver, extractorFactory);
            }
        }
    }
    
    /**
     * Resolves properties in connector configurations and updates stored properties.
     */
    private void resolveConnectorConfigProperties(AnalysisResult result) {
        try {
            logger.info("Resolving properties in connector configurations");
            
            // 1. Load properties from project first (base properties)
            PropertyResolver propertyResolver = new PropertyResolver();
            
            // Try to load project properties from multiple possible locations
            // For source projects: src/main/resources/*.properties
            // For extracted JARs: classes/*.properties or *.properties
            String propertyPattern = config.getProperty(
                "diagram.integration.property.files", 
                "src/main/resources/*.properties"
            );
            int propsLoaded = propertyResolver.loadProperties(projectPath, propertyPattern);
            logger.info("Loaded {} properties from pattern: {}", propsLoaded, propertyPattern);
            
            // Also try classes/ pattern for JAR files
            if (propsLoaded == 0) {
                propsLoaded = propertyResolver.loadProperties(projectPath, "classes/*.properties");
                logger.info("Loaded {} properties from classes/ directory", propsLoaded);
            }
            
            // Also try root level for JAR files
            if (propsLoaded == 0) {
                propsLoaded = propertyResolver.loadProperties(projectPath, "*.properties");
                logger.info("Loaded {} properties from root directory", propsLoaded);
            }
            
            // If external config file is provided, load it to override project properties
            if (externalConfigFile != null && Files.exists(externalConfigFile)) {
                logger.info("Loading external config to override properties: {}", externalConfigFile);
                int externalPropsLoaded = propertyResolver.loadPropertiesFromFile(externalConfigFile);
                logger.info("Loaded {} properties from external config file", externalPropsLoaded);
            }
            
            // 2. Update stored properties in result with resolved values
            for (PropertyInfo propInfo : result.getProperties()) {
                String key = propInfo.getKey();
                String resolvedValue = propertyResolver.getProperty(key);
                
                if (resolvedValue != null) {
                    // Update all environment values with the resolved value
                    for (String envKey : new ArrayList<>(propInfo.getEnvironmentValues().keySet())) {
                        propInfo.addEnvironmentValue(envKey, resolvedValue);
                    }
                }
            }
            
            // 3. Resolve properties in connector config attributes
            resolveConnectorConfigAttributesRecursive(result.getProjectStructure(), propertyResolver);
            
            logger.info("Property resolution completed");
            
        } catch (Exception e) {
            logger.error("Failed to resolve connector config properties", e);
            // Don't fail the entire analysis
        }
    }
    
    /**
     * Recursively resolves properties in connector config attributes.
     */
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
    
    /**
     * Resolves properties in a connector config's attributes and nested components.
     */
    private void resolveConfigAttributes(ConnectorConfig config, PropertyResolver propertyResolver) {
        // Resolve attributes
        if (config.getAttributes() != null) {
            for (Map.Entry<String, String> entry : config.getAttributes().entrySet()) {
                String value = entry.getValue();
                if (value != null && (value.contains("${") || value.contains("p('"))) {
                    String resolved = propertyResolver.resolve(value);
                    config.addAttribute(entry.getKey(), resolved);
                }
            }
        }
        
        // Resolve nested component attributes
        if (config.getNestedComponents() != null) {
            for (ComponentInfo comp : config.getNestedComponents()) {
                resolveComponentAttributes(comp, propertyResolver);
            }
        }
    }
    
    /**
     * Resolves properties in a component's attributes recursively.
     */
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
        
        // Recursively resolve children
        if (comp.getChildren() != null) {
            for (ComponentInfo child : comp.getChildren()) {
                resolveComponentAttributes(child, propertyResolver);
            }
        }
    }

}
