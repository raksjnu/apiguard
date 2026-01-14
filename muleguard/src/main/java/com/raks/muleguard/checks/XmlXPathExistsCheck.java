package com.raks.muleguard.checks;

import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;
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
 * Checks for existence of XPath expressions. By default fails if NOT found.
 * Can be configured to Fail if FOUND (failIfFound: true).
 * Can be configured to match ANY file instead of ALL (matchMode: ANY_FILE).
 */
public class XmlXPathExistsCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        if (!isRuleApplicable(projectRoot, check)) {
             return CheckResult.pass(check.getRuleId(), check.getDescription(), "Rule skipped: Pre-conditions not met.");
        }

        Map<String, Object> params = check.getParams();
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) params.get("filePatterns");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> xpathExpressions = (List<Map<String, String>>) params.get("xpathExpressions");
        
        String matchMode = (String) params.getOrDefault("matchMode", "ALL_FILES");
        boolean failIfFound = (Boolean) params.getOrDefault("failIfFound", false);

        if (filePatterns == null || filePatterns.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Configuration error: 'filePatterns' is required");
        }

        List<String> failures = new ArrayList<>();
        List<Path> matchingFiles = new ArrayList<>();
        boolean foundInAnyFile = false; // Tracks if we found the element in AT LEAST one file (for ANY_FILE mode)

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            matchingFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesAnyPattern(path, filePatterns, projectRoot))
                    .filter(path -> !shouldIgnorePath(projectRoot, path))
                    .toList();
            



            if (matchingFiles.isEmpty()) {

            }

            for (Path file : matchingFiles) {


                
                for (Map<String, String> expr : xpathExpressions) {
                    String xpath = expr.get("xpath");
                    String failureMsg = expr.get("failureMessage");
                    boolean exists = checkXPath(file, xpath);
                    
                    if (failIfFound) {

                        if (exists) {
                            failures.add("Forbidden element found matching '" + xpath + "' in " + projectRoot.relativize(file));
                        }
                    } else {

                        if (exists) {
                            foundInAnyFile = true;
                        } else {
                            if ("ALL_FILES".equals(matchMode)) {
                                failures.add(failureMsg + " (Missing in " + projectRoot.relativize(file) + ")");
                            }
                        }
                    }
                }
            }
            

            if (!failIfFound && "ANY_FILE".equals(matchMode)) {
                if (!foundInAnyFile && !matchingFiles.isEmpty()) {


                     String msg = xpathExpressions.isEmpty() ? "Element not found" : xpathExpressions.get(0).get("failureMessage");
                     failures.add(msg + " (Not found in any matching files)");
                }
            }

        } catch (Exception e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Error scanning files: " + e.getMessage());
        }

        if (failures.isEmpty()) {
             return CheckResult.pass(check.getRuleId(), check.getDescription(), "XPath checks passed.");
        } else {
             return CheckResult.fail(check.getRuleId(), check.getDescription(), "Failures:\n• " + String.join("\n• ", failures));
        }
    }

    private boolean checkXPath(Path file, String expression) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file.toFile());
            XPath xpath = XPathFactory.newInstance().newXPath();
            NodeList nodes = (NodeList) xpath.evaluate(expression, doc, XPathConstants.NODESET);
            return nodes.getLength() > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
