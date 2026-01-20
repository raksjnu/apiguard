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
        String expectedValue = (String) params.get("expectedValue");
        String forbiddenValue = (String) params.get("forbiddenValue");
        boolean resolveProperties = Boolean.parseBoolean(String.valueOf(params.getOrDefault("resolveProperties", "false")));
        String mode = (String) params.getOrDefault("mode", "EXISTS");
        String matchMode = (String) params.getOrDefault("matchMode", "ALL_FILES");

        if (filePatterns == null || filePatterns.isEmpty()) {
             logger.info("DEBUG: filePatterns is null/empty for {} Values: {}", check.getRuleId(), params.get("filePatterns"));
             return failConfig(check, "filePatterns required");
        }
        if (xpathExpr == null && 
            !params.containsKey("minVersions") && 
            !params.containsKey("exactVersions") && 
            !params.containsKey("requiredFields")) {
             return failConfig(check, "xpath or standard validation params (minVersions, etc.) required");
        }

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
            // Disable DTD validation to avoid network calls or failures on missing DTDs
            try {
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (Exception ignore) {}

            List<String> passedFilesList = new ArrayList<>();
            java.util.Set<String> successDetails = new java.util.HashSet<>();

            for (Path file : matchingFiles) {
                boolean filePassed = true; // Default to true, strictly fail on errors
                List<String> fileSuccesses = new ArrayList<>();
                List<String> fileErrors = new ArrayList<>();

                try {
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(file.toFile());
                    XPath xpath = XPathFactory.newInstance().newXPath();

                    // Standard: requiredFields (Exact Match by XPath)
                    @SuppressWarnings("unchecked")
                    Map<String, String> requiredFields = (Map<String, String>) params.get("requiredFields");
                    if (requiredFields != null) {
                        for (Map.Entry<String, String> entry : requiredFields.entrySet()) {
                            String xp = entry.getKey();
                            String expected = entry.getValue();
                            
                            try {
                                NodeList nodes = (NodeList) xpath.evaluate(xp, doc, XPathConstants.NODESET);
                                if (nodes == null || nodes.getLength() == 0) {
                                    filePassed = false;
                                    fileErrors.add("Missing required XPath: " + xp);
                                } else {
                                    boolean anyMatch = false;
                                    for (int i=0; i < nodes.getLength(); i++) {
                                        String actual = nodes.item(i).getTextContent().trim();
                                        if (resolveProperties) {
                                            actual = com.raks.aegis.util.PropertyResolver.resolve(actual, projectRoot);
                                        }

                                        if (actual.equals(expected)) {
                                            anyMatch = true;
                                            fileSuccesses.add(String.format("%s=%s", xp, actual));
                                            allFoundItems.add(String.format("%s=%s", xp, actual));
                                        } else {
                                            // Collecting non-matches for debug/report
                                            allFoundItems.add(String.format("%s=%s", xp, actual)); 
                                        }
                                    }
                                    
                                    if (!anyMatch) {
                                        filePassed = false;
                                        fileErrors.add(String.format("Value mismatch for '%s'. Expected='%s'", xp, expected));
                                    }
                                }
                            } catch (Exception e) {
                                filePassed = false;
                                fileErrors.add("XPath Error (" + xp + "): " + e.getMessage());
                            }
                        }
                    }

                    // Standard: minVersions (>=)
                    @SuppressWarnings("unchecked")
                    Map<String, String> minVersions = (Map<String, String>) params.get("minVersions");
                    if (minVersions != null) {
                       for (Map.Entry<String, String> entry : minVersions.entrySet()) {
                            String xp = entry.getKey();
                            String minVer = entry.getValue();

                            try {
                                NodeList nodes = (NodeList) xpath.evaluate(xp, doc, XPathConstants.NODESET);
                                if (nodes == null || nodes.getLength() == 0) {
                                    filePassed = false;
                                    fileErrors.add("Missing version XPath: " + xp);
                                } else {
                                    boolean anyMatch = false;
                                    for (int i=0; i < nodes.getLength(); i++) {
                                        String actual = nodes.item(i).getTextContent().trim();
                                        if (resolveProperties) {
                                            actual = com.raks.aegis.util.PropertyResolver.resolve(actual, projectRoot);
                                        }

                                        if (compareValues(actual, minVer, "GTE", "SEMVER")) {
                                            anyMatch = true;
                                            fileSuccesses.add(String.format("%s=%s", xp, actual));
                                            allFoundItems.add(String.format("%s=%s", xp, actual));
                                        } else {
                                             allFoundItems.add(String.format("%s=%s", xp, actual));
                                        }
                                    }

                                    if (!anyMatch) {
                                        filePassed = false;
                                        fileErrors.add(String.format("Version too low at '%s'. Min='%s'", xp, minVer));
                                    }
                                }
                            } catch (Exception e) {
                                filePassed = false;
                                fileErrors.add("XPath Error (" + xp + "): " + e.getMessage());
                            }
                       }
                    }

                    // Standard: exactVersions (==)
                    @SuppressWarnings("unchecked")
                    Map<String, String> exactVersions = (Map<String, String>) params.get("exactVersions");
                    if (exactVersions != null) {
                       for (Map.Entry<String, String> entry : exactVersions.entrySet()) {
                            String xp = entry.getKey();
                            String exactVer = entry.getValue();

                            try {
                                NodeList nodes = (NodeList) xpath.evaluate(xp, doc, XPathConstants.NODESET);
                                if (nodes == null || nodes.getLength() == 0) {
                                    filePassed = false;
                                    fileErrors.add("Missing version XPath: " + xp);
                                } else {
                                    boolean anyMatch = false;
                                    for (int i=0; i < nodes.getLength(); i++) {
                                        String actual = nodes.item(i).getTextContent().trim();
                                        if (resolveProperties) {
                                            actual = com.raks.aegis.util.PropertyResolver.resolve(actual, projectRoot);
                                        }

                                        if (compareValues(actual, exactVer, "EQ", "SEMVER")) {
                                            anyMatch = true;
                                            fileSuccesses.add(String.format("%s=%s", xp, actual));
                                            allFoundItems.add(String.format("%s=%s", xp, actual));
                                        } else {
                                             allFoundItems.add(String.format("%s=%s", xp, actual));
                                        }
                                    }

                                    if (!anyMatch) {
                                        filePassed = false;
                                        fileErrors.add(String.format("Version mismatch at '%s'. Expected='%s'", xp, exactVer));
                                    }
                                }
                            } catch (Exception e) {
                                filePassed = false;
                                fileErrors.add("XPath Error (" + xp + "): " + e.getMessage());
                            }
                       }
                    }

                    // Legacy Config Logic (only if xpathExpr is present)
                    if (xpathExpr != null) {
                        NodeList nodes = (NodeList) xpath.evaluate(xpathExpr, doc, XPathConstants.NODESET);
                        int foundCount = nodes.getLength();
                        java.util.Set<String> fileFoundItems = new java.util.HashSet<>();

                        if (attributeMatch != null && foundCount > 0) {
                            // Logic for attribute match can be enhanced if needed
                        }

                        boolean legacyPassed = false;

                        if ("EXISTS".equalsIgnoreCase(mode)) {
                            if (foundCount > 0) {
                                if (expectedValue != null) {
                                    boolean valMatched = false;
                                    for (int i = 0; i < nodes.getLength(); i++) {
                                        String actual = nodes.item(i).getTextContent();
                                        if (resolveProperties) {
                                            String resolved = com.raks.aegis.util.PropertyResolver.resolve(actual, projectRoot);
                                            if (expectedValue.equals(resolved)) {
                                                valMatched = true;
                                                addPropertyResolution(actual, resolved);
                                                fileSuccesses.add("Found: " + resolved);
                                                break;
                                            }
                                        } else {
                                            if (expectedValue.equals(actual)) {
                                                valMatched = true;
                                                fileSuccesses.add("Found: " + actual);
                                                break;
                                            }
                                        }
                                    }
                                    if (valMatched) legacyPassed = true;
                                    else fileErrors.add("Value mismatch (Legacy)");
                                } else {
                                    legacyPassed = true;
                                    List<String> matchDescriptions = new ArrayList<>();
                                    for (int k = 0; k < nodes.getLength() && k < 5; k++) {
                                        matchDescriptions.add(formatNode(nodes.item(k)));
                                    }
                                    if (nodes.getLength() > 5) matchDescriptions.add("... (" + (nodes.getLength() - 5) + " more)");
                                    fileSuccesses.add("Found: " + String.join(", ", matchDescriptions));
                                }
                            } else {
                                // Logic for property fallback if node finding fails... (Simplified for edit)
                                // We are replacing standard logic blocks heavily, assume legacy logic handled failures internally via 'details' previously
                                // Converting legacy logic to update fileErrors/filePassed
                                fileErrors.add("XPath not found: " + xpathExpr);
                            }
                        } else { 
                            // FORBIDDEN logic
                            if (foundCount == 0) {
                                legacyPassed = true;
                            } else {
                                if (forbiddenValue != null) {
                                    boolean forbiddenFound = false;
                                    for (int i = 0; i < nodes.getLength(); i++) {
                                        String actual = nodes.item(i).getTextContent();
                                        if (resolveProperties) {
                                            actual = com.raks.aegis.util.PropertyResolver.resolve(actual, projectRoot);
                                        }
                                        if (forbiddenValue.equals(actual)) {
                                            forbiddenFound = true;
                                            allFoundItems.add("Forbidden: " + actual);
                                            break;
                                        }
                                    }
                                    if (!forbiddenFound) legacyPassed = true;
                                    else fileErrors.add("Forbidden value found");
                                } else {
                                    // Forbidden Attribute Check
                                    if (forbiddenAttrs != null && !forbiddenAttrs.isEmpty()) {
                                        for (int i = 0; i < nodes.getLength(); i++) {
                                            org.w3c.dom.Element e = (org.w3c.dom.Element) nodes.item(i);
                                            boolean attrFound = false;
                                            for (String fa : forbiddenAttrs) {
                                                if (e.hasAttribute(fa)) {
                                                    fileFoundItems.add(fa);
                                                    allFoundItems.add(fa);
                                                    attrFound = true;
                                                }
                                            }
                                            if (attrFound) legacyPassed = false; 
                                            // The legacy logic was stricter/different, but this approximates fail-fast
                                        }
                                        // If no attributes found on any nodes, then pass? 
                                        // Legacy logic failed if ANY found.
                                        if (fileFoundItems.isEmpty()) legacyPassed = true;
                                        else fileErrors.add("Forbidden attributes found: " + fileFoundItems);
                                    } else {
                                        // Generic forbidden element found
                                        fileErrors.add("Forbidden XPath found: " + xpathExpr);
                                        legacyPassed = false;
                                    }
                                }
                            }
                        }
                        
                        if (!legacyPassed) filePassed = false;
                    } 

                } catch (Exception e) {
                    filePassed = false;
                    details.add(projectRoot.relativize(file) + " [Parse Error: " + e.getMessage() + "]");
                }

                if (filePassed) {
                    passedFileCount++;
                    passedFilesList.add(projectRoot.relativize(file).toString());
                    successDetails.addAll(fileSuccesses);
                } else {
                    if (!fileErrors.isEmpty()) {
                        details.add(projectRoot.relativize(file) + " [" + String.join(", ", fileErrors) + "]");
                    }
                }
            }

            boolean uniqueCondition = evaluateMatchMode(matchMode, totalFiles, passedFileCount);

            String checkedFilesStr = matchingFiles.stream()
                    .map(p -> projectRoot.relativize(p).toString())
                    .collect(java.util.stream.Collectors.joining(", "));

            // Use passedFilesList for matchingFilesStr to consistently show filenames that passed the check.
            String matchingFilesStr = passedFilesList.isEmpty() ? null : String.join(", ", passedFilesList);

            String foundItemsStr = allFoundItems.isEmpty() ? null : String.join(", ", allFoundItems);

            if (uniqueCondition) {
                String defaultSuccess = String.format("XML Check passed for %s files. (Mode: %s, Passed: %d/%d)", mode, matchMode, passedFileCount, totalFiles);
                return CheckResult.pass(check.getRuleId(), check.getDescription(), getCustomSuccessMessage(check, defaultSuccess, checkedFilesStr, matchingFilesStr), checkedFilesStr, matchingFilesStr);
            } else {
                String technicalMsg = String.format("XML Check failed for %s. (Mode: %s, Passed: %d/%d).\n• %s", 
                                mode, matchMode, passedFileCount, totalFiles, 
                                details.isEmpty() ? "Pattern mismatch" : String.join("\n• ", details));
                return CheckResult.fail(check.getRuleId(), check.getDescription(), getCustomMessage(check, technicalMsg, checkedFilesStr, foundItemsStr, matchingFilesStr), checkedFilesStr, foundItemsStr, matchingFilesStr);
            }

        } catch (Exception e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Scan Error: " + e.getMessage());
        }
    }

    private String formatNode(org.w3c.dom.Node node) {
        if (node == null) return "null";
        if (node.getNodeType() == org.w3c.dom.Node.ATTRIBUTE_NODE) {
            return node.getNodeName() + "=\"" + node.getNodeValue() + "\"";
        } else if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            StringBuilder sb = new StringBuilder("<");
            sb.append(node.getNodeName());
            if (node.hasAttributes()) {
                org.w3c.dom.NamedNodeMap attrs = node.getAttributes();
                for (int i = 0; i < attrs.getLength(); i++) {
                    org.w3c.dom.Node attr = attrs.item(i);
                    sb.append(" ").append(attr.getNodeName()).append("=\"").append(attr.getNodeValue()).append("\"");
                }
            }
            sb.append(">");
            return sb.toString();
        } else {
            return node.getTextContent().trim();
        }
    }

    private CheckResult failConfig(Check check, String msg) {
        return CheckResult.fail(check.getRuleId(), check.getDescription(), "Config Error: " + msg);
    }
}
