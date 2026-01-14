package com.raks.muleguard.checks;

import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class XmlAttributeExternalizedCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        if (!isRuleApplicable(projectRoot, check)) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(), "Rule skipped: Pre-conditions not met (e.g., connector not found)");
        }

        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) check.getParams().get("filePatterns");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elementAttributeSets = (List<Map<String, Object>>) check.getParams().get("elementAttributeSets");

        if (filePatterns == null || filePatterns.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Configuration error: 'filePatterns' is required");
        }
        if (elementAttributeSets == null || elementAttributeSets.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Configuration error: 'elementAttributeSets' is required");
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
                validateExternalizedAttributes(file, elementAttributeSets, projectRoot, failures);
            }

        } catch (IOException e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Error scanning files: " + e.getMessage());
        }

        if (failures.isEmpty()) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(), "All checked attributes are properly externalized.");
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Externalization failures:\n• " + String.join("\n• ", failures));
        }
    }

    private void validateExternalizedAttributes(Path file, List<Map<String, Object>> elementAttributeSets, Path projectRoot, List<String> failures) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file.toFile());

            for (Map<String, Object> set : elementAttributeSets) {
                String elementName = (String) set.get("element");
                @SuppressWarnings("unchecked")
                List<String> attributes = (List<String>) set.get("attributes");
                boolean strictPresence = (Boolean) set.getOrDefault("strictPresence", true); // If true, fail if attribute is missing

                NodeList nodeList = doc.getElementsByTagName(elementName);
                if (nodeList.getLength() > 0) {
                    for (int i = 0; i < nodeList.getLength(); i++) {
                        Element element = (Element) nodeList.item(i);
                        for (String attr : attributes) {
                            if (!element.hasAttribute(attr)) {
                                if (strictPresence) {
                                    failures.add(String.format("Element '%s' missing required attribute '%s' in file: %s",
                                            elementName, attr, projectRoot.relativize(file)));
                                }
                            } else {
                                String value = element.getAttribute(attr);
                                if (!value.matches("^\\$\\{[^}]+\\}$")) {
                                    failures.add(String.format("Attribute '%s' in element '%s' is NOT externalized (Found: '%s') in file: %s",
                                            attr, elementName, value, projectRoot.relativize(file)));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            failures.add("Error parsing XML file " + projectRoot.relativize(file) + ": " + e.getMessage());
        }
    }
}
