package com.raks.aegis.checks;

import com.jayway.jsonpath.JsonPath;
import com.raks.aegis.model.Check;
import com.raks.aegis.model.CheckResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JsonGenericCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        if (!isRuleApplicable(projectRoot, check)) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(), "Pre-conditions not met.");
        }

        Map<String, Object> params = getEffectiveParams(check);
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) params.get("filePatterns");
        String jsonPath = (String) params.get("jsonPath");
        String mode = (String) params.getOrDefault("mode", "EXISTS");
        String expectedValue = (String) params.get("expectedValue");
        String operator = (String) params.getOrDefault("operator", "EQ");
        String valueType = (String) params.getOrDefault("valueType", "STRING");
        String matchMode = (String) params.getOrDefault("matchMode", "ALL_FILES");
        boolean resolveProperties = Boolean.parseBoolean(String.valueOf(params.getOrDefault("resolveProperties", "true")));

        if (jsonPath == null && !params.containsKey("requiredElements") && !params.containsKey("requiredFields")) {
             return failConfig(check, "Configuration required: provide 'jsonPath', 'requiredElements', or 'requiredFields'");
        }
        if (filePatterns == null || filePatterns.isEmpty()) return failConfig(check, "filePatterns required");

        boolean includeLinkedConfig = Boolean.parseBoolean(String.valueOf(params.getOrDefault("includeLinkedConfig", "false")));
        List<Path> matchingFiles = findFiles(projectRoot, filePatterns, includeLinkedConfig);
        List<Path> searchRoots = com.raks.aegis.util.ProjectContextHelper.getEffectiveSearchRoots(projectRoot, this.linkedConfigPath, includeLinkedConfig);

        int passedFileCount = 0;
        int totalFiles = matchingFiles.size();
        List<String> details = new ArrayList<>();
        List<String> passedFilesList = new ArrayList<>();
        Set<String> allFoundItems = new LinkedHashSet<>();

        for (Path file : matchingFiles) {
            boolean filePassed = true;
            List<String> fileErrors = new ArrayList<>();
            List<String> fileSuccesses = new ArrayList<>();
            
            Path currentRoot = searchRoots.stream().filter(file::startsWith).findFirst().orElse(projectRoot);
            String relativePath = currentRoot.relativize(file).toString().replace("\\", "/");
            if (currentRoot.equals(linkedConfigPath)) { relativePath = "[Config] " + relativePath; }

            try {
                String content = Files.readString(file);
                Object jsonContext = JsonPath.parse(content).json();

                if (jsonPath != null) {
                    Object result = null;
                    try { result = JsonPath.parse(content).read(jsonPath); } catch (Exception e) { result = null; }

                    if ("EXISTS".equalsIgnoreCase(mode)) {
                        if (result == null) { filePassed = false; fileErrors.add("JSONPath not found: " + jsonPath); }
                        else fileSuccesses.add("Found: " + jsonPath);
                    } else if ("NOT_EXISTS".equalsIgnoreCase(mode)) {
                        if (result != null) { filePassed = false; fileErrors.add("JSONPath found: " + jsonPath); allFoundItems.add(result.toString()); }
                    } else if ("VALUE_MATCH".equalsIgnoreCase(mode)) {
                        if (result == null) { filePassed = false; fileErrors.add("JSONPath not found: " + jsonPath); }
                        else {
                            String actual = result.toString();
                            if (resolveProperties) actual = resolve(actual, currentRoot, this.propertyResolutions);
                            if (!compareValues(actual, expectedValue, operator, valueType)) {
                                filePassed = false; fileErrors.add(String.format("Mismatch: Actual='%s', Expected='%s'", actual, expectedValue));
                            } else fileSuccesses.add(String.format("%s=%s", jsonPath, actual));
                        }
                    }
                }

                // Required Fields
                @SuppressWarnings("unchecked")
                Map<String, String> requiredFields = (Map<String, String>) params.get("requiredFields");
                if (requiredFields != null && jsonContext instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> jsonMap = (Map<String, Object>) jsonContext;
                    for (Map.Entry<String, String> entry : requiredFields.entrySet()) {
                        String key = entry.getKey();
                        String expected = entry.getValue();
                        if (!jsonMap.containsKey(key)) { filePassed = false; fileErrors.add("Missing required field: " + key); }
                        else {
                            String actual = String.valueOf(jsonMap.get(key));
                            if (resolveProperties) actual = resolve(actual, currentRoot, this.propertyResolutions);
                            if (!actual.equals(expected)) { filePassed = false; fileErrors.add("Mismatch at " + key); }
                        }
                    }
                }

            } catch (Exception e) {
                filePassed = false; fileErrors.add("Error: " + e.getMessage());
            }

            if (filePassed) {
                passedFileCount++; passedFilesList.add(relativePath);
            } else {
                details.add(relativePath + " [" + String.join(", ", fileErrors) + "]");
            }
        }

        String checkedFilesStr = String.join(", ", matchingFiles.stream().map(p -> {
            Path r = searchRoots.stream().filter(p::startsWith).findFirst().orElse(projectRoot);
            String rel = r.relativize(p).toString().replace("\\", "/");
            return r.equals(linkedConfigPath) ? "[Config] " + rel : rel;
        }).toList());

        String matchingFilesStr = passedFilesList.isEmpty() ? null : String.join(", ", passedFilesList);
        boolean passed = evaluateMatchMode(matchMode, totalFiles, passedFileCount);

        if (passed) {
            String msg = String.format("Passed JSON check (%d/%d files)", passedFileCount, totalFiles);
            return CheckResult.pass(check.getRuleId(), check.getDescription(), getCustomSuccessMessage(check, msg, checkedFilesStr, matchingFilesStr), checkedFilesStr, matchingFilesStr, this.propertyResolutions);
        } else {
            String msg = String.format("JSON check failed. (Passed: %d/%d)\n• %s", passedFileCount, totalFiles, String.join("\n• ", details));
            return CheckResult.fail(check.getRuleId(), check.getDescription(), getCustomMessage(check, msg, checkedFilesStr, null, matchingFilesStr), checkedFilesStr, null, matchingFilesStr, this.propertyResolutions);
        }
    }

    private CheckResult failConfig(Check check, String msg) {
        return CheckResult.fail(check.getRuleId(), check.getDescription(), "Config Error: " + msg);
    }
}
