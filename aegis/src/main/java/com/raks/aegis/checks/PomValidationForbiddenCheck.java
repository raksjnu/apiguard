package com.raks.aegis.checks;
import com.raks.aegis.model.Check;
import com.raks.aegis.model.CheckResult;
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
public class PomValidationForbiddenCheck extends AbstractCheck {
    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        String validationType = (String) check.getParams().getOrDefault("validationType", "COMBINED");
        List<String> failures = new ArrayList<>();
        List<Path> pomFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            pomFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .filter(path -> !shouldIgnorePath(projectRoot, path)) 
                    .toList();
            if (pomFiles.isEmpty()) {
                return CheckResult.pass(check.getRuleId(), check.getDescription(),
                        "No pom.xml files found (nothing to validate)");
            }
            for (Path pomFile : pomFiles) {
                validatePom(pomFile, check.getParams(), validationType, projectRoot, failures);
            }
        } catch (IOException e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error scanning files: " + e.getMessage());
        }
        // Generate file list for reporting (used in both pass and fail scenarios)
        String fileList = pomFiles.stream()
                .map(projectRoot::relativize)
                .map(Path::toString)
                .collect(java.util.stream.Collectors.joining("; "));

        if (failures.isEmpty()) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    "No forbidden POM elements found\nFiles validated: " + fileList, fileList, fileList);
        } else {
            String technicalMsg = "Forbidden POM elements found:\n• " + String.join("\n• ", failures);
            String foundItems = String.join("; ", failures);
            return CheckResult.fail(check.getRuleId(), check.getDescription(), 
                    getCustomMessage(check, technicalMsg, fileList, foundItems, null), 
                    fileList, foundItems, null);
        }
    }
    private void validatePom(Path pomFile, Map<String, Object> params, String validationType,
            Path projectRoot, List<String> failures) {
        try {
            Document doc = parseXml(pomFile);
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
    private Document parseXml(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(file.toFile());
    }
}
