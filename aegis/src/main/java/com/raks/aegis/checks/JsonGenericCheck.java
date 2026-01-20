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
import java.util.stream.Stream;

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
        boolean resolveProperties = Boolean.parseBoolean(String.valueOf(params.getOrDefault("resolveProperties", "false")));

        @SuppressWarnings("unchecked")
        List<String> requiredElements = (List<String>) params.get("requiredElements");
        @SuppressWarnings("unchecked")
        Map<String, String> requiredFields = (Map<String, String>) params.get("requiredFields");

        if (jsonPath == null && (requiredElements == null || requiredElements.isEmpty()) && (requiredFields == null || requiredFields.isEmpty())) {
             return failConfig(check, "Configuration required: provide 'jsonPath', 'requiredElements', or 'requiredFields'");
        }
        if (filePatterns == null || filePatterns.isEmpty()) return failConfig(check, "filePatterns required");

        int passedFileCount = 0;
        int totalFiles = 0;
        List<String> details = new ArrayList<>();
        List<String> successDetails = new ArrayList<>();
        List<String> passedFilesList = new ArrayList<>();
        Set<String> allFoundItems = new LinkedHashSet<>();
        List<String> checkedFilesList = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            List<Path> matchingFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesAnyPattern(path, filePatterns, projectRoot))
                    .filter(path -> !shouldIgnorePath(projectRoot, path))
                    .toList();

            totalFiles = matchingFiles.size();

            for (Path file : matchingFiles) {
                boolean filePassed = true;
                List<String> fileReasons = new ArrayList<>();
                List<String> fileSuccesses = new ArrayList<>();

                try {
                    String content = Files.readString(file);
                    Object jsonContext = null;
                    try {
                        jsonContext = JsonPath.parse(content).json();
                    } catch (Exception e) {
                        filePassed = false;
                        fileReasons.add("JSON Parse Error: " + e.getMessage());
                    }

                    if (filePassed) {

                         if (jsonPath != null) {
                             Object result = null;
                             try {
                                 result = JsonPath.parse(content).read(jsonPath);
                             } catch (Exception e) { result = null; }

                             if ("EXISTS".equalsIgnoreCase(mode)) {
                                 if (result == null) {
                                     filePassed = false;
                                     fileReasons.add("JSONPath not found: " + jsonPath);
                                 } else {
                                     fileSuccesses.add("Found: " + jsonPath);
                                 }
                             } else if ("NOT_EXISTS".equalsIgnoreCase(mode)) {
                                 if (result != null) {
                                     filePassed = false;
                                     fileReasons.add("JSONPath found: " + jsonPath);
                                     if (result instanceof java.util.Collection) {
                                         for (Object o : (java.util.Collection<?>) result) {
                                             allFoundItems.add(o.toString());
                                         }
                                     } else if (result instanceof java.util.Map) {
                                         allFoundItems.addAll(((java.util.Map<?,?>)result).keySet().stream().map(Object::toString).collect(java.util.stream.Collectors.toList()));
                                     } else {
                                         allFoundItems.add(result.toString());
                                     }
                                 }
                             } else if ("VALUE_MATCH".equalsIgnoreCase(mode)) {
                                 if (result == null) {
                                     filePassed = false;
                                     fileReasons.add("JSONPath not found: " + jsonPath);
                                 } else {
                                     String actualStr = result.toString();

                                     String resolvedActual = actualStr;
                                     List<String> localResolutions = new ArrayList<>();
                                     if (resolveProperties) {
                                         resolvedActual = resolve(actualStr, projectRoot, localResolutions);
                                     }

                                     if (!compareValues(resolvedActual, expectedValue, operator, valueType)) {
                                         filePassed = false;
                                         fileReasons.add(String.format("Value mismatch at '%s': Actual='%s', Expected='%s'", jsonPath, resolvedActual, expectedValue));
                                     } else {
                                         fileSuccesses.add(String.format("%s=%s", jsonPath, resolvedActual));
                                         // Record resolutions on success
                                         recordResolutions(localResolutions);
                                     }
                                 }
                             }
                         }

                         @SuppressWarnings("unchecked")
                         Map<String, String> minVersions = (Map<String, String>) params.get("minVersions");
                         if (minVersions != null && !minVersions.isEmpty()) {
                             if (jsonContext instanceof Map) {
                                  @SuppressWarnings("unchecked")
                                  Map<String, Object> jsonMap = (Map<String, Object>) jsonContext;
                                  for (Map.Entry<String, String> entry : minVersions.entrySet()) {
                                     String key = entry.getKey();
                                     String minVer = entry.getValue();

                                     if (!jsonMap.containsKey(key)) {
                                         filePassed = false;
                                         fileReasons.add("Missing field for version check: " + key);
                                         continue;
                                     }

                                     Object val = jsonMap.get(key);
                                     boolean verMatch = false;
                                     List<String> localResolutions = new ArrayList<>();

                                     if (val instanceof List) {
                                         for (Object item : (List<?>)val) {
                                             String actual = item.toString();
                                             if (resolveProperties) {
                                                 actual = resolve(actual, projectRoot, localResolutions);
                                             }
                                             if (compareValues(actual, minVer, "GTE", "SEMVER")) {
                                                 verMatch = true;
                                                 val = actual; // For reporting
                                                 break;
                                             }
                                         }
                                     } else if (val != null) {
                                         String actual = val.toString();
                                         if (resolveProperties) {
                                             actual = resolve(actual, projectRoot, localResolutions);
                                         }
                                         if (compareValues(actual, minVer, "GTE", "SEMVER")) {
                                             verMatch = true;
                                             val = actual; // For reporting
                                         } else {
                                             val = actual; // For reporting mismatch
                                         }
                                     }

                                     if (!verMatch) {
                                         filePassed = false;
                                         fileReasons.add(String.format("Version too low for '%s': Found='%s', Min Required='%s'", key, val, minVer));
                                         allFoundItems.add(String.format("%s=%s", key, val));
                                         // Still record resolutions because they identify the "found" item
                                         recordResolutions(localResolutions);
                                     } else {
                                         fileSuccesses.add(String.format("%s=%s", key, val));
                                         recordResolutions(localResolutions);
                                     }
                                  }
                             }
                         }

                         @SuppressWarnings("unchecked")
                         Map<String, String> exactVersions = (Map<String, String>) params.get("exactVersions");
                         if (exactVersions != null && !exactVersions.isEmpty()) {
                             if (jsonContext instanceof Map) {
                                  @SuppressWarnings("unchecked")
                                  Map<String, Object> jsonMap = (Map<String, Object>) jsonContext;
                                  for (Map.Entry<String, String> entry : exactVersions.entrySet()) {
                                     String key = entry.getKey();
                                     String exactVer = entry.getValue();

                                     if (!jsonMap.containsKey(key)) {
                                         filePassed = false;
                                         fileReasons.add("Missing field for exact version check: " + key);
                                         continue;
                                     }

                                     Object val = jsonMap.get(key);
                                     boolean verMatch = false;
                                     List<String> localResolutions = new ArrayList<>();

                                     if (val instanceof List) {
                                         for (Object item : (List<?>)val) {
                                             String actual = item.toString();
                                             if (resolveProperties) {
                                                 actual = resolve(actual, projectRoot, localResolutions);
                                             }
                                             if (compareValues(actual, exactVer, "EQ", "SEMVER")) {
                                                 verMatch = true;
                                                 val = actual; // For reporting
                                                 break;
                                             }
                                         }
                                     } else if (val != null) {
                                         String actual = val.toString();
                                         if (resolveProperties) {
                                             actual = resolve(actual, projectRoot, localResolutions);
                                         }
                                         if (compareValues(actual, exactVer, "EQ", "SEMVER")) {
                                             verMatch = true;
                                             val = actual; // For reporting
                                         } else {
                                             val = actual; // For reporting mismatch
                                         }
                                     }

                                     if (!verMatch) {
                                         filePassed = false;
                                         fileReasons.add(String.format("Version mismatch for '%s': Found='%s', Expected Exactly='%s'", key, val, exactVer));
                                         allFoundItems.add(String.format("%s=%s", key, val));
                                         recordResolutions(localResolutions);
                                     } else {
                                         fileSuccesses.add(String.format("%s=%s", key, val));
                                         recordResolutions(localResolutions);
                                     }
                                  }
                             }
                         }

                         if (requiredElements != null && !requiredElements.isEmpty()) {
                             if (jsonContext instanceof Map) {
                                 @SuppressWarnings("unchecked")
                                 Map<String, Object> jsonMap = (Map<String, Object>) jsonContext;
                                 for (String key : requiredElements) {
                                     if (!jsonMap.containsKey(key)) {
                                         filePassed = false;
                                         fileReasons.add("Missing required element: " + key);
                                     } else {
                                         fileSuccesses.add(key);
                                     }
                                 }
                             } else {
                                 filePassed = false;
                                 fileReasons.add("Root is not a JSON Object, cannot check requiredElements");
                             }
                         }

                         if (requiredFields != null && !requiredFields.isEmpty()) {
                              if (jsonContext instanceof Map) {
                                 @SuppressWarnings("unchecked")
                                 Map<String, Object> jsonMap = (Map<String, Object>) jsonContext;
                                 for (Map.Entry<String, String> entry : requiredFields.entrySet()) {
                                     String key = entry.getKey();
                                     String expected = entry.getValue();

                                     if (!jsonMap.containsKey(key)) {
                                         filePassed = false;
                                         fileReasons.add("Missing required field: " + key);
                                      } else {
                                          Object val = jsonMap.get(key);
                                          String actual = val != null ? val.toString() : "null";

                                          String resolvedActual = actual;
                                          List<String> localResolutions = new ArrayList<>();
                                          if (resolveProperties) {
                                              resolvedActual = resolve(actual, projectRoot, localResolutions);
                                          }

                                          boolean match = false;

                                          if (resolvedActual.equals(expected)) {
                                              match = true;
                                              // Record resolutions on match
                                              recordResolutions(localResolutions);
                                          }

                                         if (!match && val instanceof List) {
                                              List<?> listVal = (List<?>) val;

                                              if (listVal.contains(expected)) match = true;

                                              else {
                                                  for (Object item : listVal) {
                                                      if (item.toString().equals(expected)) {
                                                          match = true;
                                                          break;
                                                      }

                                                      if (compareValues(item.toString(), expected, "EQ", "SEMVER")) {
                                                          match = true;
                                                          break;
                                                      }
                                                  }
                                              }
                                         }

                                          if (!match && compareValues(resolvedActual, expected, "EQ", "SEMVER")) {
                                              match = true;
                                              recordResolutions(localResolutions);
                                          }

                                          if (!match) {
                                              filePassed = false;
                                              fileReasons.add(String.format("Field mismatch '%s': Actual='%s', Expected='%s'", key, resolvedActual, expected));
                                              allFoundItems.add(String.format("%s=%s", key, resolvedActual));
                                          } else {
                                              fileSuccesses.add(String.format("%s=%s", key, resolvedActual));
                                          }
                                      }
                                 }
                             } else {
                                 filePassed = false;
                                 fileReasons.add("Root is not a JSON Object, cannot check requiredFields");
                             }
                         }

                         @SuppressWarnings("unchecked")
                         Map<String, String> forbiddenFields = (Map<String, String>) params.get("forbiddenFields");
                         if (forbiddenFields != null && !forbiddenFields.isEmpty()) {
                             if (jsonContext instanceof Map) {
                                  @SuppressWarnings("unchecked")
                                  Map<String, Object> jsonMap = (Map<String, Object>) jsonContext;
                                  for (Map.Entry<String, String> entry : forbiddenFields.entrySet()) {
                                     String key = entry.getKey();
                                     String forbiddenVal = entry.getValue();

                                     if (jsonMap.containsKey(key)) {
                                         Object val = jsonMap.get(key);
                                         String actual = val != null ? val.toString() : "null";
                                         String resolvedActual = actual;
                                         List<String> localResolutions = new ArrayList<>();

                                         if (resolveProperties) {
                                             resolvedActual = resolve(actual, projectRoot, localResolutions);
                                         }

                                         boolean match = false;
                                         if (resolvedActual.equals(forbiddenVal)) match = true;
                                         if (!match && compareValues(resolvedActual, forbiddenVal, "EQ", "SEMVER")) match = true;

                                         if (match) {
                                             filePassed = false;
                                             fileReasons.add(String.format("Forbidden field value found '%s': '%s'", key, resolvedActual));
                                             allFoundItems.add(String.format("%s=%s", key, resolvedActual));
                                             recordResolutions(localResolutions);
                                         }
                                     }
                                  }
                             }
                         }

                         @SuppressWarnings("unchecked")
                         Map<String, String> forbiddenVersions = (Map<String, String>) params.get("forbiddenVersions");
                         if (forbiddenVersions != null && !forbiddenVersions.isEmpty()) {
                             if (jsonContext instanceof Map) {
                                  @SuppressWarnings("unchecked")
                                  Map<String, Object> jsonMap = (Map<String, Object>) jsonContext;
                                  for (Map.Entry<String, String> entry : forbiddenVersions.entrySet()) {
                                     String key = entry.getKey();
                                     String forbiddenVer = entry.getValue();

                                     if (jsonMap.containsKey(key)) {
                                         Object val = jsonMap.get(key);
                                         boolean verMatch = false;
                                         String finalActual = val != null ? val.toString() : "null";
                                         List<String> localResolutions = new ArrayList<>();

                                         if (val instanceof List) {
                                             for (Object item : (List<?>)val) {
                                                 String actual = item.toString();
                                                 if (resolveProperties) {
                                                     actual = resolve(actual, projectRoot, localResolutions);
                                                 }
                                                 if (compareValues(actual, forbiddenVer, "EQ", "SEMVER")) {
                                                     verMatch = true;
                                                     finalActual = actual;
                                                     break;
                                                 }
                                             }
                                         } else if (val != null) {
                                             String actual = val.toString();
                                             if (resolveProperties) {
                                                 actual = resolve(actual, projectRoot, localResolutions);
                                             }
                                             if (compareValues(actual, forbiddenVer, "EQ", "SEMVER")) {
                                                 verMatch = true;
                                                 finalActual = actual;
                                             }
                                         }

                                         if (verMatch) {
                                             filePassed = false;
                                             fileReasons.add(String.format("Forbidden version found for '%s': '%s'", key, finalActual));
                                             allFoundItems.add(String.format("%s=%s", key, finalActual));
                                             recordResolutions(localResolutions);
                                         }
                                     }
                                  }
                             }
                         }
                    }
                     checkedFilesList.add(projectRoot.relativize(file).toString()); // Add to checked files regardless of pass/fail
                 } catch (Exception e) {
                    filePassed = false;
                    fileReasons.add("Processing Error: " + e.getMessage());
                 }

                 if (filePassed) {
                     passedFileCount++;
                     passedFilesList.add(projectRoot.relativize(file).toString());
                     successDetails.addAll(fileSuccesses);
                 } else {
                     details.add(projectRoot.relativize(file) + " [" + String.join(", ", fileReasons) + "]");
                     // If we have failure reasons that identify specific keys, we can try to add them to allFoundItems
                     // But better to populate allFoundItems directly where failures are detected?
                     // Since we updated the logic above to NOT populate allFoundItems on failure, let's rely on fileReasons for DETAILS
                     // and populate allFoundItems with the 'Actual' values we found that caused failure.

                     // We will assume the logic above has been updated to populate allFoundItems?
                     // No, the previous logic didn't populate allFoundItems on failure for requiredFields/Versions.
                     // We need to verify if we want to change that in the loop.
                     // Ideally, 'Found Items' should list what we found.
                 }
            }

        } catch (Exception e) {
             return CheckResult.fail(check.getRuleId(), check.getDescription(), "Scan Error: " + e.getMessage());
        }

        boolean uniqueCondition = evaluateMatchMode(matchMode, totalFiles, passedFileCount);
        String checkedFilesStr = String.join(", ", checkedFilesList);
        String foundItemsStr = allFoundItems.isEmpty() ? null : String.join(", ", allFoundItems);
        
        // Populate matchingFiles with passed file names for consistency
        String matchingFilesStr = passedFilesList.isEmpty() ? null : String.join(", ", passedFilesList);

        if (uniqueCondition) {
            String defaultSuccess = String.format("JSON Check passed for %s files. (Mode: %s, Passed: %d/%d)", mode, matchMode, passedFileCount, totalFiles);
            return CheckResult.pass(check.getRuleId(), check.getDescription(), getCustomSuccessMessage(check, defaultSuccess, checkedFilesStr, matchingFilesStr), checkedFilesStr, matchingFilesStr, this.propertyResolutions);
        } else {
            String technicalMsg = String.format("JSON Check failed. (Mode: %s, Passed: %d/%d).\n• %s", 
                            matchMode, passedFileCount, totalFiles, 
                            details.isEmpty() ? "Pattern mismatch" : String.join("\n• ", details));
            return CheckResult.fail(check.getRuleId(), check.getDescription(), getCustomMessage(check, technicalMsg, checkedFilesStr, foundItemsStr, matchingFilesStr), checkedFilesStr, foundItemsStr, matchingFilesStr, this.propertyResolutions);
        }
    }

    private CheckResult failConfig(Check check, String msg) {
        return CheckResult.fail(check.getRuleId(), check.getDescription(), "Config Error: " + msg);
    }
}
