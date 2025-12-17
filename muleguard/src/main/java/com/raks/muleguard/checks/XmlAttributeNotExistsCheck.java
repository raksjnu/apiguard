package com.raks.muleguard.checks;

import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * XML Attribute Not Exists Check - Validates that forbidden attributes do NOT
 * exist in XML elements.
 * Fails if forbidden attributes ARE found.
 * 
 * Supports:
 * - Multiple elements
 * - Multiple forbidden attributes per element
 * - Case-sensitive/insensitive matching
 * 
 * @author Rakesh Kumar (raksjnu@gmail.com)
 */
public class XmlAttributeNotExistsCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) check.getParams().get("filePatterns");
        @SuppressWarnings("unchecked")
        List<String> elements = (List<String>) check.getParams().get("elements");
        @SuppressWarnings("unchecked")
        List<String> forbiddenAttributes = (List<String>) check.getParams().get("forbiddenAttributes");

        // Validation
        if (filePatterns == null || filePatterns.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'filePatterns' parameter is required");
        }

        if (elements == null || elements.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'elements' parameter is required");
        }

        if (forbiddenAttributes == null || forbiddenAttributes.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'forbiddenAttributes' parameter is required");
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
                validateForbiddenAttributes(file, elements, forbiddenAttributes, projectRoot, failures);
            }

        } catch (IOException e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error scanning files: " + e.getMessage());
        }

        if (failures.isEmpty()) {
            String fileList = matchingFiles.stream()
                    .map(projectRoot::relativize)
                    .map(Path::toString)
                    .collect(java.util.stream.Collectors.joining("; "));
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    "No forbidden attributes found\nFiles validated: " + fileList);
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Forbidden attributes found:\n• " + String.join("\n• ", failures));
        }
    }

    private void validateForbiddenAttributes(Path file, List<String> elements, List<String> forbiddenAttributes,
            Path projectRoot, List<String> failures) {
        try {
            Document doc = parseXml(file);

            for (String elementName : elements) {
                NodeList nodeList = doc.getElementsByTagName(elementName);

                for (int i = 0; i < nodeList.getLength(); i++) {
                    Element element = (Element) nodeList.item(i);

                    for (String attrName : forbiddenAttributes) {
                        if (element.hasAttribute(attrName)) {
                            failures.add(String.format("Forbidden attribute '%s' found on element '%s' in file: %s",
                                    attrName, elementName, projectRoot.relativize(file)));
                        }
                    }
                }
            }
        } catch (Exception e) {
            failures.add("Error parsing XML file " + projectRoot.relativize(file) + ": " + e.getMessage());
        }
    }

    private Document parseXml(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(file.toFile());
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
        String regex = pattern
                .replace(".", "\\.")
                .replace("**/", ".*")
                .replace("**", ".*")
                .replace("*", "[^/]*")
                .replace("?", ".");

        return path.matches(regex);
    }
}
