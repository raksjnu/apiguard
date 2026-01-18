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
import java.util.stream.Stream;

public class PropertyGenericCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        if (!isRuleApplicable(projectRoot, check)) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(), "Pre-conditions not met.");
        }

        Map<String, Object> params = getEffectiveParams(check);
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) params.get("filePatterns");

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
        String operator = (String) params.getOrDefault("operator", "MATCHES"); 
        String valueType = (String) params.getOrDefault("valueType", "STRING");
        String matchMode = (String) params.getOrDefault("matchMode", "ALL_FILES");

        if (filePatterns == null || filePatterns.isEmpty()) return failConfig(check, "filePatterns required");

        @SuppressWarnings("unchecked")
        List<Map<String, String>> validationRules = (List<Map<String, String>>) params.get("validationRules");

        if (properties.isEmpty() && (validationRules == null || validationRules.isEmpty())) {
             return failConfig(check, "property, properties, or validationRules required");
        }

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
                    if (file.toString().endsWith(".xml")) props.loadFromXML(is);
                    else props.load(is);

                    if (validationRules != null && !validationRules.isEmpty()) {

                        for (Map<String, String> rule : validationRules) {
                            String type = rule.getOrDefault("type", "REQUIRED");
                            String pattern = rule.get("pattern");
                            String customMsg = rule.get("message");

                            if ("REQUIRED".equalsIgnoreCase(type)) {
                                 if (!props.containsKey(pattern)) {
                                     filePassed = false;
                                     fileErrors.add(formatCustomRuleMessage(customMsg, "Missing required info: " + pattern));
                                 }
                            } else if ("FORMAT".equalsIgnoreCase(type)) {
                                String[] parts = pattern.split("=", 2);
                                if (parts.length == 2) {
                                    String key = parts[0].trim();
                                    String regex = parts[1].trim();
                                    String val = props.getProperty(key);
                                    if (val != null && !val.matches(regex)) {
                                        filePassed = false;
                                         fileErrors.add(formatCustomRuleMessage(customMsg, "Format mismatch for " + key));
                                    }
                                }
                            }
                        }
                    } else {

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
                    } 

                } catch (Exception e) {
                     filePassed = false;
                     fileErrors.add("Read Error: " + e.getMessage());
                }

                if (filePassed) passedFileCount++;
                else details.add(projectRoot.relativize(file) + " [\n" + String.join("\n", fileErrors) + "\n]");
            }

            boolean uniqueCondition = evaluateMatchMode(matchMode, totalFiles, passedFileCount);

            String checkedFilesStr = matchingFiles.stream()
                    .map(p -> projectRoot.relativize(p).toString())
                    .collect(java.util.stream.Collectors.joining(", "));

            if (uniqueCondition) {
                String defaultSuccess = String.format("Property Check passed for %s files. (Mode: %s, Passed: %d/%d)", mode, matchMode, passedFileCount, totalFiles);
                return CheckResult.pass(check.getRuleId(), check.getDescription(), getCustomSuccessMessage(check, defaultSuccess, checkedFilesStr));
            } else {
                String technicalMsg = String.format("Property Check failed for %s. (Mode: %s, Passed: %d/%d). Failures:\n• %s", 
                                mode, matchMode, passedFileCount, totalFiles, 
                                details.isEmpty() ? "Pattern mismatch" : String.join("\n• ", details));
                return CheckResult.fail(check.getRuleId(), check.getDescription(), getCustomMessage(check, technicalMsg, checkedFilesStr));
            }

        } catch (Exception e) {
             return CheckResult.fail(check.getRuleId(), check.getDescription(), "Scan Error: " + e.getMessage());
        }
    }

    private String formatCustomRuleMessage(String customMsg, String defaultDetails) {
        if (customMsg == null || customMsg.isEmpty()) return defaultDetails;

        return customMsg.replace("{DEFAULT_MESSAGE}", defaultDetails).replace("{CORE_DETAILS}", defaultDetails);
    }

    private CheckResult failConfig(Check check, String msg) {
        return CheckResult.fail(check.getRuleId(), check.getDescription(), "Config Error: " + msg);
    }
}
