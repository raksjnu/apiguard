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

/**
 * JSON Validation Forbidden Check - Validates that forbidden JSON elements do
 * NOT exist.
 * 
 * @author Rakesh Kumar (raksjnu@gmail.com)
 */
public class JsonValidationForbiddenCheck extends AbstractCheck {

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
                    .filter(path -> !shouldIgnorePath(projectRoot, path)) // Filter ignored folders
                    .toList();

            if (jsonFiles.isEmpty()) {
                return CheckResult.pass(check.getRuleId(), check.getDescription(),
                        "No files found matching pattern (nothing to validate)");
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
                    "No forbidden JSON elements found\nFiles validated: " + fileList);
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Forbidden JSON elements found:\n• " + String.join("\n• ", failures));
        }
    }

    private void validateJson(Path jsonFile, Map<String, Object> params, Path projectRoot, List<String> failures) {
        try {
            JsonNode root = mapper.readTree(jsonFile.toFile());

            // Validate forbidden elements
            @SuppressWarnings("unchecked")
            List<String> forbiddenElements = (List<String>) params.get("forbiddenElements");
            if (forbiddenElements != null) {
                validateForbiddenElements(root, forbiddenElements, jsonFile, projectRoot, failures);
            }

            // Validate forbidden field values
            @SuppressWarnings("unchecked")
            List<Map<String, String>> forbiddenFieldValues = (List<Map<String, String>>) params
                    .get("forbiddenFieldValues");
            if (forbiddenFieldValues != null) {
                validateForbiddenFieldValues(root, forbiddenFieldValues, jsonFile, projectRoot, failures);
            }

        } catch (Exception e) {
            failures.add("Error parsing JSON file " + projectRoot.relativize(jsonFile) + ": " + e.getMessage());
        }
    }

    private void validateForbiddenElements(JsonNode root, List<String> forbiddenElements, Path jsonFile,
            Path projectRoot, List<String> failures) {
        for (String element : forbiddenElements) {
            if (root.has(element)) {
                failures.add(String.format("%s has forbidden element: %s",
                        jsonFile.getFileName().toString(), element));
            }
        }
    }

    private void validateForbiddenFieldValues(JsonNode root, List<Map<String, String>> forbiddenFieldValues,
            Path jsonFile, Path projectRoot, List<String> failures) {
        for (Map<String, String> fieldValue : forbiddenFieldValues) {
            String field = fieldValue.get("field");
            String forbiddenValue = fieldValue.get("forbiddenValue");

            JsonNode node = root.get(field);
            if (node != null && forbiddenValue.equals(node.asText())) {
                failures.add(String.format("Forbidden value '%s' found for field '%s' in %s",
                        forbiddenValue, field, projectRoot.relativize(jsonFile)));
            }
        }
    }
}
