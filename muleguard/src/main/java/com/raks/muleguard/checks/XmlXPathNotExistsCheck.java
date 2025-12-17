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
 * XML XPath Not Exists Check - Validates that forbidden XPath expressions do
 * NOT match in XML files.
 * Fails if forbidden XPath expressions DO match.
 * 
 * Supports:
 * - Multiple XPath expressions with individual failure messages
 * - File pattern matching
 * - Property resolution (${property} references)
 * 
 * @author Rakesh Kumar (raksjnu@gmail.com)
 */
public class XmlXPathNotExistsCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) check.getParams().get("filePatterns");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> xpathExpressions = (List<Map<String, String>>) check.getParams()
                .get("xpathExpressions");
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

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            matchingFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesAnyPattern(path, filePatterns, projectRoot))
                    .filter(path -> !shouldIgnorePath(projectRoot, path))  // Filter ignored folders
                    .toList();

            if (matchingFiles.isEmpty()) {
                // No files to check - pass (nothing forbidden found)
                return CheckResult.pass(check.getRuleId(), check.getDescription(),
                        "No files found matching patterns (nothing to validate)");
            }

            for (Path file : matchingFiles) {
                validateXPathsNotInFile(file, xpathExpressions, propertyResolution, projectRoot, failures);
            }

        } catch (IOException e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error scanning files: " + e.getMessage());
        }

        // Fail if ANY forbidden XPath was found
        if (failures.isEmpty()) {
            String fileList = matchingFiles.stream()
                    .map(projectRoot::relativize)
                    .map(Path::toString)
                    .collect(java.util.stream.Collectors.joining("; "));
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    "No forbidden XPath expressions found\nFiles validated: " + fileList);
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Forbidden XPath expressions found:\n• " + String.join("\n• ", failures));
        }
    }

    private void validateXPathsNotInFile(Path file, List<Map<String, String>> xpathExpressions,
            boolean propertyResolution, Path projectRoot, List<String> failures) {
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
                        "Forbidden XPath found: " + xpathString);

                if (xpathString == null || xpathString.isEmpty()) {
                    continue; // Skip invalid XPath
                }

                try {
                    NodeList nodes = (NodeList) xpath.evaluate(xpathString, doc, XPathConstants.NODESET);

                    if (nodes.getLength() > 0) {
                        // Forbidden XPath found - this is a failure
                        failures.add(file.getFileName().toString() + ": " + failureMessage +
                                " (found " + nodes.getLength() + " occurrence(s))");
                    }
                } catch (Exception e) {
                    failures.add("XPath evaluation error in " + file.getFileName().toString() +
                            ": " + xpathString + " - " + e.getMessage());
                }
            }

        } catch (Exception e) {
            failures.add("Error parsing XML file " + file.getFileName().toString() + ": " + e.getMessage());
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
