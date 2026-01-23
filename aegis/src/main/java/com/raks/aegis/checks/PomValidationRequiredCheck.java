package com.raks.aegis.checks;
import com.raks.aegis.model.Check;
import com.raks.aegis.model.CheckResult;
import com.raks.aegis.util.VersionComparator;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
public class PomValidationRequiredCheck extends AbstractCheck {
    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        String validationType = (String) check.getParams().getOrDefault("validationType", "COMBINED");
        List<String> failures = new ArrayList<>();
        List<String> successes = new ArrayList<>();  
        List<Path> pomFiles = findFiles(projectRoot, java.util.Collections.singletonList("**/pom.xml"), false);
        if (pomFiles.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "No pom.xml files found in project");
        }
        
        Set<Path> matchedPathsSet = new HashSet<>();
        for (Path pomFile : pomFiles) {
            List<String> fileFailures = new ArrayList<>();
            List<String> fileSuccesses = new ArrayList<>();
            validatePom(pomFile, check.getParams(), validationType, projectRoot, fileFailures, fileSuccesses);
            
            if (fileFailures.isEmpty() && !fileSuccesses.isEmpty()) {
                matchedPathsSet.add(pomFile);
            }
            failures.addAll(fileFailures);
            successes.addAll(fileSuccesses);
        }
        // Generate file list for reporting (used in both pass and fail scenarios)
        String fileList = pomFiles.stream()
                .map(projectRoot::relativize)
                .map(Path::toString)
                .collect(java.util.stream.Collectors.joining("; "));

        if (failures.isEmpty()) {
            String defaultSuccess = "All required POM elements found\nFiles validated: " + fileList;
            if (!successes.isEmpty()) {
                defaultSuccess += "\nActual Values Found:\n• " + String.join("\n• ", successes);
            }
            String matchingFilesForPass = successes.isEmpty() ? null : String.join("; ", successes);
            return finalizePass(check, defaultSuccess, fileList, matchingFilesForPass, fileList, matchedPathsSet);
        } else {
            String technicalMsg = "Missing or incorrect required POM elements:\n• " + String.join("\n• ", failures);
            String failureDetails = String.join("; ", failures);
            return finalizeFail(check, technicalMsg, fileList, failureDetails, fileList, matchedPathsSet);
        }
    }

    private void validatePom(Path pomFile, Map<String, Object> params, String validationType,
            Path projectRoot, List<String> failures, List<String> successes) {
        try {
            Document doc = parseXml(pomFile);
            if (doc == null) {
                failures.add("Failed to parse " + projectRoot.relativize(pomFile));
                return;
            }
            switch (validationType.toUpperCase()) {
                case "PARENT" -> validateParent(doc, params, pomFile, projectRoot, failures, successes);
                case "PROPERTIES" -> validateProperties(doc, params, pomFile, projectRoot, failures, successes);
                case "DEPENDENCIES" -> validateDependencies(doc, params, pomFile, projectRoot, failures, successes);
                case "PLUGINS" -> validatePlugins(doc, params, pomFile, projectRoot, failures, successes);
                default -> {
                    validateParent(doc, params, pomFile, projectRoot, failures, successes);
                    validateProperties(doc, params, pomFile, projectRoot, failures, successes);
                    validateDependencies(doc, params, pomFile, projectRoot, failures, successes);
                    validatePlugins(doc, params, pomFile, projectRoot, failures, successes);
                }
            }
        } catch (Exception e) {
            failures.add("Error parsing POM file " + projectRoot.relativize(pomFile) + ": " + e.getMessage());
        }
    }
    private void validateParent(Document doc, Map<String, Object> params, Path pomFile,
            Path projectRoot, List<String> failures, List<String> successes) {
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

        successes.add(String.format("Parent: %s:%s:%s (in %s)",
                groupId, artifactId, version, projectRoot.relativize(pomFile)));

        String expectedVersion = parent.get("version");
        if (expectedVersion != null && !expectedVersion.equals(version)) {
            failures.add(String.format("Parent version mismatch in %s: expected %s:%s:%s, got version '%s'",
                    projectRoot.relativize(pomFile), parent.get("groupId"), parent.get("artifactId"), 
                    expectedVersion, version));
        }

        try {
            String minVersion = parent.get("minVersion");
            if (minVersion != null && !VersionComparator.isGreaterThanOrEqual(version, minVersion)) {
                failures.add(String.format("Parent version too low in %s: %s:%s expected >= '%s', got '%s'",
                        projectRoot.relativize(pomFile), parent.get("groupId"), parent.get("artifactId"),
                        minVersion, version));
            }

            String maxVersion = parent.get("maxVersion");
            if (maxVersion != null && !VersionComparator.isLessThanOrEqual(version, maxVersion)) {
                failures.add(String.format("Parent version too high in %s: %s:%s expected <= '%s', got '%s'",
                        projectRoot.relativize(pomFile), parent.get("groupId"), parent.get("artifactId"),
                        maxVersion, version));
            }
        } catch (IllegalArgumentException e) {

        }
    }
    private void validateProperties(Document doc, Map<String, Object> params, Path pomFile,
            Path projectRoot, List<String> failures, List<String> successes) {
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
            String actualValue = getElementText(propsElement, name);

            if (actualValue == null || actualValue.isEmpty()) {
                failures.add(String.format("Property '%s' missing in %s", name, projectRoot.relativize(pomFile)));
                continue;
            }

            successes.add(String.format("Property '%s': %s (in %s)", name, actualValue, projectRoot.relativize(pomFile)));

            String expectedValue = prop.get("expectedValue");
            if (expectedValue != null && !expectedValue.equals(actualValue)) {
                failures.add(String.format("Property '%s' has wrong value in %s: expected '%s', got '%s'",
                        name, projectRoot.relativize(pomFile), expectedValue, actualValue));
            }

            try {
                String minVersion = prop.get("minVersion");
                if (minVersion != null && !VersionComparator.isGreaterThanOrEqual(actualValue, minVersion)) {
                    failures.add(String.format("Property '%s' version too low in %s: expected >= '%s', got '%s'",
                            name, projectRoot.relativize(pomFile), minVersion, actualValue));
                }

                String maxVersion = prop.get("maxVersion");
                if (maxVersion != null && !VersionComparator.isLessThanOrEqual(actualValue, maxVersion)) {
                    failures.add(String.format("Property '%s' version too high in %s: expected <= '%s', got '%s'",
                            name, projectRoot.relativize(pomFile), maxVersion, actualValue));
                }

                String greaterThan = prop.get("greaterThan");
                if (greaterThan != null && !VersionComparator.isGreaterThan(actualValue, greaterThan)) {
                    failures.add(String.format("Property '%s' version not greater in %s: expected > '%s', got '%s'",
                            name, projectRoot.relativize(pomFile), greaterThan, actualValue));
                }

                String lessThan = prop.get("lessThan");
                if (lessThan != null && !VersionComparator.isLessThan(actualValue, lessThan)) {
                    failures.add(String.format("Property '%s' version not less in %s: expected < '%s', got '%s'",
                            name, projectRoot.relativize(pomFile), lessThan, actualValue));
                }
            } catch (IllegalArgumentException e) {

            }
        }
    }
    private void validateDependencies(Document doc, Map<String, Object> params, Path pomFile,
            Path projectRoot, List<String> failures, List<String> successes) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> dependencies = (List<Map<String, String>>) params.get("dependencies");
        if (dependencies == null)
            return;
        NodeList depNodes = doc.getElementsByTagName("dependency");
        for (Map<String, String> dep : dependencies) {
            boolean found = false;
            String foundVersion = null;
            for (int i = 0; i < depNodes.getLength(); i++) {
                Element depElement = (Element) depNodes.item(i);
                if (matchesDependency(depElement, dep)) {
                    found = true;
                    foundVersion = getElementText(depElement, "version");
                    break;
                }
            }
            if (!found) {
                failures.add(String.format("Dependency %s:%s not found in %s",
                        dep.get("groupId"), dep.get("artifactId"), projectRoot.relativize(pomFile)));
            } else {

                String versionInfo = foundVersion != null ? ":" + foundVersion : "";
                successes.add(String.format("Dependency: %s:%s%s (in %s)",
                        dep.get("groupId"), dep.get("artifactId"), versionInfo, projectRoot.relativize(pomFile)));
            }
        }
    }
    private void validatePlugins(Document doc, Map<String, Object> params, Path pomFile,
            Path projectRoot, List<String> failures, List<String> successes) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> plugins = (List<Map<String, String>>) params.get("plugins");
        if (plugins == null)
            return;
        NodeList pluginNodes = doc.getElementsByTagName("plugin");
        for (Map<String, String> plugin : plugins) {
            boolean found = false;
            String foundVersion = null;
            for (int i = 0; i < pluginNodes.getLength(); i++) {
                Element pluginElement = (Element) pluginNodes.item(i);
                if (matchesPlugin(pluginElement, plugin)) {
                    found = true;
                    foundVersion = getElementText(pluginElement, "version");
                    break;
                }
            }
            if (!found) {
                failures.add(String.format("Plugin %s:%s not found in %s",
                        plugin.get("groupId"), plugin.get("artifactId"), projectRoot.relativize(pomFile)));
            } else {

                String versionInfo = foundVersion != null ? ":" + foundVersion : "";
                successes.add(String.format("Plugin: %s:%s%s (in %s)",
                        plugin.get("groupId"), plugin.get("artifactId"), versionInfo, projectRoot.relativize(pomFile)));
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
