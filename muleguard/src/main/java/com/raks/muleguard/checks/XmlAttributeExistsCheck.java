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
import java.util.Map;
import java.util.stream.Stream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
public class XmlAttributeExistsCheck extends AbstractCheck {
    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) check.getParams().get("filePatterns");
        @SuppressWarnings("unchecked")
        List<String> elements = (List<String>) check.getParams().get("elements");
        @SuppressWarnings("unchecked")
        List<String> attributes = (List<String>) check.getParams().get("attributes");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> attributeValuePairs = (List<Map<String, String>>) check.getParams()
                .get("attributeValuePairs");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elementAttributeSets = (List<Map<String, Object>>) check.getParams()
                .get("elementAttributeSets");
        Boolean propertyResolution = (Boolean) check.getParams().getOrDefault("propertyResolution", false);
        Boolean caseSensitive = (Boolean) check.getParams().getOrDefault("caseSensitive", true);
        if (filePatterns == null || filePatterns.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'filePatterns' parameter is required");
        }
        if ((elements == null || elements.isEmpty()) &&
                (attributeValuePairs == null || attributeValuePairs.isEmpty()) &&
                (elementAttributeSets == null || elementAttributeSets.isEmpty())) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: At least one of 'elements', 'attributeValuePairs', or 'elementAttributeSets' must be specified");
        }
        List<String> failures = new ArrayList<>();
        List<Path> matchingFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            matchingFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesAnyPattern(path, filePatterns, projectRoot))
                    .filter(path -> !shouldIgnorePath(projectRoot, path))  
                    .toList();
            if (matchingFiles.isEmpty()) {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        "No files found matching patterns: " + filePatterns);
            }
            for (Path file : matchingFiles) {
                if (elements != null && !elements.isEmpty() && attributes != null && !attributes.isEmpty()) {
                    validateSimpleAttributes(file, elements, attributes, caseSensitive, projectRoot, failures);
                }
                if (attributeValuePairs != null && !attributeValuePairs.isEmpty()) {
                    validateAttributeValuePairs(file, attributeValuePairs, caseSensitive, propertyResolution,
                            projectRoot, failures);
                }
                if (elementAttributeSets != null && !elementAttributeSets.isEmpty()) {
                    validateElementAttributeSets(file, elementAttributeSets, caseSensitive, propertyResolution,
                            projectRoot, failures);
                }
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
                    "All required attributes found\nFiles validated: " + fileList);
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Attribute validation failures:\n• " + String.join("\n• ", failures));
        }
    }
    private void validateSimpleAttributes(Path file, List<String> elements, List<String> attributes,
            boolean caseSensitive, Path projectRoot, List<String> failures) {
        try {
            Document doc = parseXml(file);
            for (String elementName : elements) {
                NodeList nodeList = doc.getElementsByTagName(elementName);
                for (int i = 0; i < nodeList.getLength(); i++) {
                    Element element = (Element) nodeList.item(i);
                    for (String attrName : attributes) {
                        if (!element.hasAttribute(attrName)) {
                            failures.add(String.format("Element '%s' missing attribute '%s' in file: %s",
                                    elementName, attrName, projectRoot.relativize(file)));
                        }
                    }
                }
                if (nodeList.getLength() == 0) {
                    failures.add(String.format("Element '%s' not found in file: %s",
                            elementName, projectRoot.relativize(file)));
                }
            }
        } catch (Exception e) {
            failures.add("Error parsing XML file " + projectRoot.relativize(file) + ": " + e.getMessage());
        }
    }
    private void validateAttributeValuePairs(Path file, List<Map<String, String>> pairs,
            boolean caseSensitive, boolean propertyResolution, Path projectRoot, List<String> failures) {
        try {
            Document doc = parseXml(file);
            for (Map<String, String> pair : pairs) {
                String elementName = pair.get("element");
                String attrName = pair.get("attribute");
                String expectedValue = pair.get("expectedValue");
                NodeList nodeList = doc.getElementsByTagName(elementName);
                boolean found = false;
                for (int i = 0; i < nodeList.getLength(); i++) {
                    Element element = (Element) nodeList.item(i);
                    if (element.hasAttribute(attrName)) {
                        String actualValue = element.getAttribute(attrName);
                        boolean matches = matchesValue(actualValue, expectedValue, caseSensitive, propertyResolution);
                        if (matches) {
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    failures.add(String.format("Element '%s' with attribute '%s'='%s' not found in file: %s",
                            elementName, attrName, expectedValue, projectRoot.relativize(file)));
                }
            }
        } catch (Exception e) {
            failures.add("Error parsing XML file " + projectRoot.relativize(file) + ": " + e.getMessage());
        }
    }
    private void validateElementAttributeSets(Path file, List<Map<String, Object>> sets,
            boolean caseSensitive, boolean propertyResolution, Path projectRoot, List<String> failures) {
        try {
            Document doc = parseXml(file);
            for (Map<String, Object> set : sets) {
                String elementName = (String) set.get("element");
                @SuppressWarnings("unchecked")
                Map<String, String> attributesMap = (Map<String, String>) set.get("attributes");
                NodeList nodeList = doc.getElementsByTagName(elementName);
                boolean found = false;
                for (int i = 0; i < nodeList.getLength(); i++) {
                    Element element = (Element) nodeList.item(i);
                    boolean allMatch = true;
                    for (Map.Entry<String, String> entry : attributesMap.entrySet()) {
                        String attrName = entry.getKey();
                        String expectedValue = entry.getValue();
                        if (!element.hasAttribute(attrName)) {
                            allMatch = false;
                            break;
                        }
                        String actualValue = element.getAttribute(attrName);
                        boolean matches = matchesValue(actualValue, expectedValue, caseSensitive, propertyResolution);
                        if (!matches) {
                            allMatch = false;
                            break;
                        }
                    }
                    if (allMatch) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    failures.add(String.format("Element '%s' with required attributes %s not found in file: %s",
                            elementName, attributesMap, projectRoot.relativize(file)));
                }
            }
        } catch (Exception e) {
            failures.add("Error parsing XML file " + projectRoot.relativize(file) + ": " + e.getMessage());
        }
    }
    private boolean matchesValue(String actualValue, String expectedValue, boolean caseSensitive,
            boolean propertyResolution) {
        if (propertyResolution && actualValue.matches("\\$\\{[^}]+\\}")) {
            return true;
        }
        return caseSensitive ? actualValue.equals(expectedValue) : actualValue.equalsIgnoreCase(expectedValue);
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
