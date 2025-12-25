package com.raks.raksanalyzer.analyzer.tibco;

import com.raks.raksanalyzer.core.utils.FileUtils;
import com.raks.raksanalyzer.core.utils.XmlUtils;
import com.raks.raksanalyzer.domain.model.tibco.TibcoSharedResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for Tibco shared resources.
 * Handles shared variables, job shared variables, data formats, and alias libraries.
 */
public class TibcoResourceParser {
    private static final Logger logger = LoggerFactory.getLogger(TibcoResourceParser.class);
    
    /**
     * Parse shared variable file (.sharedvariable)
     */
    public static TibcoSharedResource parseSharedVariable(Path resourceFile) {
        try {
            Document doc = XmlUtils.parseXmlFile(resourceFile);
            TibcoSharedResource resource = new TibcoSharedResource();
            resource.setType("SharedVariable");
            resource.setFilePath(resourceFile.toString());
            resource.setName(resourceFile.getFileName().toString().replace(".sharedvariable", ""));
            
            Element root = doc.getDocumentElement();
            
            // Data type
            Element typeElement = XmlUtils.getFirstChildElement(root, "type");
            if (typeElement != null) {
                resource.setDataType(XmlUtils.getElementText(typeElement));
            }
            
            // Default value
            Element valueElement = XmlUtils.getFirstChildElement(root, "defaultValue");
            if (valueElement != null) {
                resource.setDefaultValue(XmlUtils.getElementText(valueElement));
            }
            
            logger.debug("Parsed shared variable: {}", resource.getName());
            return resource;
            
        } catch (IOException e) {
            logger.error("Failed to parse shared variable: {}", resourceFile, e);
            return null;
        }
    }
    
    /**
     * Parse job shared variable file (.jobsharedvariable)
     */
    public static TibcoSharedResource parseJobSharedVariable(Path resourceFile) {
        try {
            Document doc = XmlUtils.parseXmlFile(resourceFile);
            TibcoSharedResource resource = new TibcoSharedResource();
            resource.setType("JobSharedVariable");
            resource.setFilePath(resourceFile.toString());
            resource.setName(resourceFile.getFileName().toString().replace(".jobsharedvariable", ""));
            
            Element root = doc.getDocumentElement();
            
            // Data type
            Element typeElement = XmlUtils.getFirstChildElement(root, "type");
            if (typeElement != null) {
                resource.setDataType(XmlUtils.getElementText(typeElement));
            }
            
            // Default value
            Element valueElement = XmlUtils.getFirstChildElement(root, "defaultValue");
            if (valueElement != null) {
                resource.setDefaultValue(XmlUtils.getElementText(valueElement));
            }
            
            // Scope
            Element scopeElement = XmlUtils.getFirstChildElement(root, "scope");
            if (scopeElement != null) {
                resource.setScope(XmlUtils.getElementText(scopeElement));
            }
            
            logger.debug("Parsed job shared variable: {}", resource.getName());
            return resource;
            
        } catch (IOException e) {
            logger.error("Failed to parse job shared variable: {}", resourceFile, e);
            return null;
        }
    }
    
    /**
     * Parse data format file (.sharedparse)
     */
    public static TibcoSharedResource parseDataFormat(Path resourceFile) {
        try {
            Document doc = XmlUtils.parseXmlFile(resourceFile);
            TibcoSharedResource resource = new TibcoSharedResource();
            resource.setType("DataFormat");
            resource.setFilePath(resourceFile.toString());
            resource.setName(resourceFile.getFileName().toString().replace(".sharedparse", ""));
            
            Element root = doc.getDocumentElement();
            
            // Format type (Delimited, Fixed, etc.)
            Element formatElement = XmlUtils.getFirstChildElement(root, "formatType");
            if (formatElement != null) {
                resource.setFormatType(XmlUtils.getElementText(formatElement));
            }
            
            // Delimiter (for delimited formats)
            Element delimiterElement = XmlUtils.getFirstChildElement(root, "delimiter");
            if (delimiterElement != null) {
                resource.setDelimiter(XmlUtils.getElementText(delimiterElement));
            }
            
            logger.debug("Parsed data format: {}", resource.getName());
            return resource;
            
        } catch (IOException e) {
            logger.error("Failed to parse data format: {}", resourceFile, e);
            return null;
        }
    }
    
    /**
     * Parse alias library file (.aliaslib)
     */
    public static TibcoSharedResource parseAliasLibrary(Path resourceFile) {
        try {
            Document doc = XmlUtils.parseXmlFile(resourceFile);
            TibcoSharedResource resource = new TibcoSharedResource();
            resource.setType("AliasLibrary");
            resource.setFilePath(resourceFile.toString());
            resource.setName(resourceFile.getFileName().toString().replace(".aliaslib", ""));
            
            // Parse alias definitions
            List<Element> aliases = XmlUtils.getElementsByTagName(doc, "alias");
            for (Element alias : aliases) {
                Element nameElement = XmlUtils.getFirstChildElement(alias, "name");
                Element targetElement = XmlUtils.getFirstChildElement(alias, "target");
                
                if (nameElement != null && targetElement != null) {
                    String aliasName = XmlUtils.getElementText(nameElement);
                    String aliasTarget = XmlUtils.getElementText(targetElement);
                    resource.addProperty(aliasName, aliasTarget);
                }
            }
            
            logger.debug("Parsed alias library: {} with {} aliases", resource.getName(), resource.getProperties().size());
            return resource;
            
        } catch (IOException e) {
            logger.error("Failed to parse alias library: {}", resourceFile, e);
            return null;
        }
    }
    
    /**
     * Parse all shared resources in a project
     */
    public static List<TibcoSharedResource> parseAllResources(Path projectPath) {
        List<TibcoSharedResource> resources = new ArrayList<>();
        
        try {
            // Shared variables
            List<Path> sharedVars = FileUtils.findFilesByExtension(projectPath, "sharedvariable");
            for (Path file : sharedVars) {
                TibcoSharedResource resource = parseSharedVariable(file);
                if (resource != null) {
                    resources.add(resource);
                }
            }
            
            // Job shared variables
            List<Path> jobVars = FileUtils.findFilesByExtension(projectPath, "jobsharedvariable");
            for (Path file : jobVars) {
                TibcoSharedResource resource = parseJobSharedVariable(file);
                if (resource != null) {
                    resources.add(resource);
                }
            }
            
            // Data formats
            List<Path> dataFormats = FileUtils.findFilesByExtension(projectPath, "sharedparse");
            for (Path file : dataFormats) {
                TibcoSharedResource resource = parseDataFormat(file);
                if (resource != null) {
                    resources.add(resource);
                }
            }
            
            // Alias libraries
            List<Path> aliasLibs = FileUtils.findFilesByExtension(projectPath, "aliaslib");
            for (Path file : aliasLibs) {
                TibcoSharedResource resource = parseAliasLibrary(file);
                if (resource != null) {
                    resources.add(resource);
                }
            }
            
            logger.info("Parsed {} Tibco shared resources", resources.size());
            
        } catch (IOException e) {
            logger.error("Failed to parse shared resources", e);
        }
        
        return resources;
    }
}
