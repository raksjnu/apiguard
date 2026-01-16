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

        Map<String, Object> params = check.getParams();
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) params.get("filePatterns");
        String jsonPath = (String) params.get("jsonPath");
        String mode = (String) params.getOrDefault("mode", "EXISTS");
        String expectedValue = (String) params.get("expectedValue");
        String operator = (String) params.getOrDefault("operator", "EQ");
        String valueType = (String) params.getOrDefault("valueType", "STRING");
        String matchMode = (String) params.getOrDefault("matchMode", "ALL_FILES");

        if (filePatterns == null || filePatterns.isEmpty()) return failConfig(check, "filePatterns required");
        if (jsonPath == null) return failConfig(check, "jsonPath required");

        int passedFileCount = 0;
        int totalFiles = 0;
        List<String> details = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            List<Path> matchingFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesAnyPattern(path, filePatterns, projectRoot))
                    .filter(path -> !shouldIgnorePath(projectRoot, path))
                    .toList();
            
            totalFiles = matchingFiles.size();
            
            for (Path file : matchingFiles) {
                boolean filePassed = false;
                try {
                    String content = Files.readString(file);
                    Object result = null;
                    try {
                        result = JsonPath.parse(content).read(jsonPath);
                    } catch (Exception e) {
                        result = null; 
                    }
                    
                    if ("EXISTS".equalsIgnoreCase(mode)) {
                        if (result != null) filePassed = true;
                        else details.add(projectRoot.relativize(file) + " [Path not found]");
                    } else if ("NOT_EXISTS".equalsIgnoreCase(mode)) {
                        if (result == null) filePassed = true;
                        else details.add(projectRoot.relativize(file) + " [Path found]");
                    } else if ("VALUE_MATCH".equalsIgnoreCase(mode)) {
                        if (result == null) {
                             details.add(projectRoot.relativize(file) + " [Path not found]");
                        } else {
                            // Extract actual string value locally to pass to helper
                            String actualStr = result.toString();
                            if (compareValues(actualStr, expectedValue, operator, valueType)) {
                                filePassed = true;
                            } else {
                                details.add(projectRoot.relativize(file) + 
                                    String.format(" [Value mismatch: Actual='%s', Expected='%s', Op='%s', Type='%s']", 
                                    actualStr, expectedValue, operator, valueType));
                            }
                        }
                    } else {
                        details.add("Unknown mode: " + mode);
                    }
                    
                } catch (Exception e) {
                   details.add(projectRoot.relativize(file) + " [JSON Parse Error: " + e.getMessage() + "]");
                }
                
                if (filePassed) passedFileCount++;
            }

        } catch (Exception e) {
             return CheckResult.fail(check.getRuleId(), check.getDescription(), "Scan Error: " + e.getMessage());
        }

        boolean uniqueCondition = evaluateMatchMode(matchMode, totalFiles, passedFileCount);
        
        if (uniqueCondition) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    String.format("JSON Check passed for %s files. (Mode: %s, Passed: %d/%d)", mode, matchMode, passedFileCount, totalFiles));
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    String.format("JSON Check failed for %s. (Mode: %s, Passed: %d/%d). Failures:\n• %s", 
                            mode, matchMode, passedFileCount, totalFiles, 
                            details.isEmpty() ? "Pattern mismatch" : String.join("\n• ", details)));
        }
    }
    
    private CheckResult failConfig(Check check, String msg) {
        return CheckResult.fail(check.getRuleId(), check.getDescription(), "Config Error: " + msg);
    }
}
