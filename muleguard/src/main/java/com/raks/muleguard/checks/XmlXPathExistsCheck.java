package com.raks.muleguard.checks;

import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * XML XPath Exists Check - Validates that XPath expressions match in XML files.
 * Fails if required XPath expressions do NOT match.
 * 
 * Supports:
 * - Multiple XPath expressions with individual failure messages
 * - AND/OR logic (requireAll parameter)
 * - Property resolution (${property} references)
 * - File pattern matching
 * 
 * @author Rakesh Kumar (raksjnu@gmail.com)
 */
public class XmlXPathExistsCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) check.getParams().get("filePatterns");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> xpathExpressions = (List<Map<String, String>>) check.getParams()
                .get("xpathExpressions");
        Boolean requireAll = (Boolean) check.getParams().getOrDefault("requireAll", true);
        Boolean propertyResolution = (Boolean) check.getParams().getOrDefault("propertyResolution", false);

        // Validation
        if (filePatterns == null || filePatterns.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'filePatterns' parameter is required");
        }

        if (xpathExpressions == null || xpathExpressions.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'xpathExpressions' parameter is required");
        }

        List<String> failures = new ArrayList<>();
        List<Path> matchingFiles = new ArrayList<>();
        List<String> successes = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            matchingFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesAnyPattern(path, filePatterns, projectRoot))
                    .filter(path -> !shouldIgnorePath(projectRoot, path))  // Filter ignored folders
                    .toList();

            if (matchingFiles.isEmpty()) {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        "No files found matching patterns: " + filePatterns);
            }

            for (Path file : matchingFiles) {
                validateXPathsInFile(file, xpathExpressions, requireAll, propertyResolution,
                        projectRoot, failures, successes);
            }

        } catch (IOException e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error scanning files: " + e.getMessage());
        }

        // Determine result based on requireAll
        if (requireAll) {
            // ALL XPath expressions must match
            if (failures.isEmpty()) {
            String fileList = matchingFiles.stream()
                    .map(projectRoot::relativize)
                    .map(Path::toString)
                    .collect(java.util.stream.Collectors.joining("; "));
                return CheckResult.pass(check.getRuleId(), check.getDescription(),
                        "All required XPath expressions found\nFiles validated: " + fileList);
            } else {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        "XPath validation failures:\n• " + String.join("\n• ", failures));
            }
        } else {
            // At least ONE XPath expression must match
            if (!successes.isEmpty()) {
                return CheckResult.pass(check.getRuleId(), check.getDescription(),
                        "At least one required XPath expression found");
            } else {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        "No XPath expressions matched:\n• " + String.join("\n• ", failures));
            }
        }
    }

    private void validateXPathsInFile(Path file, List<Map<String, String>> xpathExpressions,
            boolean requireAll, boolean propertyResolution,
            Path projectRoot, List<String> failures, List<String> successes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file.toFile());

            XPathFactory xPathFactory = XPathFactory.newInstance();
            XPath xpath = xPathFactory.newXPath();

            for (Map<String, String> xpathExpr : xpathExpressions) {
                String xpathString = xpathExpr.get("xpath");
                String failureMessage = xpathExpr.getOrDefault("failureMessage",
                        "XPath not found: " + xpathString);

                if (xpathString == null || xpathString.isEmpty()) {
                    failures.add("Invalid XPath expression (empty) in file: " + projectRoot.relativize(file));
                    continue;
                }

                try {
                    NodeList nodes = (NodeList) xpath.evaluate(xpathString, doc, XPathConstants.NODESET);

                    if (nodes.getLength() > 0) {
                        successes.add("XPath found in " + projectRoot.relativize(file) + ": " + xpathString);
                    } else {
                        failures.add(failureMessage + " in file: " + projectRoot.relativize(file));
                    }
                } catch (Exception e) {
                    failures.add("XPath evaluation error in " + projectRoot.relativize(file) +
                            ": " + xpathString + " - " + e.getMessage());
                }
            }

        } catch (Exception e) {
            failures.add("Error parsing XML file " + projectRoot.relativize(file) + ": " + e.getMessage());
        }
    }

    private boolean matchesAnyPattern(Path path, List<String> patterns, Path projectRoot) {
        String relativePath = projectRoot.relativize(path).toString().replace("\\", "/");

        for (String pattern : patterns) {
            if (matchesPattern(relativePath, pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPattern(String path, String pattern) {
        // Convert glob pattern to regex
        String regex = pattern
                .replace(".", "\\.")
                .replace("**/", ".*")
                .replace("**", ".*")
                .replace("*", "[^/]*")
                .replace("?", ".");

        return path.matches(regex);
    }
}
