package com.raks.muleguard.checks;

import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;
import com.raks.muleguard.PropertyResolver;
import org.dom4j.Document;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Generic XML validation check that consolidates multiple XML-based validation
 * rules.
 * 
 * Replaces:
 * - RULE_005_ibmmqciphervaluecheck (IBM MQ cipher suite validation)
 * - RULE_008_apiautodiscoverycheck (XPath element existence)
 * - RULE_010_UnsupportedApplicationCodeCheck (forbidden XML attributes)
 * - RULE_012_CryptoJceEncryptPbeCheck (forbidden values in elements)
 * - RULE_013_CryptoJceConfigTypeCheck (required attributes in elements)
 * 
 * Validation Types:
 * - EXISTS: Element must exist (via XPath)
 * - NOT_EXISTS: Element must NOT exist
 * - ATTRIBUTE_VALUE: Attribute must have specific value
 * - ATTRIBUTE_EXISTS: Attribute must be present
 * - FORBIDDEN_VALUE: Element must NOT contain value
 * - FORBIDDEN_ATTRIBUTE: Element must NOT have specific attributes
 * 
 * @author Rakesh Kumar (raksjnu@gmail.com)
 */
public class GenericXmlValidationCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        String validationType = (String) check.getParams().get("validationType");
        String pathPattern = (String) check.getParams().getOrDefault("path", "src/main/mule/*.xml");

        if (validationType == null) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'validationType' parameter is required");
        }

        Path muleSources = projectRoot.resolve("src/main/mule");
        if (!Files.isDirectory(muleSources)) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    "No Mule source files found in src/main/mule");
        }

        switch (validationType.toUpperCase()) {
            case "EXISTS":
            case "NOT_EXISTS":
                return validateXPathExists(projectRoot, check, pathPattern, validationType);

            case "ATTRIBUTE_VALUE":
                return validateAttributeValue(projectRoot, check, pathPattern);

            case "ATTRIBUTE_EXISTS":
                return validateAttributeExists(projectRoot, check, pathPattern);

            case "FORBIDDEN_VALUE":
                return validateForbiddenValue(projectRoot, check, pathPattern);

            case "FORBIDDEN_ATTRIBUTE":
                return validateForbiddenAttribute(projectRoot, check, pathPattern);

            default:
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        "Unknown validationType: " + validationType);
        }
    }

    /**
     * Validate that an XPath expression matches (or doesn't match) elements
     * Enhanced to support multiple XPath checks in a single rule
     */
    private CheckResult validateXPathExists(Path projectRoot, Check check, String pathPattern, String validationType) {
        // Check if we have a single xpath or multiple xpaths
        String singleXpath = (String) check.getParams().get("xpath");
        @SuppressWarnings("unchecked")
        List<String> multipleXpaths = (List<String>) check.getParams().get("xpaths");
        String failureMessage = (String) check.getParams().get("failureMessage");

        // Support both single and multiple XPath configurations
        List<String> xpathsToCheck = new ArrayList<>();
        if (singleXpath != null) {
            xpathsToCheck.add(singleXpath);
        }
        if (multipleXpaths != null && !multipleXpaths.isEmpty()) {
            xpathsToCheck.addAll(multipleXpaths);
        }

        if (xpathsToCheck.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'xpath' or 'xpaths' parameter is required");
        }

        List<Path> xmlFiles = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            xmlFiles = paths
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> path.toString().toLowerCase().endsWith(".xml"))
                    .filter(path -> matchesPathPattern(path, projectRoot, pathPattern))
                    .filter(path -> !shouldIgnorePath(projectRoot, path)) // Filter ignored folders
                    .toList();

            if (xmlFiles.isEmpty()) {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        "No XML files found matching path: " + pathPattern);
            }

            List<String> failures = new ArrayList<>();
            List<String> successes = new ArrayList<>();

            // Check each XPath expression
            for (String xpath : xpathsToCheck) {
                boolean foundInAnyFile = false;

                for (Path xmlFile : xmlFiles) {
                    try {
                        SAXReader reader = new SAXReader();
                        Document document = reader.read(xmlFile.toFile());
                        List<Node> nodes = document.selectNodes(xpath);

                        boolean elementExists = nodes != null && !nodes.isEmpty();

                        if (elementExists) {
                            foundInAnyFile = true;
                            break; // Found in this file, no need to check other files for this xpath
                        }
                    } catch (Exception e) {
                        // Continue checking other files
                    }
                }

                // Evaluate result for this XPath
                if ("EXISTS".equalsIgnoreCase(validationType)) {
                    if (foundInAnyFile) {
                        successes.add("✓ XPath found: " + xpath);
                    } else {
                        failures.add("✗ XPath not found: " + xpath);
                    }
                } else { // NOT_EXISTS
                    if (!foundInAnyFile) {
                        successes.add("✓ XPath not found (as expected): " + xpath);
                    } else {
                        failures.add("✗ Forbidden XPath found: " + xpath);
                    }
                }
            }

            // Return result based on all XPath checks
            if (failures.isEmpty()) {
                String fileList = xmlFiles.stream()
                        .map(projectRoot::relativize)
                        .map(Path::toString)
                        .collect(java.util.stream.Collectors.joining("; "));
                String message = successes.size() == 1 ? successes.get(0)
                        : "All XPath validations passed:\n" + String.join("\n", successes);
                return CheckResult.pass(check.getRuleId(), check.getDescription(),
                        message + "\nFiles validated: " + fileList);
            } else {
                String message = failureMessage != null ? failureMessage
                        : "XPath validation failures:\n" + String.join("\n", failures);
                return CheckResult.fail(check.getRuleId(), check.getDescription(), message);
            }

        } catch (IOException e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error scanning project files: " + e.getMessage());
        }
    }

    /**
     * Validate that an XML attribute has a specific value (with property
     * resolution)
     */
    private CheckResult validateAttributeValue(Path projectRoot, Check check, String pathPattern) {
        String xpath = (String) check.getParams().get("xpath");
        String expectedValue = (String) check.getParams().get("expectedValue");
        boolean propertyResolution = Boolean.parseBoolean(
                String.valueOf(check.getParams().getOrDefault("propertyResolution", "false")));

        if (xpath == null || expectedValue == null) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'xpath' and 'expectedValue' are required");
        }

        PropertyResolver propertyResolver = propertyResolution ? new PropertyResolver(projectRoot) : null;
        List<String> failures = new ArrayList<>();
        AtomicBoolean attributeFoundInAnyFile = new AtomicBoolean(false);

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(path -> !Files.isDirectory(path))
                    .filter(path -> path.toString().toLowerCase().endsWith(".xml"))
                    .filter(path -> matchesPathPattern(path, projectRoot, pathPattern))
                    .forEach(xmlFile -> {
                        try {
                            SAXReader reader = new SAXReader();
                            // Disable validation but keep namespaces enabled
                            reader.setValidation(false);
                            reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                            reader.setFeature("http://xml.org/sax/features/validation", false);

                            Document document = reader.read(xmlFile.toFile());
                            List<Node> nodes = document.selectNodes(xpath);

                            if (nodes != null && !nodes.isEmpty()) {
                                attributeFoundInAnyFile.set(true);
                                for (Node node : nodes) {
                                    String actualValue = node.getText();
                                    String resolvedValue = propertyResolver != null
                                            ? propertyResolver.resolve(actualValue)
                                            : actualValue;

                                    // Check if property resolution failed (property not found)
                                    if (resolvedValue == null) {
                                        failures.add(String.format(
                                                "Property not found in %s. Placeholder: \"%s\"",
                                                projectRoot.relativize(xmlFile), actualValue));
                                    } else if (!expectedValue.equals(resolvedValue)) {
                                        failures.add(String.format(
                                                "Incorrect value in %s. Found: \"%s\", Expected: \"%s\"",
                                                projectRoot.relativize(xmlFile), resolvedValue, expectedValue));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Continue processing other files - silently ignore parse errors
                        }
                    });

        } catch (IOException e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error scanning files: " + e.getMessage());
        }

        if (!attributeFoundInAnyFile.get()) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    "No matching elements found (not applicable)");
        }

        if (failures.isEmpty()) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    "All attribute values are correct");
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    String.join("\n", failures));
        }
    }

    /**
     * Validate that an XML element has a required attribute
     */
    private CheckResult validateAttributeExists(Path projectRoot, Check check, String pathPattern) {
        String elementName = (String) check.getParams().get("elementName");
        String requiredAttribute = (String) check.getParams().get("requiredAttribute");

        if (elementName == null || requiredAttribute == null) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'elementName' and 'requiredAttribute' are required");
        }

        List<String> failures = new ArrayList<>();
        AtomicBoolean elementFoundInAnyFile = new AtomicBoolean(false);

        String regex = String.format("(?i)<(%s)(?!.*\\b%s\\s*=)[^>]*>",
                Pattern.quote(elementName), Pattern.quote(requiredAttribute));
        Pattern violationPattern = Pattern.compile(regex);
        Pattern elementExistsPattern = Pattern.compile(String.format("<%s", Pattern.quote(elementName)));

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(path -> !Files.isDirectory(path))
                    .filter(path -> path.toString().toLowerCase().endsWith(".xml"))
                    .filter(path -> matchesPathPattern(path, projectRoot, pathPattern))
                    .forEach(file -> {
                        try {
                            String content = Files.readString(file);
                            if (elementExistsPattern.matcher(content).find()) {
                                elementFoundInAnyFile.set(true);
                                Matcher violationMatcher = violationPattern.matcher(content);
                                if (violationMatcher.find()) {
                                    failures.add(String.format(
                                            "Found <%s> element without required '%s' attribute in file %s",
                                            elementName, requiredAttribute, projectRoot.relativize(file)));
                                }
                            }
                        } catch (IOException e) {
                            // Continue processing
                        }
                    });

        } catch (IOException e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error scanning files: " + e.getMessage());
        }

        if (!elementFoundInAnyFile.get() || failures.isEmpty()) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    "All elements have required attributes");
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    String.join("\n", failures));
        }
    }

    /**
     * Validate that an XML element does NOT contain a forbidden value
     */
    private CheckResult validateForbiddenValue(Path projectRoot, Check check, String pathPattern) {
        String elementName = (String) check.getParams().get("elementName");
        String forbiddenValue = (String) check.getParams().get("forbiddenValue");

        if (elementName == null || forbiddenValue == null) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'elementName' and 'forbiddenValue' are required");
        }

        List<String> failures = new ArrayList<>();
        String regex = String.format("(?i)<%s[^>]*?%s[^>]*?>",
                Pattern.quote(elementName), Pattern.quote(forbiddenValue));
        Pattern pattern = Pattern.compile(regex);

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(path -> !Files.isDirectory(path))
                    .filter(path -> path.toString().toLowerCase().endsWith(".xml"))
                    .filter(path -> matchesPathPattern(path, projectRoot, pathPattern))
                    .forEach(file -> {
                        try {
                            String content = Files.readString(file);
                            Matcher matcher = pattern.matcher(content);
                            if (matcher.find()) {
                                failures.add(String.format(
                                        "Found forbidden value '%s' in <%s> element in file %s",
                                        forbiddenValue, elementName, projectRoot.relativize(file)));
                            }
                        } catch (IOException e) {
                            // Continue processing
                        }
                    });

        } catch (IOException e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error scanning files: " + e.getMessage());
        }

        if (failures.isEmpty()) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    "No forbidden values found");
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    String.join("\n", failures));
        }
    }

    /**
     * Validate that XML elements do NOT have forbidden attributes
     */
    private CheckResult validateForbiddenAttribute(Path projectRoot, Check check, String pathPattern) {
        @SuppressWarnings("unchecked")
        List<String> elements = (List<String>) check.getParams().get("elements");
        @SuppressWarnings("unchecked")
        List<String> attributes = (List<String>) check.getParams().get("attributes");

        if (elements == null || attributes == null) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'elements' and 'attributes' are required");
        }

        List<String> issues = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(path -> !Files.isDirectory(path))
                    .filter(path -> path.toString().toLowerCase().endsWith(".xml"))
                    .filter(path -> matchesPathPattern(path, projectRoot, pathPattern))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            List<String> foundAttributes = new ArrayList<>();

                            // Check each element-attribute combination
                            for (String element : elements) {
                                for (String attribute : attributes) {
                                    // Create regex to find this specific attribute in this element
                                    String specificRegex = String.format("(?si)<[a-zA-Z0-9_-]*:?%s\\s+[^>]*?%s\\s*=",
                                            Pattern.quote(element), Pattern.quote(attribute));
                                    Pattern specificPattern = Pattern.compile(specificRegex);
                                    Matcher specificMatcher = specificPattern.matcher(content);

                                    if (specificMatcher.find()) {
                                        foundAttributes.add(String.format("'%s' in <%s>", attribute, element));
                                    }
                                }
                            }

                            if (!foundAttributes.isEmpty()) {
                                issues.add(String.format(
                                        "File: %s - Found: %s",
                                        projectRoot.relativize(path),
                                        String.join(", ", foundAttributes)));
                            }
                        } catch (Exception e) {
                            // Continue processing
                        }
                    });

        } catch (Exception e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error while checking for forbidden attributes: " + e.getMessage());
        }

        if (issues.isEmpty()) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    "No forbidden attributes found");
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Found forbidden attributes:\n" + String.join("\n", issues));
        }
    }

    /**
     * Check if a path matches the given pattern
     */
    private boolean matchesPathPattern(Path path, Path projectRoot, String pattern) {
        try {
            return path.getFileSystem().getPathMatcher("glob:**/" + pattern).matches(path);
        } catch (Exception e) {
            // If pattern matching fails, include the file
            return true;
        }
    }
}
