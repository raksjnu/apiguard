package com.raks.aegis.checks;

import com.raks.aegis.model.Check;
import com.raks.aegis.model.CheckResult;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Universal Property Validation Check.
 * Replaces: GenericPropertyFileCheck, MandatoryPropertyValueCheck, OptionalPropertyValueCheck.
 * Parameters:
 *  - filePatterns (List<String>): Target files.
 *  - property (String): The property key to find.
 *  - value (String, Optional): The expected value (regex).
 *  - mode (String): EXISTS (key must exist), NOT_EXISTS, VALUE_MATCH (key must exist AND match value).
 *  - matchMode (String): ALL_FILES, ANY_FILE, NONE_OF_FILES.
 */
public class PropertyGenericCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        if (!isRuleApplicable(projectRoot, check)) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(), "Pre-conditions not met.");
        }

        Map<String, Object> params = check.getParams();
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) params.get("filePatterns");
        
        // Support single 'property' or list 'properties'
        List<String> properties = new ArrayList<>();
        if (params.containsKey("property")) {
            properties.add((String) params.get("property"));
        }
        if (params.containsKey("properties")) {
            Object propsObj = params.get("properties");
            if (propsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> pList = (List<String>) propsObj;
                properties.addAll(pList);
            }
        }
        
        String expectedValueRegex = (String) params.get("value");
        String mode = (String) params.getOrDefault("mode", "EXISTS");
        String operator = (String) params.getOrDefault("operator", "MATCHES"); // Default to Regex for backward compatibility
        String valueType = (String) params.getOrDefault("valueType", "STRING");
        String matchMode = (String) params.getOrDefault("matchMode", "ALL_FILES");

        if (filePatterns == null || filePatterns.isEmpty()) return failConfig(check, "filePatterns required");
        if (properties.isEmpty()) return failConfig(check, "property or properties required");

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
                boolean filePassed = true;
                List<String> fileErrors = new ArrayList<>();
                
                try (InputStream is = Files.newInputStream(file)) {
                    Properties props = new Properties();
                    if (file.toString().endsWith(".xml")) {
                        props.loadFromXML(is);
                    } else {
                        props.load(is);
                    }
                    
                    for (String propertyKey : properties) {
                        String actualValue = props.getProperty(propertyKey);
                        
                        if ("EXISTS".equalsIgnoreCase(mode)) {
                            if (actualValue == null) {
                                filePassed = false;
                                fileErrors.add("Missing Key: " + propertyKey);
                            }
                        } else if ("NOT_EXISTS".equalsIgnoreCase(mode)) {
                            if (actualValue != null) {
                                filePassed = false;
                                fileErrors.add("Forbidden Key found: " + propertyKey);
                            }
                        } else if ("VALUE_MATCH".equalsIgnoreCase(mode)) {
                            if (actualValue == null) {
                                filePassed = false;
                                fileErrors.add("Missing Key: " + propertyKey);
                            } else {
                                if (!compareValues(actualValue, expectedValueRegex, operator, valueType)) {
                                    filePassed = false;
                                    fileErrors.add(String.format("Value mismatch: %s='%s', Expected='%s', Op='%s'", 
                                        propertyKey, actualValue, expectedValueRegex, operator));
                                }
                            }
                        } else if ("OPTIONAL_MATCH".equalsIgnoreCase(mode)) {
                            if (actualValue != null) {
                                if (!compareValues(actualValue, expectedValueRegex, operator, valueType)) {
                                    filePassed = false;
                                    fileErrors.add(String.format("Value mismatch: %s='%s', Expected='%s', Op='%s'", 
                                        propertyKey, actualValue, expectedValueRegex, operator));
                                }
                            }
                        }
                    }
                    
                } catch (Exception e) {
                   filePassed = false;
                   fileErrors.add("Read Error: " + e.getMessage());
                }
                
                if (filePassed) {
                    passedFileCount++;
                } else {
                    details.add(projectRoot.relativize(file) + " [" + String.join(", ", fileErrors) + "]");
                }
            }

        } catch (Exception e) {
             return CheckResult.fail(check.getRuleId(), check.getDescription(), "Scan Error: " + e.getMessage());
        }

        boolean uniqueCondition = evaluateMatchMode(matchMode, totalFiles, passedFileCount);
        
        if (uniqueCondition) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    String.format("Property Check passed for %s files. (Mode: %s, Passed: %d/%d)", mode, matchMode, passedFileCount, totalFiles));
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    String.format("Property Check failed for %s. (Mode: %s, Passed: %d/%d). Failures:\n• %s", 
                            mode, matchMode, passedFileCount, totalFiles, 
                            details.isEmpty() ? "Pattern mismatch" : String.join("\n• ", details)));
        }
    }
    
    private CheckResult failConfig(Check check, String msg) {
        return CheckResult.fail(check.getRuleId(), check.getDescription(), "Config Error: " + msg);
    }
}
