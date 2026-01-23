package com.raks.aegis.checks;

import com.raks.aegis.model.Check;
import com.raks.aegis.model.CheckResult;

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

        java.util.Set<Path> matchedPathsSet = new java.util.HashSet<>();
        for (Path file : matchingFiles) {
            boolean filePassed = true;
            List<String> fileErrors = new ArrayList<>();
            List<String> fileSuccesses = new ArrayList<>();
            
            Path currentRoot = searchRoots.stream().filter(file::startsWith).findFirst().orElse(projectRoot);
            String relativePath = currentRoot.relativize(file).toString().replace("\\", "/");
            if (currentRoot.equals(linkedConfigPath)) { relativePath = "[Config] " + relativePath; }

            try {
                String content = readFileContent(file, params);
                Object jsonContext = com.jayway.jsonpath.JsonPath.parse(content).json();

                if (jsonPath != null && !"$".equals(jsonPath)) {
                    Object result = null;
                    try { result = com.jayway.jsonpath.JsonPath.parse(content).read(jsonPath); } catch (Exception e) { result = null; }

                    if ("EXISTS".equalsIgnoreCase(mode) || "OPTIONAL_MATCH".equalsIgnoreCase(mode)) {
                        if (result == null) { 
                            if (!"OPTIONAL_MATCH".equalsIgnoreCase(mode)) {
                                filePassed = false; 
                                fileErrors.add("JSONPath not found: " + jsonPath); 
                            }
                        } else {
                            fileSuccesses.add("Found: " + jsonPath);
                            allFoundItems.add(jsonPath + " exists");
                        }
                    } else if ("NOT_EXISTS".equalsIgnoreCase(mode)) {
                        // ... existing logic for NOT_EXISTS
                        if (result != null) { 
                            String forbiddenValue = (String) params.get("forbiddenValue");
                            if (forbiddenValue == null) {
                                filePassed = false; fileErrors.add("JSONPath found: " + jsonPath); allFoundItems.add(result.toString()); 
                            } else {
                                String rawValue = result.toString();
                                java.util.Set<String> allValues = resolveAndRecord(rawValue, currentRoot, java.util.Collections.singletonList(forbiddenValue), false);
                                for (String actual : allValues) {
                                    if (compareValues(actual, forbiddenValue, operator, valueType)) {
                                        filePassed = false; 
                                        fileErrors.add("Forbidden value '" + forbiddenValue + "' found at resolution: '" + actual + "' for JSONPath: " + jsonPath);
                                        allFoundItems.add(actual);
                                    }
                                }
                            }
                        }
                    } else if ("VALUE_MATCH".equalsIgnoreCase(mode) || "OPTIONAL_MATCH".equalsIgnoreCase(mode)) {
                        if (result == null) { 
                            if (!"OPTIONAL_MATCH".equalsIgnoreCase(mode)) {
                                filePassed = false; 
                                fileErrors.add("JSONPath not found: " + jsonPath); 
                            }
                        } else if (expectedValue != null) {
                            String rawValue = result.toString();
                            java.util.Set<String> allValues = resolveAndRecord(rawValue, currentRoot, java.util.Collections.singletonList(expectedValue), true);
                            for (String actual : allValues) {
                                if (!compareValues(actual, expectedValue, operator, valueType)) {
                                    filePassed = false; 
                                    fileErrors.add(String.format("Resolution mismatch: Actual='%s', Expected='%s' (from raw: '%s')", actual, expectedValue, rawValue));
                                } else {
                                    fileSuccesses.add(String.format("%s=%s", jsonPath, actual));
                                    allFoundItems.add(String.format("%s: %s", jsonPath, actual));
                                }
                            }
                        }
                    }
                }

                // Required Elements (Existence only)
                @SuppressWarnings("unchecked")
                List<String> requiredElements = (List<String>) params.get("requiredElements");
                if (requiredElements != null && jsonContext instanceof Map) {
                    Map<?, ?> jsonMap = (Map<?, ?>) jsonContext;
                    for (String element : requiredElements) {
                        if (!jsonMap.containsKey(element)) {
                            filePassed = false;
                            fileErrors.add("Missing required element: " + element);
                        } else {
                            fileSuccesses.add("Element exists: " + element);
                            allFoundItems.add("Element: " + element + " (exists)");
                        }
                    }
                }

                // Exact Versions (Value matching)
                @SuppressWarnings("unchecked")
                Map<String, String> exactVersions = (Map<String, String>) params.get("exactVersions");
                if (exactVersions != null && jsonContext instanceof Map) {
                    Map<?, ?> jsonMap = (Map<?, ?>) jsonContext;
                    for (Map.Entry<String, String> entry : exactVersions.entrySet()) {
                        String key = entry.getKey();
                        String expected = String.valueOf(entry.getValue());
                        if (!jsonMap.containsKey(key)) {
                            filePassed = false;
                            fileErrors.add("Missing field for version check: " + key);
                        } else {
                            String actual = String.valueOf(jsonMap.get(key));
                            if (!compareValues(actual, expected, operator, valueType)) {
                                filePassed = false;
                                fileErrors.add(String.format("Version mismatch at '%s': Actual='%s', Expected='%s'", key, actual, expected));
                            } else {
                                fileSuccesses.add(String.format("%s=%s", key, actual));
                                allFoundItems.add(String.format("%s: %s (in %s)", key, actual, relativePath));
                            }
                        }
                    }
                }

                // Required Fields (Value matching with resolution)
                @SuppressWarnings("unchecked")
                Map<String, String> requiredFields = (Map<String, String>) params.get("requiredFields");
                if (requiredFields != null && jsonContext instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> jsonMap = (Map<String, Object>) jsonContext;
                    for (Map.Entry<String, String> entry : requiredFields.entrySet()) {
                        String key = entry.getKey();
                        String expected = entry.getValue();
                        if (!jsonMap.containsKey(key)) {
                            filePassed = false;
                            fileErrors.add("Missing required field: " + key);
                        } else {
                            Object actualRaw = jsonMap.get(key);
                            List<String> actualsToCompare = new ArrayList<>();
                            
                            if (actualRaw instanceof List) {
                                for (Object item : (List<?>) actualRaw) {
                                    if (item != null) actualsToCompare.add(String.valueOf(item).trim());
                                }
                            } else if (actualRaw != null) {
                                actualsToCompare.add(String.valueOf(actualRaw).trim());
                            }

                            boolean foundMatch = false;
                            List<String> rawResolutions = new ArrayList<>();
                            String matchedActual = null;
                            for (String raw : actualsToCompare) {
                                java.util.Set<String> resolvedValues = resolveAndRecord(raw, currentRoot, java.util.Collections.singletonList(expected), true);
                                for (String actual : resolvedValues) {
                                    if (compareValues(actual, expected, operator, valueType)) {
                                        foundMatch = true;
                                        matchedActual = actual;
                                        break;
                                    } else {
                                        rawResolutions.add(actual);
                                    }
                                }
                                if (foundMatch) break;
                            }

                            if (!foundMatch) {
                                filePassed = false;
                                if (rawResolutions.isEmpty()) {
                                    fileErrors.add(String.format("Field '%s' check failed (no values found)", key));
                                } else {
                                    fileErrors.add(String.format("Resolution mismatch at '%s': Actual='%s', Expected='%s'", key, rawResolutions.toString(), expected));
                                }
                            } else {
                                fileSuccesses.add(String.format("%s MATCHED", key));
                                allFoundItems.add(String.format("%s: %s (in %s)", key, matchedActual, relativePath));
                            }
                        }
                    }
                }

            } catch (Exception e) {
                filePassed = false; fileErrors.add("Error: " + e.getMessage());
            }

            if (filePassed) {
                passedFileCount++; passedFilesList.add(relativePath);
                if (!fileSuccesses.isEmpty()) {
                    matchedPathsSet.add(file);
                }
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
        String foundItemsStr = allFoundItems.isEmpty() ? null : String.join(", ", allFoundItems);
        boolean passed = evaluateMatchMode(matchMode, totalFiles, passedFileCount);

        if (passed) {
            String msg = String.format("Passed JSON check (%d/%d files)", passedFileCount, totalFiles);
            return finalizePass(check, msg, checkedFilesStr, foundItemsStr, matchingFilesStr, matchedPathsSet);
        } else {
            String failDetailsStr = String.join("; ", details);
            String msg = String.format("JSON check failed. (Passed: %d/%d)\n• %s", passedFileCount, totalFiles, String.join("\n• ", details));
            return finalizeFail(check, msg, checkedFilesStr, failDetailsStr, matchingFilesStr, matchedPathsSet);
        }
    }


    private CheckResult failConfig(Check check, String msg) {
        return CheckResult.fail(check.getRuleId(), check.getDescription(), "Config Error: " + msg);
    }
}
