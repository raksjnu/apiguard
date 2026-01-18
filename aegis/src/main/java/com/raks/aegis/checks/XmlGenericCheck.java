package com.raks.aegis.checks;

import com.raks.aegis.model.Check;
import com.raks.aegis.model.CheckResult;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Universal XML Validation Check.
 * Replaces: XmlXPathExists, XmlXPathNotExists, XmlAttributeExists, XmlAttributeNotExists.
 * Parameters:
 *  - filePatterns (List<String>): Target files.
 *  - xpath (String): Expression to find.
 *  - attribute (String, optional): Specific attribute value to check on found nodes.
 *  - mode (String): EXISTS (count > 0) or NOT_EXISTS (count == 0).
 *  - matchMode (String): ALL_FILES, ANY_FILE, NONE_OF_FILES.
 */
public class XmlGenericCheck extends AbstractCheck {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(XmlGenericCheck.class);

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        if (!isRuleApplicable(projectRoot, check)) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(), "Pre-conditions not met.");
        }

        Map<String, Object> params = getEffectiveParams(check);
        
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) params.get("filePatterns");
        String xpathExpr = (String) params.get("xpath");
        String attributeMatch = (String) params.get("attribute");
        String mode = (String) params.getOrDefault("mode", "EXISTS");
        String matchMode = (String) params.getOrDefault("matchMode", "ALL_FILES");

        if (filePatterns == null || filePatterns.isEmpty()) {
             logger.info("DEBUG: filePatterns is null/empty for {} Values: {}", check.getRuleId(), params.get("filePatterns"));
             return failConfig(check, "filePatterns required");
        }
        if (xpathExpr == null) return failConfig(check, "xpath required");

        int passedFileCount = 0;
        int totalFiles = 0;
        List<String> details = new ArrayList<>();

        @SuppressWarnings("unchecked")
        List<String> forbiddenAttrs = (List<String>) params.get("forbiddenAttributes");
        java.util.Set<String> allFoundItems = new java.util.HashSet<>();

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            List<Path> matchingFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesAnyPattern(path, filePatterns, projectRoot))
                    .filter(path -> !shouldIgnorePath(projectRoot, path))
                    .toList();
            
            totalFiles = matchingFiles.size();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            
            for (Path file : matchingFiles) {
                boolean filePassed = false;
                try {
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(file.toFile());
                    XPath xpath = XPathFactory.newInstance().newXPath();
                    NodeList nodes = (NodeList) xpath.evaluate(xpathExpr, doc, XPathConstants.NODESET);
                    
                    int foundCount = nodes.getLength();
                    java.util.Set<String> fileFoundItems = new java.util.HashSet<>();
                    
                    if (attributeMatch != null && foundCount > 0) {
                        // Refine count based on attribute value match?
                    }

                    if ("EXISTS".equalsIgnoreCase(mode)) {
                        if (foundCount > 0) {
                            filePassed = true;
                        } else {
                            // FALLBACK: Property Resolution Logic
                            // If strict match failed, check if it was due to unresolved property placeholders.
                            logger.debug("Attempting property resolution fallback for XPath: {}", xpathExpr);
                            try {
                                String[] subPaths = xpathExpr.split(" \\| ");
                                boolean propertyMatchFound = false;
                                for (String subPath : subPaths) {
                                    // Pattern matches CheckFactory format: //*[local-name()='ELEMENT' and @ATTR='VALUE']
                                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("local-name\\(\\)\\s*=\\s*'([^']+)'\\s+and\\s+@([^=]+)='([^']+)'").matcher(subPath);
                                    while (m.find()) {
                                        String elem = m.group(1);
                                        String attr = m.group(2);
                                        String expected = m.group(3);
                                        
                                        logger.debug("Checking property resolution: element={}, attribute={}, expected={}", elem, attr, expected);
                                        
                                        String relaxedXpath = "//*[local-name()='" + elem + "' and @" + attr + "]";
                                        NodeList relaxedNodes = (NodeList) xpath.evaluate(relaxedXpath, doc, XPathConstants.NODESET);
                                        
                                        logger.debug("Found {} nodes with relaxed XPath", relaxedNodes.getLength());
                                        
                                        for (int i = 0; i < relaxedNodes.getLength(); i++) {
                                            org.w3c.dom.Node n = relaxedNodes.item(i);
                                            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                                                org.w3c.dom.Element e = (org.w3c.dom.Element) n;
                                                String rawVal = e.getAttribute(attr);
                                                String resolved = com.raks.aegis.util.PropertyResolver.resolve(rawVal, projectRoot);
                                                logger.debug("Property resolution: raw='{}', resolved='{}', expected='{}'", rawVal, resolved, expected);
                                                if (expected.equals(resolved)) {
                                                    propertyMatchFound = true;
                                                    logger.info("Property resolution SUCCESS: {} -> {}", rawVal, resolved);
                                                    break;
                                                }
                                            }
                                        }
                                        if (propertyMatchFound) break;
                                    }
                                    if (propertyMatchFound) break;
                                }
                                
                                if (propertyMatchFound) {
                                    filePassed = true;
                                } else {
                                    details.add(projectRoot.relativize(file) + " [XPath not found]");
                                }
                            } catch (Exception ex) {
                                logger.error("Property resolution fallback failed", ex);
                                details.add(projectRoot.relativize(file) + " [XPath not found]");
                            }
                        }
                    } else { // NOT_EXISTS
                        if (foundCount == 0) {
                            filePassed = true;
                        } else {
                            // Identify what was found
                            if (forbiddenAttrs != null && !forbiddenAttrs.isEmpty()) {
                                for (int i = 0; i < nodes.getLength(); i++) {
                                    org.w3c.dom.Node n = nodes.item(i);
                                    if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                                        org.w3c.dom.Element e = (org.w3c.dom.Element) n;
                                        for (String fa : forbiddenAttrs) {
                                            if (e.hasAttribute(fa)) {
                                                fileFoundItems.add(fa);
                                                allFoundItems.add(fa);
                                            }
                                        }
                                    }
                                }
                            }
                            String foundStr = fileFoundItems.isEmpty() ? String.valueOf(foundCount) + " matches" : "Found: " + String.join(", ", fileFoundItems);
                            details.add(projectRoot.relativize(file) + " [XPath found (" + foundStr + ")]");
                        }
                    }
                    
                } catch (Exception e) {
                    details.add(projectRoot.relativize(file) + " [Parse Error: " + e.getMessage() + "]");
                }
                
                if (filePassed) passedFileCount++;
            }

            boolean uniqueCondition = evaluateMatchMode(matchMode, totalFiles, passedFileCount);
            
            String checkedFilesStr = matchingFiles.stream()
                    .map(p -> projectRoot.relativize(p).toString())
                    .collect(java.util.stream.Collectors.joining(", "));
            
            String foundItemsStr = allFoundItems.isEmpty() ? null : String.join(", ", allFoundItems);

            if (uniqueCondition) {
                String defaultSuccess = String.format("XML Check passed for %s files. (Mode: %s, Passed: %d/%d)", mode, matchMode, passedFileCount, totalFiles);
                return CheckResult.pass(check.getRuleId(), check.getDescription(), getCustomSuccessMessage(check, defaultSuccess, checkedFilesStr));
            } else {
                String technicalMsg = String.format("XML Check failed for %s. (Mode: %s, Passed: %d/%d). Failures:\n• %s", 
                                mode, matchMode, passedFileCount, totalFiles, 
                                details.isEmpty() ? "Pattern mismatch" : String.join("\n• ", details));
                return CheckResult.fail(check.getRuleId(), check.getDescription(), getCustomMessage(check, technicalMsg, checkedFilesStr, foundItemsStr));
            }

        } catch (Exception e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Scan Error: " + e.getMessage());
        }
    }
    
    private CheckResult failConfig(Check check, String msg) {
        return CheckResult.fail(check.getRuleId(), check.getDescription(), "Config Error: " + msg);
    }
}
