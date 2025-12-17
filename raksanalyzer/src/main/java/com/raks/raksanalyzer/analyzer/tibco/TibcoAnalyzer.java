package com.raks.raksanalyzer.analyzer.tibco;

import com.raks.raksanalyzer.core.utils.FileUtils;
import com.raks.raksanalyzer.core.utils.XmlUtils;
import com.raks.raksanalyzer.domain.enums.ProjectType;
import com.raks.raksanalyzer.domain.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Analyzer for Tibco BusinessWorks 5.x projects.
 * 
 * Analyzes:
 * - .process files (business processes)
 * - Activities (JDBC, HTTP, transformers, etc.)
 * - Global variables from .substvar files
 * - Shared resources
 * - Schemas and WSDLs
 */
public class TibcoAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(TibcoAnalyzer.class);
    
    private final Path projectPath;
    private final List<String> environments;
    private final Map<String, Map<String, String>> globalVariables = new HashMap<>();
    
    public TibcoAnalyzer(Path projectPath, List<String> environments) {
        this.projectPath = projectPath;
        this.environments = environments;
    }
    
    /**
     * Analyze Tibco BW5 project and return results.
     */
    public AnalysisResult analyze() {
        logger.info("Starting Tibco BW5 project analysis: {}", projectPath);
        
        AnalysisResult result = new AnalysisResult();
        result.setAnalysisId(UUID.randomUUID().toString());
        result.setStartTime(java.time.LocalDateTime.now());
        
        try {
            // Extract project information
            ProjectInfo projectInfo = extractProjectInfo();
            result.setProjectInfo(projectInfo);
            
            // Parse global variables from .substvar files
            parseGlobalVariables();
            
            // Find and parse .process files
            List<Path> processFiles = findProcessFiles();
            logger.info("Found {} process files", processFiles.size());
            
            // Parse processes and activities
            for (Path processFile : processFiles) {
                parseProcess(processFile, result);
            }
            
            // Convert global variables to properties
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
    
    /**
     * Extract project information.
     */
    private ProjectInfo extractProjectInfo() {
        ProjectInfo info = new ProjectInfo();
        info.setProjectType(ProjectType.TIBCO_BW5);
        info.setProjectPath(projectPath.toString());
        info.setProjectName(projectPath.getFileName().toString());
        
        // Try to detect Tibco version from project files
        // This could be enhanced to read from .designtimelibs or other metadata files
        info.setTibcoVersion("5.x");
        
        return info;
    }
    
    /**
     * Find all .process files in the project.
     */
    private List<Path> findProcessFiles() throws IOException {
        return FileUtils.findFilesByExtension(projectPath, "process");
    }
    
    /**
     * Parse global variables from .substvar files.
     * Format: defaultVars/<VariableName>/defaultVars.substvar
     */
    private void parseGlobalVariables() {
        try {
            Path defaultVarsDir = projectPath.resolve("defaultVars");
            
            if (!Files.exists(defaultVarsDir)) {
                logger.warn("defaultVars directory not found: {}", defaultVarsDir);
                return;
            }
            
            // Find all .substvar files
            List<Path> substvarFiles = FileUtils.findFilesByExtension(defaultVarsDir, "substvar");
            
            for (Path substvarFile : substvarFiles) {
                parseSubstvarFile(substvarFile);
            }
            
            logger.info("Parsed global variables from {} .substvar files", substvarFiles.size());
            
        } catch (IOException e) {
            logger.error("Failed to parse global variables", e);
        }
    }
    
    /**
     * Parse individual .substvar file (XML format).
     * Example structure:
     * <globalVariables>
     *   <globalVariable>
     *     <name>hostname</name>
     *     <value>localhost</value>
     *   </globalVariable>
     * </globalVariables>
     */
    private void parseSubstvarFile(Path substvarFile) {
        try {
            Document doc = XmlUtils.parseXmlFile(substvarFile);
            Element root = doc.getDocumentElement();
            
            // Get variable group name from parent directory
            String groupName = substvarFile.getParent().getFileName().toString();
            
            // Find all globalVariable elements
            List<Element> variables = XmlUtils.getElementsByTagName(doc, "globalVariable");
            
            Map<String, String> variableMap = new HashMap<>();
            
            for (Element varElement : variables) {
                Element nameElement = XmlUtils.getFirstChildElement(varElement, "name");
                Element valueElement = XmlUtils.getFirstChildElement(varElement, "value");
                
                if (nameElement != null && valueElement != null) {
                    String name = XmlUtils.getElementText(nameElement);
                    String value = XmlUtils.getElementText(valueElement);
                    variableMap.put(name, value);
                }
            }
            
            globalVariables.put(groupName, variableMap);
            logger.debug("Parsed {} variables from group: {}", variableMap.size(), groupName);
            
        } catch (IOException e) {
            logger.error("Failed to parse .substvar file: {}", substvarFile, e);
        }
    }
    
    /**
     * Parse Tibco .process file.
     */
    private void parseProcess(Path processFile, AnalysisResult result) {
        try {
            logger.debug("Parsing Tibco process: {}", processFile.getFileName());
            Document doc = XmlUtils.parseXmlFile(processFile);
            
            // Get process name from pd:name element
            Element pdName = XmlUtils.getFirstChildElement(doc.getDocumentElement(), "pd:name");
            String processName = pdName != null ? XmlUtils.getElementText(pdName) : processFile.getFileName().toString();
            
            FlowInfo flowInfo = new FlowInfo();
            flowInfo.setName(processName);
            flowInfo.setType("process");
            flowInfo.setFileName(processFile.getFileName().toString());
            
            // Get process description
            Element pdDescription = XmlUtils.getFirstChildElement(doc.getDocumentElement(), "pd:description");
            if (pdDescription != null) {
                flowInfo.setDescription(XmlUtils.getElementText(pdDescription));
            }
            
            // Parse activities
            parseActivities(doc, flowInfo);
            
            result.getFlows().add(flowInfo);
            
        } catch (IOException e) {
            logger.error("Failed to parse Tibco process: {}", processFile, e);
        }
    }
    
    /**
     * Parse activities from process document.
     */
    private void parseActivities(Document doc, FlowInfo flowInfo) {
        // Find all pd:activity elements
        List<Element> activities = XmlUtils.getElementsByTagName(doc, "pd:activity");
        
        for (Element activity : activities) {
            ComponentInfo componentInfo = parseActivity(activity);
            if (componentInfo != null) {
                flowInfo.addComponent(componentInfo);
            }
        }
    }
    
    /**
     * Parse individual activity element.
     */
    private ComponentInfo parseActivity(Element activityElement) {
        ComponentInfo componentInfo = new ComponentInfo();
        
        // Get activity name
        Element nameElement = XmlUtils.getFirstChildElement(activityElement, "pd:name");
        if (nameElement != null) {
            componentInfo.setName(XmlUtils.getElementText(nameElement));
        }
        
        // Get activity type
        Element typeElement = XmlUtils.getFirstChildElement(activityElement, "pd:type");
        if (typeElement != null) {
            String type = XmlUtils.getElementText(typeElement);
            componentInfo.setType(type);
            componentInfo.setCategory(determineActivityCategory(type));
        }
        
        // Get configuration
        Element configElement = XmlUtils.getFirstChildElement(activityElement, "config");
        if (configElement != null) {
            parseActivityConfig(configElement, componentInfo);
        }
        
        return componentInfo;
    }
    
    /**
     * Parse activity configuration.
     */
    private void parseActivityConfig(Element configElement, ComponentInfo componentInfo) {
        // Extract common configuration attributes
        org.w3c.dom.NodeList children = configElement.getChildNodes();
        
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String tagName = element.getTagName();
                String value = XmlUtils.getElementText(element);
                
                // Resolve global variable references (%%VariableGroup/VariableName%%)
                value = resolveGlobalVariableReferences(value);
                
                componentInfo.addAttribute(tagName, value);
            }
        }
    }
    
    /**
     * Resolve global variable references in format: %%VariableGroup/VariableName%%
     */
    private String resolveGlobalVariableReferences(String value) {
        if (value == null || !value.contains("%%")) {
            return value;
        }
        
        // Pattern: %%GroupName/VariableName%%
        String resolved = value;
        int startIdx = resolved.indexOf("%%");
        
        while (startIdx != -1) {
            int endIdx = resolved.indexOf("%%", startIdx + 2);
            if (endIdx == -1) break;
            
            String reference = resolved.substring(startIdx + 2, endIdx);
            String[] parts = reference.split("/");
            
            if (parts.length == 2) {
                String groupName = parts[0];
                String varName = parts[1];
                
                Map<String, String> group = globalVariables.get(groupName);
                if (group != null && group.containsKey(varName)) {
                    String varValue = group.get(varName);
                    resolved = resolved.replace("%%" + reference + "%%", varValue);
                }
            }
            
            startIdx = resolved.indexOf("%%", endIdx + 2);
        }
        
        return resolved;
    }
    
    /**
     * Determine activity category based on type.
     */
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
    
    /**
     * Convert global variables to PropertyInfo objects.
     */
    private void convertGlobalVariablesToProperties(AnalysisResult result) {
        for (Map.Entry<String, Map<String, String>> groupEntry : globalVariables.entrySet()) {
            String groupName = groupEntry.getKey();
            Map<String, String> variables = groupEntry.getValue();
            
            for (Map.Entry<String, String> varEntry : variables.entrySet()) {
                String varName = varEntry.getKey();
                String varValue = varEntry.getValue();
                
                PropertyInfo propertyInfo = new PropertyInfo();
                propertyInfo.setKey(groupName + "/" + varName);
                propertyInfo.setDefaultValue(varValue);
                
                // Add to all environments (Tibco typically uses same variables across environments)
                for (String env : environments) {
                    propertyInfo.addEnvironmentValue(env, varValue);
                }
                
                result.getProperties().add(propertyInfo);
            }
        }
    }
}
