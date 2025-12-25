package com.raks.raksanalyzer.util;

import com.raks.raksanalyzer.core.utils.FileUtils;
import com.raks.raksanalyzer.domain.model.tibco.TibcoGlobalVariable;
import com.raks.raksanalyzer.core.utils.XmlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Unified parser for TIBCO global variables from multiple sources.
 * Supports three configuration sources with priority:
 * 1. Custom config file (--config parameter) - Highest priority
 * 2. TIBCO.xml (EAR projects)
 * 3. defaultVars/*.substvar (Source projects) - Lowest priority
 */
public class TibcoConfigParser {
    private static final Logger logger = LoggerFactory.getLogger(TibcoConfigParser.class);
    
    /**
     * Parse global variables with flexible source resolution.
     * Priority: customConfigPath > TIBCO.xml > defaultVars/
     * 
     * @param projectPath Path to project root
     * @param customConfigPath Optional custom config file path (from --config parameter)
     * @return Map of environment name to list of global variables
     */
    public static Map<String, List<TibcoGlobalVariable>> parseGlobalVariables(
            Path projectPath, 
            Path customConfigPath) {
        
        // CRITICAL LOGGING: Entry point trace


        if (customConfigPath != null) {


        } else {

        }
        
        // Master map to hold merged variables (groupName/varName -> GlobalVariable)
        // We use a Map<String, TibcoGlobalVariable> for easy lookup and replacement
        Map<String, TibcoGlobalVariable> mergedVariables = new HashMap<>();
        
        // 1. Priority 3 (Base): defaultVars/ (Source projects)
        Path defaultVarsDir = projectPath.resolve("defaultVars");
        if (Files.exists(defaultVarsDir)) {
            logger.info("Loading base configuration from defaultVars: {}", defaultVarsDir);
            Map<String, List<TibcoGlobalVariable>> defaultVars = parseDefaultVars(defaultVarsDir);
            mergeIntoHelper(mergedVariables, defaultVars);
        }
        
        // 2. Priority 2: TIBCO.xml (EAR projects)
        Path tibcoXml = projectPath.resolve("TIBCO.xml");
        if (!Files.exists(tibcoXml)) {
            try {
                // Recursive search for TIBCO.xml
                List<Path> found = FileUtils.findFiles(projectPath, "TIBCO.xml");
                if (!found.isEmpty()) {
                    tibcoXml = found.get(0);
                }
            } catch (Exception e) {
                logger.warn("Error searching for TIBCO.xml: {}", e.getMessage());
            }
        }

        if (Files.exists(tibcoXml)) {
            logger.info("Loading configuration from TIBCO.xml: {}", tibcoXml);
            Map<String, List<TibcoGlobalVariable>> tibcoVars = parseTibcoXml(tibcoXml);
            mergeIntoHelper(mergedVariables, tibcoVars);
        }
        
        // 3. Priority 1 (Highest): Custom config file (--config parameter)
        if (customConfigPath != null) {
            if (Files.exists(customConfigPath)) {
                logger.info("Loading override configuration from custom file: {}", customConfigPath.toAbsolutePath());
                Map<String, List<TibcoGlobalVariable>> customVars = parseTibcoXml(customConfigPath);
                mergeIntoHelper(mergedVariables, customVars);
            } else {
                System.out.println("DEBUG: Custom config path does not exist: " + customConfigPath.toAbsolutePath());
                logger.info("Custom configuration file provided but NOT found: {}", customConfigPath.toAbsolutePath());
            }
        }
        
        if (mergedVariables.isEmpty()) {
            logger.warn("No global variable configuration found");
            return new HashMap<>();
        }
        
        // Flatten to list for sorting and regrouping
        List<TibcoGlobalVariable> finalList = new ArrayList<>(mergedVariables.values());
        
        // Sort for consistent output
        finalList.sort(Comparator.comparing(TibcoGlobalVariable::getFullName));
        
        // Regroup variables by Group Name
        Map<String, List<TibcoGlobalVariable>> result = new HashMap<>();
        
        for (TibcoGlobalVariable gv : finalList) {
            String group = gv.getGroupName();
            if (group == null || group.isEmpty()) {
                group = "default";
            }
            result.computeIfAbsent(group, k -> new ArrayList<>()).add(gv);
        }
        
        logger.info("Final resolved configuration contains {} global variables across {} groups", 
            finalList.size(), result.size());
        return result;
    }

    /**
     * Helper to merge variables into the master map.
     * Overwrites existing variables with the same Full Name.
     */
    private static void mergeIntoHelper(Map<String, TibcoGlobalVariable> master, Map<String, List<TibcoGlobalVariable>> source) {
        if (source == null) return;
        
        for (List<TibcoGlobalVariable> list : source.values()) {
            for (TibcoGlobalVariable gv : list) {
                // Key is full path: Group/Name
                String key = gv.getFullName();
                master.put(key, gv);
            }
        }
    }
    
    /**
     * Parse TIBCO.xml file for global variables.
     * Supports two formats:
     * 
     * 1. EAR Deployment Descriptor Format (TIBCO.xml):
     * <DeploymentDescriptors>
     *   <NameValuePairs>
     *     <name>Global Variables</name>
     *     <NameValuePair>
     *       <name>httpConnection/httphost</name>
     *       <value>localhost</value>
     *     </NameValuePair>
     *   </NameValuePairs>
     * </DeploymentDescriptors>
     * 
     * 2. Source Project Format (.substvar):
     * <repository>
     *   <globalVariables>
     *     <globalVariable>
     *       <name>%%VariableName%%</name>
     *       <value>value</value>
     *     </globalVariable>
     *   </globalVariables>
     * </repository>
     * 
     * @param tibcoXmlPath Path to TIBCO.xml file
     * @return Map of environment name to list of global variables
     */
    public static Map<String, List<TibcoGlobalVariable>> parseTibcoXml(Path tibcoXmlPath) {
        Map<String, List<TibcoGlobalVariable>> result = new HashMap<>();
        
        try {
            Document doc = XmlUtils.parseXmlFile(tibcoXmlPath);
            Element root = doc.getDocumentElement();
            
            // Check if this is a DeploymentDescriptors format (EAR TIBCO.xml)
            if ("DeploymentDescriptors".equals(root.getNodeName())) {
                logger.info("Detected EAR deployment descriptor format");
                result = parseDeploymentDescriptorFormat(doc);
                if (!result.isEmpty()) {
                    return result;
                }
            }
            
            // Fall back to repository/globalVariables format (source project)
            Element globalVariablesElement = XmlUtils.getFirstChildElement(root, "globalVariables");
            if (globalVariablesElement == null) {
                logger.warn("No globalVariables or NameValuePairs element found in TIBCO.xml");
                return result;
            }
            
            // Parse all globalVariable elements
            List<TibcoGlobalVariable> variables = new ArrayList<>();
            NodeList varNodes = globalVariablesElement.getElementsByTagName("globalVariable");
            
            for (int i = 0; i < varNodes.getLength(); i++) {
                Element varElement = (Element) varNodes.item(i);
                
                String name = XmlUtils.getElementText(XmlUtils.getFirstChildElement(varElement, "name"));
                String value = XmlUtils.getElementText(XmlUtils.getFirstChildElement(varElement, "value"));
                String type = XmlUtils.getElementText(XmlUtils.getFirstChildElement(varElement, "type"));
                String deploymentSettable = XmlUtils.getElementText(XmlUtils.getFirstChildElement(varElement, "deploymentSettable"));
                
                // Clean up name (remove surrounding %%)
                if (name != null) {
                    name = name.trim();
                    if (name.startsWith("%%") && name.endsWith("%%")) {
                        name = name.substring(2, name.length() - 2);
                    }
                }
                
                if (name != null) {
                    TibcoGlobalVariable gv = new TibcoGlobalVariable();
                    
                    // Extract Group Name if present (e.g. Connections/JMS/ProviderUrl)
                    if (name.contains("/")) {
                        int lastSlash = name.lastIndexOf('/');
                        String group = name.substring(0, lastSlash);
                        String leaf = name.substring(lastSlash + 1);
                        gv.setGroupName(group);
                        gv.setName(leaf);
                    } else {
                        gv.setName(name);
                    }
                    
                    gv.setValue(value != null ? value : "");
                    gv.setType(type != null ? type : "String");
                    gv.setDeploymentSettable("true".equalsIgnoreCase(deploymentSettable));
                    
                    variables.add(gv);
                }
            }
            
            // TIBCO.xml doesn't have environment separation, so use "default" environment
            if (!variables.isEmpty()) {
                result.put("default", variables);
                logger.info("Parsed {} global variables from TIBCO.xml", variables.size());
            }
            
        } catch (Exception e) {
            logger.error("Failed to parse TIBCO.xml: {}", tibcoXmlPath, e);
        }
        
        return result;
    }
    
    /**
     * Parse TIBCO EAR deployment descriptor format.
     * Extracts global variables from NameValuePairs section.
     * Supports multiple property types:
     * - NameValuePair (String)
     * - NameValuePairPassword (Password)
     * - NameValuePairInteger (Integer)
     * - NameValuePairBoolean (Boolean)
     */
    private static Map<String, List<TibcoGlobalVariable>> parseDeploymentDescriptorFormat(Document doc) {
        Map<String, List<TibcoGlobalVariable>> result = new HashMap<>();
        List<TibcoGlobalVariable> variables = new ArrayList<>();
        
        try {
            // Find all NameValuePairs elements
            NodeList nameValuePairsList = doc.getElementsByTagName("NameValuePairs");
            
            for (int i = 0; i < nameValuePairsList.getLength(); i++) {
                Element nameValuePairs = (Element) nameValuePairsList.item(i);
                
                // Check if this is the "Global Variables" section
                Element nameElement = XmlUtils.getFirstChildElement(nameValuePairs, "name");
                String sectionName = nameElement != null ? XmlUtils.getElementText(nameElement) : "";
                
                if ("Global Variables".equals(sectionName)) {
                    logger.info("Found Global Variables section in deployment descriptor");
                    
                    // Define all possible property type elements
                    String[] propertyTypes = {
                        "NameValuePair",           // String type
                        "NameValuePairPassword",   // Password type
                        "NameValuePairInteger",    // Integer type
                        "NameValuePairBoolean"     // Boolean type
                    };
                    
                    // Parse all property types
                    for (String propertyType : propertyTypes) {
                        NodeList pairNodes = nameValuePairs.getElementsByTagName(propertyType);
                        
                        for (int j = 0; j < pairNodes.getLength(); j++) {
                            Element pairElement = (Element) pairNodes.item(j);
                            
                            String varName = XmlUtils.getElementText(XmlUtils.getFirstChildElement(pairElement, "name"));
                            String varValue = XmlUtils.getElementText(XmlUtils.getFirstChildElement(pairElement, "value"));
                            
                            // Clean up name (remove surrounding %%)
                            if (varName != null) {
                                varName = varName.trim();
                                if (varName.startsWith("%%") && varName.endsWith("%%")) {
                                    varName = varName.substring(2, varName.length() - 2);
                                }
                            }
                            
                            if (varName != null && !varName.isEmpty()) {
                                TibcoGlobalVariable gv = new TibcoGlobalVariable();
                                
                                // Extract Group Name if present
                                if (varName.contains("/")) {
                                    int lastSlash = varName.lastIndexOf('/');
                                    String group = varName.substring(0, lastSlash);
                                    String leaf = varName.substring(lastSlash + 1);
                                    gv.setGroupName(group);
                                    gv.setName(leaf);
                                } else {
                                    gv.setName(varName);
                                }
                                
                                gv.setValue(varValue != null ? varValue : "");
                                
                                // Set type based on element name
                                String type = "String"; // Default
                                if ("NameValuePairPassword".equals(propertyType)) {
                                    type = "Password";
                                } else if ("NameValuePairInteger".equals(propertyType)) {
                                    type = "Integer";
                                } else if ("NameValuePairBoolean".equals(propertyType)) {
                                    type = "Boolean";
                                }
                                gv.setType(type);
                                gv.setDeploymentSettable(true); // Assume all are deployment settable
                                
                                variables.add(gv);
                                logger.debug("Parsed variable: {} = {} (type: {})", varName, varValue, type);
                            }
                        }
                    }
                }
            }
            
            if (!variables.isEmpty()) {
                result.put("default", variables);
                logger.info("Parsed {} global variables from deployment descriptor (all types)", variables.size());
            }
            
        } catch (Exception e) {
            logger.error("Failed to parse deployment descriptor format", e);
        }
        
        return result;
    }
    
    /**
     * Parse defaultVars directory for .substvar files.
     * Preserves the folder structure as the group name.
     * 
     * @param defaultVarsDir Path to defaultVars directory
     * @return Map of environment name to list of global variables
     */
    public static Map<String, List<TibcoGlobalVariable>> parseDefaultVars(Path defaultVarsDir) {
        Map<String, List<TibcoGlobalVariable>> result = new HashMap<>();
        List<TibcoGlobalVariable> allVariables = new ArrayList<>();
        
        try {
            // Find all .substvar files
            List<Path> substvarFiles = FileUtils.findFilesByExtension(defaultVarsDir, "substvar");
            
            for (Path substvarFile : substvarFiles) {
                // Derive group name from relative path
                String groupName = deriveGroupName(substvarFile, defaultVarsDir);
                
                List<TibcoGlobalVariable> variables = parseSubstvarFile(substvarFile, groupName);
                if (!variables.isEmpty()) {
                    allVariables.addAll(variables);
                }
            }
            
            if (!allVariables.isEmpty()) {
                result.put("default", allVariables);
                logger.info("Parsed {} global variables from defaultVars", allVariables.size());
            }
            
        } catch (IOException e) {
            logger.error("Failed to parse defaultVars directory", e);
        }
        
        return result;
    }
    
    private static String deriveGroupName(Path substvarFile, Path defaultVarsDir) {
        Path parent = substvarFile.getParent();
        if (parent == null || parent.equals(defaultVarsDir)) {
            // If in root of defaultVars, check if it's defaultVars.substvar
            return "defaultVars";
        }
        
        try {
            // Relative path from defaultVars
            String rel = defaultVarsDir.relativize(parent).toString();
            // Normalize separators
            rel = rel.replace("\\", "/");
            return rel;
        } catch (IllegalArgumentException e) {
            return parent.getFileName().toString();
        }
    }
    
    /**
     * Parse a single .substvar file.
     * 
     * @param substvarFile Path to .substvar file
     * @param groupName Group name derived from folder structure
     * @return List of global variables
     */
    private static List<TibcoGlobalVariable> parseSubstvarFile(Path substvarFile, String groupName) {
        List<TibcoGlobalVariable> variables = new ArrayList<>();
        
        try {
            Document doc = XmlUtils.parseXmlFile(substvarFile);
            Element root = doc.getDocumentElement();
            
            Element globalVariablesElement = XmlUtils.getFirstChildElement(root, "globalVariables");
            if (globalVariablesElement == null) {
                return variables;
            }
            
            NodeList varNodes = globalVariablesElement.getElementsByTagName("globalVariable");
            
            for (int i = 0; i < varNodes.getLength(); i++) {
                Element varElement = (Element) varNodes.item(i);
                
                // Use safe XML extraction that handles comments and whitespace
                String name = XmlUtils.getElementText(XmlUtils.getFirstChildElement(varElement, "name"));
                String value = XmlUtils.getElementText(XmlUtils.getFirstChildElement(varElement, "value"));
                String type = XmlUtils.getElementText(XmlUtils.getFirstChildElement(varElement, "type"));
                String deploymentSettable = XmlUtils.getElementText(XmlUtils.getFirstChildElement(varElement, "deploymentSettable"));
                
                // Clean up name (remove surrounding %%)
                if (name != null) {
                    name = name.trim();
                    if (name.startsWith("%%") && name.endsWith("%%")) {
                        name = name.substring(2, name.length() - 2);
                    }
                }
                
                if (name != null && !name.isEmpty()) {
                    TibcoGlobalVariable gv = new TibcoGlobalVariable();
                    gv.setName(name);
                    gv.setValue(value != null ? value : "");
                    gv.setType(type != null ? type : "String");
                    gv.setDeploymentSettable("true".equalsIgnoreCase(deploymentSettable));
                    gv.setGroupName(groupName); // Set the group name!
                    
                    variables.add(gv);
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to parse .substvar file: {}", substvarFile, e);
        }
        
        return variables;
    }
}
