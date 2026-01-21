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
        boolean resolveProperties = Boolean.parseBoolean(String.valueOf(params.getOrDefault("resolveProperties", "false")));
        String mode = (String) params.getOrDefault("mode", "EXISTS");
        String matchMode = (String) params.getOrDefault("matchMode", "ALL_FILES");

        if (filePatterns == null || filePatterns.isEmpty()) {
             return failConfig(check, "filePatterns required");
        }
        if (xpathExpr == null && 
            !params.containsKey("minVersions") && 
            !params.containsKey("exactVersions") && 
            !params.containsKey("requiredFields")) {
             return failConfig(check, "xpath or standard validation params (minVersions, etc.) required");
        }

        boolean includeLinkedConfig = Boolean.parseBoolean(String.valueOf(params.getOrDefault("includeLinkedConfig", "false")));
        List<Path> matchingFiles = findFiles(projectRoot, filePatterns, includeLinkedConfig);
        List<Path> searchRoots = com.raks.aegis.util.ProjectContextHelper.getEffectiveSearchRoots(projectRoot, this.linkedConfigPath, includeLinkedConfig);

        int passedFileCount = 0;
        int totalFiles = matchingFiles.size();
        List<String> details = new ArrayList<>();
        List<String> passedFilesList = new ArrayList<>();
        java.util.Set<String> successDetails = new java.util.LinkedHashSet<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); } catch (Exception ignore) {}

        for (Path file : matchingFiles) {
            boolean filePassed = true;
            List<String> fileSuccesses = new ArrayList<>();
            List<String> fileErrors = new ArrayList<>();
            
            Path currentRoot = searchRoots.stream().filter(file::startsWith).findFirst().orElse(projectRoot);
            String relativePath = currentRoot.relativize(file).toString().replace("\\", "/");
            if (currentRoot.equals(linkedConfigPath)) { relativePath = "[Config] " + relativePath; }

            try {
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(file.toFile());
                XPath xpath = XPathFactory.newInstance().newXPath();

                // Required Fields
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
                            boolean matched = false;
                            for (int i=0; i<nodes.getLength(); i++) {
                                String actual = nodes.item(i).getTextContent().trim();
                                if (resolveProperties) actual = resolve(actual, currentRoot, this.propertyResolutions);
                                if (actual.equals(expected)) { matched = true; fileSuccesses.add(xp + "=" + actual); break; }
                            }
                            if (!matched) { filePassed = false; fileErrors.add("Mismatch at " + xp); }
                        }
                    }
                }

                // Min Versions
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
                            boolean matched = false;
                            for (int i=0; i<nodes.getLength(); i++) {
                                String actual = nodes.item(i).getTextContent().trim();
                                if (resolveProperties) actual = resolve(actual, currentRoot, this.propertyResolutions);
                                if (compareValues(actual, minVer, "GTE", "SEMVER")) { matched = true; fileSuccesses.add(xp + "=" + actual); break; }
                            }
                            if (!matched) { filePassed = false; fileErrors.add("Version too low: " + xp); }
                        }
                    }
                }

                // Legacy/Xpath logic
                if (xpathExpr != null) {
                    NodeList nodes = (NodeList) xpath.evaluate(xpathExpr, doc, XPathConstants.NODESET);
                    if ("EXISTS".equalsIgnoreCase(mode)) {
                        if (nodes.getLength() == 0) { filePassed = false; fileErrors.add("XPath not found: " + xpathExpr); }
                        else fileSuccesses.add("Found: " + xpathExpr);
                    } else { // FORBIDDEN
                        if (nodes.getLength() > 0) { filePassed = false; fileErrors.add("Forbidden XPath found: " + xpathExpr); }
                    }
                }

            } catch (Exception e) {
                filePassed = false; fileErrors.add("Error: " + e.getMessage());
            }

            if (filePassed) {
                passedFileCount++; passedFilesList.add(relativePath); successDetails.addAll(fileSuccesses);
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
        boolean passed = evaluateMatchMode(matchMode, totalFiles, passedFileCount);

        if (passed) {
            String msg = String.format("Passed XML check (%d/%d files)", passedFileCount, totalFiles);
            return CheckResult.pass(check.getRuleId(), check.getDescription(), getCustomSuccessMessage(check, msg, checkedFilesStr, matchingFilesStr), checkedFilesStr, matchingFilesStr, this.propertyResolutions);
        } else {
            String msg = String.format("XML check failed. (Passed: %d/%d)\n• %s", passedFileCount, totalFiles, String.join("\n• ", details));
            return CheckResult.fail(check.getRuleId(), check.getDescription(), getCustomMessage(check, msg, checkedFilesStr, null, matchingFilesStr), checkedFilesStr, null, matchingFilesStr, this.propertyResolutions);
        }
    }

    private CheckResult failConfig(Check check, String msg) {
        return CheckResult.fail(check.getRuleId(), check.getDescription(), "Config Error: " + msg);
    }
}
