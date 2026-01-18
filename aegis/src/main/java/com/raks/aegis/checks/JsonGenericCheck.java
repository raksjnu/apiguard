package com.raks.aegis.checks;

import com.jayway.jsonpath.JsonPath;
import com.raks.aegis.model.Check;
import com.raks.aegis.model.CheckResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Universal JSON Validation Check.
 * Replaces: JsonValidationRequiredCheck, JsonValidationForbiddenCheck.
 * Parameters:
 *  - filePatterns (List<String>): Target files.
 *  - jsonPath (String): JsonPath expression to evaluate.
 *  - mode (String): EXISTS (count > 0) or NOT_EXISTS (count == 0) or VALUE_MATCH.
 *  - expectedValue (String): For VALUE_MATCH mode.
 *  - matchMode (String): ALL_FILES, ANY_FILE, NONE_OF_FILES.
 */
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
        
        // New Parameters for Object Validation
        @SuppressWarnings("unchecked")
        List<String> requiredElements = (List<String>) params.get("requiredElements");
        @SuppressWarnings("unchecked")
        Map<String, String> requiredFields = (Map<String, String>) params.get("requiredFields");
        
        // Validation: At least one main check config must exist
        if (jsonPath == null && (requiredElements == null || requiredElements.isEmpty()) && (requiredFields == null || requiredFields.isEmpty())) {
             return failConfig(check, "Configuration required: provide 'jsonPath', 'requiredElements', or 'requiredFields'");
        }
        if (filePatterns == null || filePatterns.isEmpty()) return failConfig(check, "filePatterns required");

        int passedFileCount = 0;
        int totalFiles = 0;
        List<String> details = new ArrayList<>();
        java.util.Set<String> allFoundItems = new java.util.HashSet<>();
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
                         // 1. JSONPath Check (if configured)
                         if (jsonPath != null) {
                             Object result = null;
                             try {
                                 result = JsonPath.parse(content).read(jsonPath);
                             } catch (Exception e) { result = null; }
                             
                             if ("EXISTS".equalsIgnoreCase(mode)) {
                                 if (result == null) {
                                     filePassed = false;
                                     fileReasons.add("JSONPath not found: " + jsonPath);
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
                                     if (!compareValues(actualStr, expectedValue, operator, valueType)) {
                                         filePassed = false;
                                         fileReasons.add(String.format("Value mismatch at '%s': Actual='%s', Expected='%s'", jsonPath, actualStr, expectedValue));
                                     }
                                 }
                             }
                         }
                         
                         // 1.5 Min Versions Check (Restored Feature)
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
                                     
                                     if (val instanceof List) {
                                         // If array, pass if ANY element >= minVer
                                         for (Object item : (List<?>)val) {
                                             if (compareValues(item.toString(), minVer, "GTE", "SEMVER")) {
                                                 verMatch = true;
                                                 break;
                                             }
                                         }
                                     } else if (val != null) {
                                         if (compareValues(val.toString(), minVer, "GTE", "SEMVER")) {
                                             verMatch = true;
                                         }
                                     }
                                     
                                     if (!verMatch) {
                                         filePassed = false;
                                         fileReasons.add(String.format("Version too low for '%s': Found='%s', Min Required='%s'", key, val, minVer));
                                     }
                                  }
                             }
                         }

                         // 2. Required Elements (Keys) Check
                         if (requiredElements != null && !requiredElements.isEmpty()) {
                             if (jsonContext instanceof Map) {
                                 @SuppressWarnings("unchecked")
                                 Map<String, Object> jsonMap = (Map<String, Object>) jsonContext;
                                 for (String key : requiredElements) {
                                     if (!jsonMap.containsKey(key)) {
                                         filePassed = false;
                                         fileReasons.add("Missing required element: " + key);
                                     }
                                 }
                             } else {
                                 filePassed = false;
                                 fileReasons.add("Root is not a JSON Object, cannot check requiredElements");
                             }
                         }
                         
                         // 3. Required Fields (Key-Value) Check
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
                                         
                                         // Enhanced Logic: Handle Arrays and Loose Equality
                                         boolean match = false;
                                         
                                         // 1. Strict String Equality
                                         if (actual.equals(expected)) match = true;
                                         
                                         // 2. Array Containment (if value is List)
                                         if (!match && val instanceof List) {
                                              List<?> listVal = (List<?>) val;
                                              // Check precise match
                                              if (listVal.contains(expected)) match = true;
                                              // Check toString match (e.g. "17.0" matching "17.0")
                                              else {
                                                  for (Object item : listVal) {
                                                      if (item.toString().equals(expected)) {
                                                          match = true;
                                                          break;
                                                      }
                                                      // Try exact semver match if it looks like a version
                                                      if (compareValues(item.toString(), expected, "EQ", "SEMVER")) {
                                                          match = true;
                                                          break;
                                                      }
                                                  }
                                              }
                                         }
                                         
                                         // 3. Scalar SemVer Match fallback (e.g. "17.0" == "17")
                                         if (!match && compareValues(actual, expected, "EQ", "SEMVER")) {
                                             match = true;
                                         }

                                         if (!match) {
                                             filePassed = false;
                                             fileReasons.add(String.format("Field mismatch '%s': Actual='%s', Expected='%s'", key, actual, expected));
                                         }
                                     }
                                 }
                             } else {
                                 filePassed = false;
                                 fileReasons.add("Root is not a JSON Object, cannot check requiredFields");
                             }
                         }
                    }
                } catch (Exception e) {
                   filePassed = false;
                   fileReasons.add("Processing Error: " + e.getMessage());
                }
                
                if (filePassed) {
                    passedFileCount++;
                    checkedFilesList.add(projectRoot.relativize(file).toString());
                } else {
                    details.add(projectRoot.relativize(file) + " [" + String.join(", ", fileReasons) + "]");
                }
            }

        } catch (Exception e) {
             return CheckResult.fail(check.getRuleId(), check.getDescription(), "Scan Error: " + e.getMessage());
        }

        boolean uniqueCondition = evaluateMatchMode(matchMode, totalFiles, passedFileCount);
        String checkedFilesStr = String.join(", ", checkedFilesList);
        String foundItemsStr = allFoundItems.isEmpty() ? null : String.join(", ", allFoundItems);
        
        if (uniqueCondition) {
            String defaultSuccess = String.format("JSON Check passed for %s files. (Mode: %s, Passed: %d/%d)", mode, matchMode, passedFileCount, totalFiles);
            return CheckResult.pass(check.getRuleId(), check.getDescription(), getCustomSuccessMessage(check, defaultSuccess, checkedFilesStr));
        } else {
            String technicalMsg = String.format("JSON Check failed. (Mode: %s, Passed: %d/%d). Failures:\n• %s", 
                            matchMode, passedFileCount, totalFiles, 
                            details.isEmpty() ? "Pattern mismatch" : String.join("\n• ", details));
            return CheckResult.fail(check.getRuleId(), check.getDescription(), getCustomMessage(check, technicalMsg, checkedFilesStr, foundItemsStr));
        }
    }
    
    private CheckResult failConfig(Check check, String msg) {
        return CheckResult.fail(check.getRuleId(), check.getDescription(), "Config Error: " + msg);
    }
}
