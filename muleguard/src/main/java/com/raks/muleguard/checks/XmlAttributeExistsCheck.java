package com.raks.muleguard.checks;

import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Checks if specific attributes exist on specific XML elements.
 */
public class XmlAttributeExistsCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        if (!isRuleApplicable(projectRoot, check)) {
             return CheckResult.pass(check.getRuleId(), check.getDescription(), "Rule skipped: Pre-conditions not met.");
        }

        Map<String, Object> params = check.getParams();
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) params.get("filePatterns");
        boolean propertyResolution = (Boolean) params.getOrDefault("propertyResolution", false);
        boolean caseSensitive = (Boolean) params.getOrDefault("caseSensitive", true);
        

        boolean checkIfElementExists = (Boolean) params.getOrDefault("checkIfElementExists", true);


        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elementAttributeSets = (List<Map<String, Object>>) params.get("elementAttributeSets");


        @SuppressWarnings("unchecked")
        List<String> simpleElements = (List<String>) params.get("elements");
        @SuppressWarnings("unchecked")
        List<String> simpleAttributes = (List<String>) params.get("attributes");

        if (filePatterns == null || filePatterns.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Configuration error: 'filePatterns' is required");
        }

        List<String> failures = new ArrayList<>();
        List<Path> matchingFiles = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            matchingFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesAnyPattern(path, filePatterns, projectRoot))
                    .filter(path -> !shouldIgnorePath(projectRoot, path))
                    .toList();

            for (Path file : matchingFiles) {
                if (elementAttributeSets != null && !elementAttributeSets.isEmpty()) {
                    validateElementAttributeSets(file, elementAttributeSets, checkIfElementExists, propertyResolution, caseSensitive, failures, projectRoot);
                } else if (simpleElements != null && simpleAttributes != null) {
                    validateSimpleElements(file, simpleElements, simpleAttributes, checkIfElementExists, propertyResolution, caseSensitive, failures, projectRoot);
                }
            }

        } catch (Exception e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Error scanning files: " + e.getMessage());
        }

        if (failures.isEmpty()) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(), "All XML attribute checks passed.");
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Validation failures:\n• " + String.join("\n• ", failures));
        }
    }

    private void validateElementAttributeSets(Path file, List<Map<String, Object>> sets, boolean checkIfElementExists, 
                                              boolean propertyResolution, boolean caseSensitive, List<String> failures, Path projectRoot) {
        try {
            Document doc = parseXml(file);
            for (Map<String, Object> set : sets) {
                String elementName = (String) set.get("element");
                @SuppressWarnings("unchecked")
                Map<String, String> attributeExpectedValues = (Map<String, String>) set.get("attributes");

                NodeList nodes = doc.getElementsByTagName(elementName);
                if (nodes.getLength() == 0) {
                     if (checkIfElementExists) {
                         failures.add(String.format("Element '%s' not found in file: %s", elementName, projectRoot.relativize(file)));
                     }
                     continue;
                }

                for (int i = 0; i < nodes.getLength(); i++) {
                    Element element = (Element) nodes.item(i);
                    for (Map.Entry<String, String> entry : attributeExpectedValues.entrySet()) {
                        String attrName = entry.getKey();
                        String expectedValue = entry.getValue(); // Regex or exact

                        if (!element.hasAttribute(attrName)) {
                            failures.add(String.format("Element '%s' missing attribute '%s' in file: %s", elementName, attrName, projectRoot.relativize(file)));
                        } else {
                            String actualValue = element.getAttribute(attrName);
                            if (expectedValue != null && !matches(actualValue, expectedValue, caseSensitive)) {
                                 failures.add(String.format("Attribute '%s' in element '%s' value mismatch. Expected pattern: '%s', Found: '%s' in file: %s", 
                                     attrName, elementName, expectedValue, actualValue, projectRoot.relativize(file)));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            failures.add("Error parsing " + projectRoot.relativize(file) + ": " + e.getMessage());
        }
    }

    private void validateSimpleElements(Path file, List<String> elements, List<String> attributes, boolean checkIfElementExists,
                                        boolean propertyResolution, boolean caseSensitive, List<String> failures, Path projectRoot) {
         try {
            Document doc = parseXml(file);
            for (String elementName : elements) {
                NodeList nodes = doc.getElementsByTagName(elementName);
                 if (nodes.getLength() == 0) {
                     if (checkIfElementExists) {
                         failures.add(String.format("Element '%s' not found in file: %s", elementName, projectRoot.relativize(file)));
                     }
                     continue;
                }
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element element = (Element) nodes.item(i);
                    for (String attrName : attributes) {
                         if (!element.hasAttribute(attrName)) {
                            failures.add(String.format("Element '%s' missing attribute '%s' in file: %s", elementName, attrName, projectRoot.relativize(file)));
                         }
                    }
                }
            }
         } catch (Exception e) {
             failures.add("Error parsing " + projectRoot.relativize(file) + ": " + e.getMessage());
         }
    }

    private Document parseXml(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(file.toFile());
    }

    private boolean matches(String value, String expected, boolean caseSensitive) {
        if (!caseSensitive) {
            return value.matches("(?i)" + expected) || value.equalsIgnoreCase(expected);
        }
        return value.matches(expected) || value.equals(expected);
    }
}
