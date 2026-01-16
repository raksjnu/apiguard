package com.raks.muleguard.checks;
import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
public class JsonValidationRequiredCheck extends AbstractCheck {
    private final ObjectMapper mapper = new ObjectMapper();
    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        String filePattern = (String) check.getParams().get("filePattern");
        if (filePattern == null || filePattern.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'filePattern' parameter is required");
        }
        List<String> failures = new ArrayList<>();
        List<Path> jsonFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            jsonFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(filePattern))
                    .filter(path -> !shouldIgnorePath(projectRoot, path)) 
                    .toList();
            if (jsonFiles.isEmpty()) {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        "No files found matching pattern: " + filePattern);
            }
            for (Path jsonFile : jsonFiles) {
                validateJson(jsonFile, check.getParams(), projectRoot, failures);
            }
        } catch (IOException e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error scanning files: " + e.getMessage());
        }
        if (failures.isEmpty()) {
            String fileList = jsonFiles.stream()
                    .map(projectRoot::relativize)
                    .map(Path::toString)
                    .collect(java.util.stream.Collectors.joining("; "));
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    "All required JSON elements found\nFiles validated: " + fileList);
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "JSON validation failures:\n• " + String.join("\n• ", failures));
        }
    }
    private void validateJson(Path jsonFile, Map<String, Object> params, Path projectRoot, List<String> failures) {
        try {
            JsonNode root = mapper.readTree(jsonFile.toFile());
            @SuppressWarnings("unchecked")
            Map<String, String> minVersions = (Map<String, String>) params.get("minVersions");
            if (minVersions != null) {
                validateMinVersions(root, minVersions, jsonFile, projectRoot, failures);
            }
            @SuppressWarnings("unchecked")
            Map<String, String> requiredFields = (Map<String, String>) params.get("requiredFields");
            if (requiredFields != null) {
                validateRequiredFields(root, requiredFields, jsonFile, projectRoot, failures);
            }
            @SuppressWarnings("unchecked")
            List<String> requiredElements = (List<String>) params.get("requiredElements");
            if (requiredElements != null) {
                validateRequiredElements(root, requiredElements, jsonFile, projectRoot, failures);
            }
        } catch (Exception e) {
            failures.add("Error parsing JSON file " + projectRoot.relativize(jsonFile) + ": " + e.getMessage());
        }
    }
    private void validateMinVersions(JsonNode root, Map<String, String> minVersions, Path jsonFile,
            Path projectRoot, List<String> failures) {
        for (Map.Entry<String, String> entry : minVersions.entrySet()) {
            String field = entry.getKey();
            String minVersion = entry.getValue();
            JsonNode node = root.get(field);
            if (node == null) {
                failures.add(String.format("Field '%s' missing in %s", field, projectRoot.relativize(jsonFile)));
            } else {
                String actualVersion = node.asText();
                // ValueMatcher handles ">= minVersion" if passing SEMANTIC_VERSION.
                // Construct condition: ">= " + minVersion
                String criteria = ">=" + minVersion;
                boolean satisfies = com.raks.muleguard.util.ValueMatcher.matches(
                    actualVersion, criteria, com.raks.muleguard.util.ValueMatcher.MatchMode.SEMANTIC_VERSION, true);
                
                if (!satisfies) {
                    failures.add(String.format("Field '%s' version too low in %s: expected >= %s, got %s",
                            field, projectRoot.relativize(jsonFile), minVersion, actualVersion));
                }
            }
        }
    }
    private void validateRequiredFields(JsonNode root, Map<String, String> requiredFields, Path jsonFile,
            Path projectRoot, List<String> failures) {
        for (Map.Entry<String, String> entry : requiredFields.entrySet()) {
            String field = entry.getKey();
            String expectedValue = entry.getValue();
            JsonNode node = root.get(field);
            if (node == null) {
                failures.add(String.format("Field '%s' missing in %s", field, projectRoot.relativize(jsonFile)));
            } else {
                if (node.isArray()) {
                    boolean found = false;
                    for (JsonNode element : node) {
                        if (expectedValue.equals(element.asText())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                         failures.add(String.format("Field '%s' (array) does not contain expected value '%s' in %s",
                            field, expectedValue, projectRoot.relativize(jsonFile)));
                    }
                } else {
                    String actualValue = node.asText();
                    if (!expectedValue.equals(actualValue)) {
                        failures.add(String.format("Field '%s' has wrong value in %s: expected '%s', got '%s'",
                                field, projectRoot.relativize(jsonFile), expectedValue, actualValue));
                    }
                }
            }
        }
    }
    private void validateRequiredElements(JsonNode root, List<String> requiredElements, Path jsonFile,
            Path projectRoot, List<String> failures) {
        for (String element : requiredElements) {
            if (!root.has(element)) {
                failures.add(String.format("Element '%s' missing in %s", element, projectRoot.relativize(jsonFile)));
            }
        }
    }

}
