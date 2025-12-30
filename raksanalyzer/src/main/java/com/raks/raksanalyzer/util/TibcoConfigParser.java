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
public class TibcoConfigParser {
    private static final Logger logger = LoggerFactory.getLogger(TibcoConfigParser.class);
    public static Map<String, List<TibcoGlobalVariable>> parseGlobalVariables(
            Path projectPath, 
            Path customConfigPath) {
        if (customConfigPath != null) {
        } else {
        }
        Map<String, TibcoGlobalVariable> mergedVariables = new HashMap<>();
        Path defaultVarsDir = projectPath.resolve("defaultVars");
        if (Files.exists(defaultVarsDir)) {
            logger.info("Loading base configuration from defaultVars: {}", defaultVarsDir);
            Map<String, List<TibcoGlobalVariable>> defaultVars = parseDefaultVars(defaultVarsDir);
            mergeIntoHelper(mergedVariables, defaultVars);
        }
        Path tibcoXml = projectPath.resolve("TIBCO.xml");
        if (!Files.exists(tibcoXml)) {
            try {
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
        List<TibcoGlobalVariable> finalList = new ArrayList<>(mergedVariables.values());
        finalList.sort(Comparator.comparing(TibcoGlobalVariable::getFullName));
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
    private static void mergeIntoHelper(Map<String, TibcoGlobalVariable> master, Map<String, List<TibcoGlobalVariable>> source) {
        if (source == null) return;
        for (List<TibcoGlobalVariable> list : source.values()) {
            for (TibcoGlobalVariable gv : list) {
                String key = gv.getFullName();
                master.put(key, gv);
            }
        }
    }
    public static Map<String, List<TibcoGlobalVariable>> parseTibcoXml(Path tibcoXmlPath) {
        Map<String, List<TibcoGlobalVariable>> result = new HashMap<>();
        try {
            Document doc = XmlUtils.parseXmlFile(tibcoXmlPath);
            Element root = doc.getDocumentElement();
            if ("DeploymentDescriptors".equals(root.getNodeName())) {
                logger.info("Detected EAR deployment descriptor format");
                result = parseDeploymentDescriptorFormat(doc);
                if (!result.isEmpty()) {
                    return result;
                }
            }
            Element globalVariablesElement = XmlUtils.getFirstChildElement(root, "globalVariables");
            if (globalVariablesElement == null) {
                logger.warn("No globalVariables or NameValuePairs element found in TIBCO.xml");
                return result;
            }
            List<TibcoGlobalVariable> variables = new ArrayList<>();
            NodeList varNodes = globalVariablesElement.getElementsByTagName("globalVariable");
            for (int i = 0; i < varNodes.getLength(); i++) {
                Element varElement = (Element) varNodes.item(i);
                String name = XmlUtils.getElementText(XmlUtils.getFirstChildElement(varElement, "name"));
                String value = XmlUtils.getElementText(XmlUtils.getFirstChildElement(varElement, "value"));
                String type = XmlUtils.getElementText(XmlUtils.getFirstChildElement(varElement, "type"));
                String deploymentSettable = XmlUtils.getElementText(XmlUtils.getFirstChildElement(varElement, "deploymentSettable"));
                if (name != null) {
                    name = name.trim();
                    if (name.startsWith("%%") && name.endsWith("%%")) {
                        name = name.substring(2, name.length() - 2);
                    }
                }
                if (name != null) {
                    TibcoGlobalVariable gv = new TibcoGlobalVariable();
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
            if (!variables.isEmpty()) {
                result.put("default", variables);
                logger.info("Parsed {} global variables from TIBCO.xml", variables.size());
            }
        } catch (Exception e) {
            logger.error("Failed to parse TIBCO.xml: {}", tibcoXmlPath, e);
        }
        return result;
    }
    private static Map<String, List<TibcoGlobalVariable>> parseDeploymentDescriptorFormat(Document doc) {
        Map<String, List<TibcoGlobalVariable>> result = new HashMap<>();
        List<TibcoGlobalVariable> variables = new ArrayList<>();
        try {
            NodeList nameValuePairsList = doc.getElementsByTagName("NameValuePairs");
            for (int i = 0; i < nameValuePairsList.getLength(); i++) {
                Element nameValuePairs = (Element) nameValuePairsList.item(i);
                Element nameElement = XmlUtils.getFirstChildElement(nameValuePairs, "name");
                String sectionName = nameElement != null ? XmlUtils.getElementText(nameElement) : "";
                if ("Global Variables".equals(sectionName)) {
                    logger.info("Found Global Variables section in deployment descriptor");
                    String[] propertyTypes = {
                        "NameValuePair",           
                        "NameValuePairPassword",   
                        "NameValuePairInteger",    
                        "NameValuePairBoolean"     
                    };
                    for (String propertyType : propertyTypes) {
                        NodeList pairNodes = nameValuePairs.getElementsByTagName(propertyType);
                        for (int j = 0; j < pairNodes.getLength(); j++) {
                            Element pairElement = (Element) pairNodes.item(j);
                            String varName = XmlUtils.getElementText(XmlUtils.getFirstChildElement(pairElement, "name"));
                            String varValue = XmlUtils.getElementText(XmlUtils.getFirstChildElement(pairElement, "value"));
                            if (varName != null) {
                                varName = varName.trim();
                                if (varName.startsWith("%%") && varName.endsWith("%%")) {
                                    varName = varName.substring(2, varName.length() - 2);
                                }
                            }
                            if (varName != null && !varName.isEmpty()) {
                                TibcoGlobalVariable gv = new TibcoGlobalVariable();
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
                                String type = "String"; 
                                if ("NameValuePairPassword".equals(propertyType)) {
                                    type = "Password";
                                } else if ("NameValuePairInteger".equals(propertyType)) {
                                    type = "Integer";
                                } else if ("NameValuePairBoolean".equals(propertyType)) {
                                    type = "Boolean";
                                }
                                gv.setType(type);
                                gv.setDeploymentSettable(true); 
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
    public static Map<String, List<TibcoGlobalVariable>> parseDefaultVars(Path defaultVarsDir) {
        Map<String, List<TibcoGlobalVariable>> result = new HashMap<>();
        List<TibcoGlobalVariable> allVariables = new ArrayList<>();
        try {
            List<Path> substvarFiles = FileUtils.findFilesByExtension(defaultVarsDir, "substvar");
            for (Path substvarFile : substvarFiles) {
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
                String name = XmlUtils.getElementText(XmlUtils.getFirstChildElement(varElement, "name"));
                String value = XmlUtils.getElementText(XmlUtils.getFirstChildElement(varElement, "value"));
                String type = XmlUtils.getElementText(XmlUtils.getFirstChildElement(varElement, "type"));
                String deploymentSettable = XmlUtils.getElementText(XmlUtils.getFirstChildElement(varElement, "deploymentSettable"));
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
                    gv.setGroupName(groupName); 
                    variables.add(gv);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse .substvar file: {}", substvarFile, e);
        }
        return variables;
    }
}
