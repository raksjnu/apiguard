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

public class PomValidationRequiredCheck extends AbstractCheck {
    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        String validationType = (String) check.getParams().getOrDefault("validationType", "COMBINED");
        String matchMode = (String) check.getParams().getOrDefault("matchMode", "ALL_FILES");
        
        List<String> allFailures = new ArrayList<>();
        List<Path> pomFiles = new ArrayList<>();
        int passedPoms = 0;

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            pomFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .filter(path -> !shouldIgnorePath(projectRoot, path)) 
                    .toList();
            if (pomFiles.isEmpty()) {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        "No pom.xml files found in project");
            }
            for (Path pomFile : pomFiles) {
                List<String> pomFailures = new ArrayList<>();
                validatePom(pomFile, check.getParams(), validationType, projectRoot, pomFailures);
                
                if (pomFailures.isEmpty()) {
                    passedPoms++;
                } else {
                    allFailures.addAll(pomFailures);
                }
            }
        } catch (IOException e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error scanning files: " + e.getMessage());
        }

        if ("ANY_FILE".equalsIgnoreCase(matchMode)) {
            if (passedPoms > 0) {
                return CheckResult.pass(check.getRuleId(), check.getDescription(),
                        "At least one POM file passed validation (" + passedPoms + "/" + pomFiles.size() + " files passed)");
            } else {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        "POM validation failures (Not found in ANY matching files):\n• " + String.join("\n• ", allFailures));
            }
        } else {
            if (passedPoms == pomFiles.size()) {
                String fileList = pomFiles.stream()
                        .map(projectRoot::relativize)
                        .map(Path::toString)
                        .collect(java.util.stream.Collectors.joining("; "));
                return CheckResult.pass(check.getRuleId(), check.getDescription(),
                        "All required POM elements found\nFiles validated: " + fileList);
            } else {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        "POM validation failures:\n• " + String.join("\n• ", allFailures));
            }
        }
    }
    
    private void validatePom(Path pomFile, Map<String, Object> params, String validationType,
            Path projectRoot, List<String> failures) {
        try {
            Document doc = parseXml(pomFile);
            if ("PARENT".equals(validationType) || "COMBINED".equals(validationType)) {
                validateParent(doc, params, pomFile, projectRoot, failures);
            }
            if ("PROPERTIES".equals(validationType) || "COMBINED".equals(validationType)) {
                validateProperties(doc, params, pomFile, projectRoot, failures);
            }
            if ("DEPENDENCIES".equals(validationType) || "COMBINED".equals(validationType)) {
                validateDependencies(doc, params, pomFile, projectRoot, failures);
            }
            if ("PLUGINS".equals(validationType) || "COMBINED".equals(validationType)) {
                validatePlugins(doc, params, pomFile, projectRoot, failures);
            }
        } catch (Exception e) {
            failures.add("Error parsing POM file " + projectRoot.relativize(pomFile) + ": " + e.getMessage());
        }
    }

    private void validateParent(Document doc, Map<String, Object> params, Path pomFile,
            Path projectRoot, List<String> failures) {
        @SuppressWarnings("unchecked")
        Map<String, String> parent = (Map<String, String>) params.get("parent");
        if (parent == null)
            return;
        NodeList parentNodes = doc.getElementsByTagName("parent");
        if (parentNodes.getLength() == 0) {
            failures.add("Parent element missing in " + projectRoot.relativize(pomFile));
            return;
        }
        Element parentElement = (Element) parentNodes.item(0);
        String groupId = getElementText(parentElement, "groupId");
        String artifactId = getElementText(parentElement, "artifactId");
        String version = getElementText(parentElement, "version");
        if (!parent.get("groupId").equals(groupId) || !parent.get("artifactId").equals(artifactId)) {
            failures.add(String.format("Parent mismatch in %s: expected %s:%s",
                    projectRoot.relativize(pomFile), parent.get("groupId"), parent.get("artifactId")));
            return;
        }
        String expectedVersion = parent.get("version");
        if (expectedVersion != null && !expectedVersion.equals(version)) {
            failures.add(String.format("Parent version mismatch in %s: expected %s:%s:%s, got version '%s'",
                    projectRoot.relativize(pomFile), parent.get("groupId"), parent.get("artifactId"), 
                    expectedVersion, version));
        }
    }

    private void validateProperties(Document doc, Map<String, Object> params, Path pomFile,
            Path projectRoot, List<String> failures) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> properties = (List<Map<String, String>>) params.get("properties");
        if (properties == null)
            return;
        NodeList propsNode = doc.getElementsByTagName("properties");
        if (propsNode.getLength() == 0) {
            failures.add("Properties section missing in " + projectRoot.relativize(pomFile));
            return;
        }
        Element propsElement = (Element) propsNode.item(0);
        for (Map<String, String> prop : properties) {
            String name = prop.get("name");
            String expectedValue = prop.get("expectedValue");
            String actualValue = getElementText(propsElement, name);
            if (actualValue == null || actualValue.isEmpty()) {
                failures.add(String.format("Property '%s' missing in %s", name, projectRoot.relativize(pomFile)));
            } else if (expectedValue != null && !expectedValue.equals(actualValue)) {
                failures.add(String.format("Property '%s' has wrong value in %s: expected '%s', got '%s'",
                        name, projectRoot.relativize(pomFile), expectedValue, actualValue));
            }
        }
    }

    private void validateDependencies(Document doc, Map<String, Object> params, Path pomFile,
            Path projectRoot, List<String> failures) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> dependencies = (List<Map<String, String>>) params.get("dependencies");
        if (dependencies == null)
            return;
        NodeList depNodes = doc.getElementsByTagName("dependency");
        for (Map<String, String> dep : dependencies) {
            boolean found = false;
            for (int i = 0; i < depNodes.getLength(); i++) {
                Element depElement = (Element) depNodes.item(i);
                if (matchesDependency(depElement, dep)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                failures.add(String.format("Dependency %s:%s not found in %s",
                        dep.get("groupId"), dep.get("artifactId"), projectRoot.relativize(pomFile)));
            }
        }
    }

    private void validatePlugins(Document doc, Map<String, Object> params, Path pomFile,
            Path projectRoot, List<String> failures) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> plugins = (List<Map<String, String>>) params.get("plugins");
        if (plugins == null)
            return;
        NodeList pluginNodes = doc.getElementsByTagName("plugin");
        for (Map<String, String> plugin : plugins) {
            boolean found = false;
            for (int i = 0; i < pluginNodes.getLength(); i++) {
                Element pluginElement = (Element) pluginNodes.item(i);
                if (matchesPlugin(pluginElement, plugin)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                failures.add(String.format("Plugin %s:%s not found in %s",
                        plugin.get("groupId"), plugin.get("artifactId"), projectRoot.relativize(pomFile)));
            }
        }
    }

    private boolean matchesDependency(Element depElement, Map<String, String> expected) {
        String groupId = getElementText(depElement, "groupId");
        String artifactId = getElementText(depElement, "artifactId");
        if (!expected.get("groupId").equals(groupId) || !expected.get("artifactId").equals(artifactId)) {
            return false;
        }
        String expectedVersion = expected.get("version");
        if (expectedVersion != null) {
            String actualVersion = getElementText(depElement, "version");
            return expectedVersion.equals(actualVersion);
        }
        return true;
    }

    private boolean matchesPlugin(Element pluginElement, Map<String, String> expected) {
        String groupId = getElementText(pluginElement, "groupId");
        String artifactId = getElementText(pluginElement, "artifactId");
        if (!expected.get("groupId").equals(groupId) || !expected.get("artifactId").equals(artifactId)) {
            return false;
        }
        String expectedVersion = expected.get("version");
        if (expectedVersion != null) {
            String actualVersion = getElementText(pluginElement, "version");
            return expectedVersion.equals(actualVersion);
        }
        return true;
    }

    private String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }

    private Document parseXml(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(file.toFile());
    }
}
