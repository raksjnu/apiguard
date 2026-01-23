package com.raks.aegis.checks;
import com.raks.aegis.model.Check;
import com.raks.aegis.model.CheckResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
public class PomValidationForbiddenCheck extends AbstractCheck {
    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        String validationType = (String) check.getParams().getOrDefault("validationType", "COMBINED");
        List<String> failures = new ArrayList<>();
        List<Path> pomFiles = findFiles(projectRoot, java.util.Collections.singletonList("**/pom.xml"), false);
        if (pomFiles.isEmpty()) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    "No pom.xml files found (nothing to validate)");
        }
        
        java.util.Set<Path> matchedPathsSet = new java.util.HashSet<>();
        for (Path pomFile : pomFiles) {
            List<String> fileFailures = new ArrayList<>();
            validatePom(pomFile, check.getParams(), validationType, projectRoot, fileFailures);
            
            if (!fileFailures.isEmpty()) {
                matchedPathsSet.add(pomFile);
            }
            failures.addAll(fileFailures);
        }
        // Generate file list for reporting (used in both pass and fail scenarios)
        String fileList = pomFiles.stream()
                .map(projectRoot::relativize)
                .map(Path::toString)
                .collect(java.util.stream.Collectors.joining("; "));

        if (failures.isEmpty()) {
            String msg = "No forbidden POM elements found\nFiles validated: " + fileList;
            return finalizePass(check, msg, fileList, null, fileList, matchedPathsSet);
        } else {
            String technicalMsg = "Forbidden POM elements found:\n• " + String.join("\n• ", failures);
            String foundItems = String.join("; ", failures);
            return finalizeFail(check, technicalMsg, fileList, foundItems, null, matchedPathsSet);
        }
    }

    private void validatePom(Path pomFile, Map<String, Object> params, String validationType,
            Path projectRoot, List<String> failures) {
        try {
            Document doc = parseXml(pomFile, params);
            if ("PROPERTIES".equals(validationType) || "COMBINED".equals(validationType)) {
                validateForbiddenProperties(doc, params, pomFile, projectRoot, failures);
            }
            if ("DEPENDENCIES".equals(validationType) || "COMBINED".equals(validationType)) {
                validateForbiddenDependencies(doc, params, pomFile, projectRoot, failures);
            }
            if ("PLUGINS".equals(validationType) || "COMBINED".equals(validationType)) {
                validateForbiddenPlugins(doc, params, pomFile, projectRoot, failures);
            }
        } catch (Exception e) {
            failures.add("Error parsing POM file " + projectRoot.relativize(pomFile) + ": " + e.getMessage());
        }
    }
    private void validateForbiddenProperties(Document doc, Map<String, Object> params, Path pomFile,
            Path projectRoot, List<String> failures) {
        @SuppressWarnings("unchecked")
        List<String> forbiddenProperties = (List<String>) params.get("forbiddenProperties");
        if (forbiddenProperties == null)
            return;
        NodeList propsNode = doc.getElementsByTagName("properties");
        if (propsNode.getLength() == 0)
            return;
        Element propsElement = (Element) propsNode.item(0);
        for (String propName : forbiddenProperties) {
            String value = getElementText(propsElement, propName);
            if (value != null && !value.isEmpty()) {
                failures.add(String.format("Forbidden property '%s' found in %s",
                        propName, projectRoot.relativize(pomFile)));
            }
        }
    }
    private void validateForbiddenDependencies(Document doc, Map<String, Object> params, Path pomFile,
            Path projectRoot, List<String> failures) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> forbiddenDependencies = (List<Map<String, String>>) params
                .get("forbiddenDependencies");
        if (forbiddenDependencies == null)
            return;
        NodeList depNodes = doc.getElementsByTagName("dependency");
        for (Map<String, String> dep : forbiddenDependencies) {
            for (int i = 0; i < depNodes.getLength(); i++) {
                Element depElement = (Element) depNodes.item(i);
                if (matchesDependency(depElement, dep)) {
                    failures.add(String.format("Forbidden dependency %s:%s found in %s",
                            dep.get("groupId"), dep.get("artifactId"), projectRoot.relativize(pomFile)));
                }
            }
        }
    }
    private void validateForbiddenPlugins(Document doc, Map<String, Object> params, Path pomFile,
            Path projectRoot, List<String> failures) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> forbiddenPlugins = (List<Map<String, String>>) params.get("forbiddenPlugins");
        if (forbiddenPlugins == null)
            return;
        NodeList pluginNodes = doc.getElementsByTagName("plugin");
        for (Map<String, String> plugin : forbiddenPlugins) {
            for (int i = 0; i < pluginNodes.getLength(); i++) {
                Element pluginElement = (Element) pluginNodes.item(i);
                if (matchesPlugin(pluginElement, plugin)) {
                    failures.add(String.format("Forbidden plugin %s:%s found in %s",
                            plugin.get("groupId"), plugin.get("artifactId"), projectRoot.relativize(pomFile)));
                }
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
}
