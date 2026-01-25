package com.raks.aegis.checks;

import com.raks.aegis.model.Check;
import com.raks.aegis.model.CheckResult;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class XmlGenericCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        if (!isRuleApplicable(projectRoot, check)) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(), "Pre-conditions not met.");
        }

        Map<String, Object> params = getEffectiveParams(check);
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) params.get("filePatterns");
        String xpathExpr = (String) params.get("xpath");
        String mode = (String) params.getOrDefault("mode", "EXISTS");
        String matchMode = (String) params.getOrDefault("matchMode", "ALL_FILES");

        if (filePatterns == null || filePatterns.isEmpty()) {
             return failConfig(check, "filePatterns required");
        }
        
        // Relaxed validation: allow elementContentPairs, forbiddenTokens, or xpathExpressions
        if (xpathExpr == null && 
            !params.containsKey("minVersions") && 
            !params.containsKey("exactVersions") && 
            !params.containsKey("requiredFields") &&
            !params.containsKey("elementContentPairs") &&
            !params.containsKey("forbiddenTokens") &&
            !params.containsKey("xpathExpressions") &&
            !params.containsKey("elements")) {
             return failConfig(check, "xpath or validation parameters required (e.g., forbiddenTokens, elementContentPairs)");
        }

        boolean includeLinkedConfig = Boolean.parseBoolean(String.valueOf(params.getOrDefault("includeLinkedConfig", "false")));
        List<Path> matchingFiles = findFiles(projectRoot, filePatterns, includeLinkedConfig);
        List<Path> searchRoots = com.raks.aegis.util.ProjectContextHelper.getEffectiveSearchRoots(projectRoot, this.linkedConfigPath, includeLinkedConfig);

        int passedFileCount = 0;
        int totalFiles = matchingFiles.size();
        List<String> details = new ArrayList<>();
        List<String> passedFilesList = new ArrayList<>();
        java.util.Set<String> successDetails = new java.util.LinkedHashSet<>();


        java.util.Set<Path> matchedPathsSet = new java.util.HashSet<>();
        for (Path file : matchingFiles) {
            boolean filePassed = true;
            List<String> fileSuccesses = new ArrayList<>();
            List<String> fileErrors = new ArrayList<>();
            
            Path currentRoot = searchRoots.stream().filter(file::startsWith).findFirst().orElse(projectRoot);
            String relativePath = currentRoot.relativize(file).toString().replace("\\", "/");
            if (currentRoot.equals(linkedConfigPath)) { relativePath = "[Config] " + relativePath; }

            try {
                Document doc = parseXml(file, params);
                XPath xpath = XPathFactory.newInstance().newXPath();

                // 1. Required Fields
                @SuppressWarnings("unchecked")
                Map<String, String> requiredFields = (Map<String, String>) params.get("requiredFields");
                if (requiredFields != null) {
                    for (Map.Entry<String, String> entry : requiredFields.entrySet()) {
                        String xp = entry.getKey();
                        String expected = entry.getValue();
                        NodeList nodes = (NodeList) xpath.evaluate(xp, doc, XPathConstants.NODESET);
                        if (nodes == null || nodes.getLength() == 0) {
                            filePassed = false; fileErrors.add("Missing: " + xp);
                        } else {
                            for (int i=0; i<nodes.getLength(); i++) {
                                String rawValue = nodes.item(i).getTextContent().trim();
                                java.util.Set<String> allValues = resolveAll(rawValue, currentRoot);
                                boolean anyMatch = false;
                                for (String actual : allValues) {
                                    if (actual.equals(expected)) {
                                        anyMatch = true;
                                        fileSuccesses.add(xp + "=" + actual);
                                    } else {
                                        filePassed = false;
                                        fileErrors.add("Resolution of " + xp + " mismatch: '" + actual + "' (Expected: '" + expected + "')");
                                    }
                                }
                                if (!anyMatch && allValues.isEmpty()) { 
                                     filePassed = false; fileErrors.add("Mismatch at " + xp);
                                }
                            }
                        }
                    }
                }

                // 2. Min Versions
                @SuppressWarnings("unchecked")
                Map<String, String> minVersions = (Map<String, String>) params.get("minVersions");
                if (minVersions != null) {
                    for (Map.Entry<String, String> entry : minVersions.entrySet()) {
                        String xp = entry.getKey();
                        String minVer = entry.getValue();
                        NodeList nodes = (NodeList) xpath.evaluate(xp, doc, XPathConstants.NODESET);
                        if (nodes == null || nodes.getLength() == 0) {
                            filePassed = false; fileErrors.add("Missing Version: " + xp);
                        } else {
                            for (int i=0; i<nodes.getLength(); i++) {
                                String rawValue = nodes.item(i).getTextContent().trim();
                                java.util.Set<String> allValues = resolveAll(rawValue, currentRoot);
                                for (String actual : allValues) {
                                    if (compareValues(actual, minVer, "GTE", "SEMVER")) {
                                        fileSuccesses.add(xp + "=" + actual);
                                    } else {
                                        filePassed = false;
                                        fileErrors.add("Resolution of " + xp + " too low: '" + actual + "' (Min: '" + minVer + "')");
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Element Content Pairs
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> contentPairs = (List<Map<String, Object>>) params.get("elementContentPairs");
                if (contentPairs != null) {
                    for (Map<String, Object> pair : contentPairs) {
                        String el = (String) pair.get("element");
                        String localName = (el != null && el.contains(":")) ? el.substring(el.lastIndexOf(":") + 1) : el;
                        String xp = "//*[local-name()='" + localName + "']";
                        @SuppressWarnings("unchecked")
                        List<String> fTokens = (List<String>) pair.get("forbiddenTokens");
                        @SuppressWarnings("unchecked")
                        List<String> rTokens = (List<String>) pair.get("requiredTokens");

                        NodeList nodes = (NodeList) xpath.evaluate(xp, doc, XPathConstants.NODESET);
                        if (nodes != null && nodes.getLength() > 0) {
                            for (int i=0; i<nodes.getLength(); i++) {
                                String rawValue = nodes.item(i).getTextContent().trim();
                                if (localName.equals("config")) {
                                    String sv = ((org.w3c.dom.Element)nodes.item(i)).getAttribute("soapVersion");
                                    if (sv != null && !sv.isEmpty()) rawValue = sv;
                                }

                                java.util.Set<String> allValues = resolveAll(rawValue, currentRoot);
                                for (String actualValue : allValues) {
                                    boolean thisValuePassed = true;
                                    if (fTokens != null) {
                                        for (String ft : fTokens) {
                                            if (actualValue.contains(ft)) {
                                                filePassed = false;
                                                thisValuePassed = false;
                                                fileErrors.add("Forbidden token '" + ft + "' found in <" + el + ">: " + actualValue);
                                            }
                                        }
                                    }
                                    if (rTokens != null) {
                                        boolean found = false;
                                        for (String rt : rTokens) {
                                            if (actualValue.contains(rt)) { found = true; break; }
                                        }
                                        if (!found) {
                                            filePassed = false;
                                            thisValuePassed = false;
                                            fileErrors.add("None of required tokens " + rTokens + " found in <" + el + ">: " + actualValue);
                                        }
                                    }
                                    if (thisValuePassed) {
                                        fileSuccesses.add(el + " : " + actualValue);
                                    }
                                }
                            }
                        }
                    }
                }
                // 4. Legacy/Xpath logic
                if (xpathExpr != null) {
                    NodeList nodes = (NodeList) xpath.evaluate(xpathExpr, doc, XPathConstants.NODESET);
                    String forbiddenValue = (String) params.get("forbiddenValue");
                    String expectedValue = (String) params.get("expectedValue");
                    @SuppressWarnings("unchecked")
                    List<String> forbiddenTokens = (List<String>) params.get("forbiddenTokens");
                    String operator = (String) params.getOrDefault("operator", "EQ");
                    String valueType = (String) params.getOrDefault("valueType", "STRING");
                    boolean wholeWord = Boolean.parseBoolean(String.valueOf(params.getOrDefault("wholeWord", "false")));

                    if ("EXISTS".equalsIgnoreCase(mode) || "OPTIONAL_MATCH".equalsIgnoreCase(mode)) {
                        if (nodes.getLength() == 0) { 
                            if (!"OPTIONAL_MATCH".equalsIgnoreCase(mode)) {
                                filePassed = false; 
                                fileErrors.add("XPath not found: " + xpathExpr); 
                            }
                        } else {
                            for (int i=0; i<nodes.getLength(); i++) {
                                String rawValue = nodes.item(i).getTextContent().trim();
                                java.util.Set<String> allValues = resolveAll(rawValue, currentRoot);
                                if (expectedValue != null) {
                                    for (String actual : allValues) {
                                        if (compareValues(actual, expectedValue, operator, valueType)) { 
                                            fileSuccesses.add(String.format("%s matches '%s' (Actual: '%s')", xpathExpr, expectedValue, actual)); 
                                        } else {
                                            filePassed = false;
                                            fileErrors.add("Resolution of " + xpathExpr + " mismatch: '" + actual + "' (Expected: '" + expectedValue + "')");
                                        }
                                    }
                                } else {
                                    fileSuccesses.add(String.format("Found %s (Resolution: %s)", xpathExpr, allValues.toString()));
                                }
                            }
                        }
                    } else { // NOT_EXISTS / FORBIDDEN
                        if (nodes.getLength() > 0) {
                            // If no specific content/token values are forbidden, then the EXISTENCE of the node itself is forbidden.
                            if (forbiddenValue == null && forbiddenTokens == null) {
                                filePassed = false;
                                fileErrors.add("Forbidden XPath found: " + xpathExpr);
                            } else {
                                for (int i=0; i<nodes.getLength(); i++) {
                                    String rawValue = nodes.item(i).getTextContent().trim();
                                    java.util.Set<String> allValues = resolveAll(rawValue, currentRoot);
                                    for (String actual : allValues) {
                                        if (forbiddenValue != null && compareValues(actual, forbiddenValue, operator, valueType)) {
                                            filePassed = false;
                                            fileErrors.add("Forbidden value '" + forbiddenValue + "' found at resolution: '" + actual + "' for XPath: " + xpathExpr);
                                        }
                                        if (forbiddenTokens != null) {
                                            for (String ft : forbiddenTokens) {
                                                if (com.raks.aegis.util.CheckHelper.isTokenPresent(actual, ft, wholeWord, false, true)) {
                                                    filePassed = false;
                                                    fileErrors.add("Forbidden token '" + ft + "' found at resolution: '" + actual + "' for XPath: " + xpathExpr);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            } catch (Exception e) {
                filePassed = false; fileErrors.add("Error: " + e.getMessage());
            }

            if (filePassed) {
                passedFileCount++; passedFilesList.add(relativePath); successDetails.addAll(fileSuccesses);
                if (xpathExpr != null) {
                    // Re-evaluate or just use a flag? It's easier to just add it here if it passed and wasn't the "optional but missing" case.
                    // If filePassed is true and it's OPTIONAL_MATCH, it could be missing.
                    // But if fileSuccesses is not empty, it means we found something or it passed.
                    if (!fileSuccesses.isEmpty()) {
                        matchedPathsSet.add(file);
                    }
                }
            } else {
                details.add(relativePath + " [" + String.join(", ", fileErrors) + "]");
            }
        }

        String checkedFilesStr = String.join(", ", matchingFiles.stream().map(p -> {
            Path r = searchRoots.stream().filter(p::startsWith).findFirst().orElse(projectRoot);
            String rel = r.relativize(p).toString().replace("\\", "/");
            return r.equals(linkedConfigPath) ? "[Config] " + rel : rel;
        }).toList());

        String matchingFilesStr = passedFilesList.isEmpty() ? null : String.join(", ", passedFilesList);
        String foundItemsStr = successDetails.isEmpty() ? null : String.join(", ", successDetails);
        boolean passed = evaluateMatchMode(matchMode, totalFiles, passedFileCount);

        if (passed) {
            String msg = String.format("Passed XML check (%d/%d files)", passedFileCount, totalFiles);
            return finalizePass(check, msg, checkedFilesStr, foundItemsStr, matchingFilesStr, matchedPathsSet);
        } else {
            String failDetailsStr = String.join("; ", details);
            String msg = String.format("XML check failed. (Passed: %d/%d)\n• %s", passedFileCount, totalFiles, String.join("\n• ", details));
            return finalizeFail(check, msg, checkedFilesStr, failDetailsStr, matchingFilesStr, matchedPathsSet);
        }
    }

    private CheckResult failConfig(Check check, String msg) {
        return CheckResult.fail(check.getRuleId(), check.getDescription(), "Config Error: " + msg);
    }
}
