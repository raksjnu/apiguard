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

            List<String> passedFilesList = new ArrayList<>();
            java.util.Set<String> successDetails = new java.util.HashSet<>();

            for (Path file : matchingFiles) {
                boolean filePassed = false;
                List<String> fileSuccesses = new ArrayList<>();

                try {
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(file.toFile());
                    XPath xpath = XPathFactory.newInstance().newXPath();
                    NodeList nodes = (NodeList) xpath.evaluate(xpathExpr, doc, XPathConstants.NODESET);

                    int foundCount = nodes.getLength();
                    java.util.Set<String> fileFoundItems = new java.util.HashSet<>();

                    if (attributeMatch != null && foundCount > 0) {
                        // Logic for attribute match can be enhanced if needed
                    }

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
                                if (valMatched) filePassed = true;
                                else details.add(projectRoot.relativize(file) + " [Value mismatch]");
                            } else {
                                filePassed = true;
                                fileSuccesses.add("XPath Found: " + xpathExpr);
                            }
                        } else {

                            logger.debug("Attempting property resolution fallback for XPath: {}", xpathExpr);
                            try {
                                String[] subPaths = xpathExpr.split(" \\| ");
                                boolean propertyMatchFound = false;
                                for (String subPath : subPaths) {
                                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("local-name\\(\\)\\s*=\\s*'([^']+)'\\s+and\\s+@([^=]+)='([^']+)'").matcher(subPath);
                                    while (m.find()) {
                                        String elemToken = m.group(1);
                                        String attrToken = m.group(2);
                                        String expectedToken = m.group(3);
                                        String relaxedXpath = "//*[local-name()='" + elemToken + "' and @" + attrToken + "]";
                                        NodeList relaxedNodes = (NodeList) xpath.evaluate(relaxedXpath, doc, XPathConstants.NODESET);
                                        for (int i = 0; i < relaxedNodes.getLength(); i++) {
                                            org.w3c.dom.Element e = (org.w3c.dom.Element) relaxedNodes.item(i);
                                            String rawVal = e.getAttribute(attrToken);
                                            String resolved = com.raks.aegis.util.PropertyResolver.resolve(rawVal, projectRoot);
                                            if (expectedToken.equals(resolved)) {
                                                propertyMatchFound = true;
                                                addPropertyResolution(rawVal, resolved);
                                                fileSuccesses.add(String.format("Found %s (Resolved: %s)", rawVal, resolved));
                                                break;
                                            }
                                        }
                                        if (propertyMatchFound) break;
                                    }
                                    if (propertyMatchFound) break;
                                }
                                if (propertyMatchFound) filePassed = true;
                                else details.add(projectRoot.relativize(file) + " [XPath not found]");
                            } catch (Exception ex) {
                                details.add(projectRoot.relativize(file) + " [XPath not found]");
                            }
                        }
                    } else { 
                        if (foundCount == 0) {
                            filePassed = true;
                        } else {
                            if (forbiddenValue != null) {
                                boolean forbiddenFound = false;
                                for (int i = 0; i < nodes.getLength(); i++) {
                                    String actual = nodes.item(i).getTextContent();
                                    String comparisonValue = actual;
                                    if (resolveProperties) {
                                        comparisonValue = com.raks.aegis.util.PropertyResolver.resolve(actual, projectRoot);
                                        addPropertyResolution(actual, comparisonValue);
                                    }
                                    if (forbiddenValue.equals(comparisonValue)) {
                                        forbiddenFound = true;
                                        break;
                                    }
                                }
                                if (!forbiddenFound) filePassed = true;
                                else details.add(projectRoot.relativize(file) + " [Forbidden value found]");
                            } else {

                                if (forbiddenAttrs != null && !forbiddenAttrs.isEmpty()) {
                                    for (int i = 0; i < nodes.getLength(); i++) {
                                        org.w3c.dom.Element e = (org.w3c.dom.Element) nodes.item(i);
                                        for (String fa : forbiddenAttrs) {
                                            if (e.hasAttribute(fa)) {
                                                fileFoundItems.add(fa);
                                                allFoundItems.add(fa);
                                            }
                                        }
                                    }
                                }
                                String foundStr = fileFoundItems.isEmpty() ? String.valueOf(foundCount) + " matches" : "Found: " + String.join(", ", fileFoundItems);
                                details.add(projectRoot.relativize(file) + " [XPath found (" + foundStr + ")]");
                            }
                        }
                    }

                } catch (Exception e) {
                    details.add(projectRoot.relativize(file) + " [Parse Error: " + e.getMessage() + "]");
                }

                if (filePassed) {
                    passedFileCount++;
                    passedFilesList.add(projectRoot.relativize(file).toString());
                    successDetails.addAll(fileSuccesses);
                }
            }

            boolean uniqueCondition = evaluateMatchMode(matchMode, totalFiles, passedFileCount);

            String checkedFilesStr = matchingFiles.stream()
                    .map(p -> projectRoot.relativize(p).toString())
                    .collect(java.util.stream.Collectors.joining(", "));

            // Use successDetails for positive matches (e.g., Found: XYZ).
            // For FORBIDDEN checks where we passed because we found nothing, successDetails is empty -> N/A.
            String matchingFilesStr = successDetails.isEmpty() ? null : String.join(", ", successDetails);

            String foundItemsStr = allFoundItems.isEmpty() ? null : String.join(", ", allFoundItems);

            if (uniqueCondition) {
                String defaultSuccess = String.format("XML Check passed for %s files. (Mode: %s, Passed: %d/%d)", mode, matchMode, passedFileCount, totalFiles);
                return CheckResult.pass(check.getRuleId(), check.getDescription(), getCustomSuccessMessage(check, defaultSuccess, checkedFilesStr, matchingFilesStr), checkedFilesStr, matchingFilesStr);
            } else {
                String technicalMsg = String.format("XML Check failed for %s. (Mode: %s, Passed: %d/%d). Failures:\n• %s", 
                                mode, matchMode, passedFileCount, totalFiles, 
                                details.isEmpty() ? "Pattern mismatch" : String.join("\n• ", details));
                return CheckResult.fail(check.getRuleId(), check.getDescription(), getCustomMessage(check, technicalMsg, checkedFilesStr, foundItemsStr), checkedFilesStr, foundItemsStr);
            }

        } catch (Exception e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Scan Error: " + e.getMessage());
        }
    }

    private CheckResult failConfig(Check check, String msg) {
        return CheckResult.fail(check.getRuleId(), check.getDescription(), "Config Error: " + msg);
    }
}
