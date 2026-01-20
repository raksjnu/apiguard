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
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Stream;

public class PropertyGenericCheck extends AbstractCheck {

    private static class PropertyConstraint {
        String name;
        List<String> allowedValues;

        PropertyConstraint(String name, List<String> allowedValues) {
            this.name = name;
            this.allowedValues = allowedValues;
        }
    }

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        if (!isRuleApplicable(projectRoot, check)) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(), "Pre-conditions not met.");
        }

        Map<String, Object> params = getEffectiveParams(check);
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) params.get("filePatterns");

        List<PropertyConstraint> propertyConstraints = new ArrayList<>();

        if (params.containsKey("property")) {
            propertyConstraints.add(new PropertyConstraint((String) params.get("property"), null));
        }

        if (params.containsKey("properties")) {
            Object propsObj = params.get("properties");
            if (propsObj instanceof List) {
                List<?> list = (List<?>) propsObj;
                for (Object item : list) {
                    if (item instanceof String) {
                        propertyConstraints.add(new PropertyConstraint((String) item, null));
                    } else if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) item;
                        String name = (String) map.get("name");
                        @SuppressWarnings("unchecked")
                        List<String> values = (List<String>) map.get("values");
                        propertyConstraints.add(new PropertyConstraint(name, values));
                    }
                }
            }
        }

        String expectedValueRegex = (String) params.get("value");
        String mode = (String) params.getOrDefault("mode", "EXISTS");
        String operator = (String) params.getOrDefault("operator", "MATCHES"); 
        String valueType = (String) params.getOrDefault("valueType", "STRING");
        String matchMode = (String) params.getOrDefault("matchMode", "ALL_FILES");
        boolean resolveProperties = Boolean.parseBoolean(String.valueOf(params.getOrDefault("resolveProperties", "false")));

        if (filePatterns == null || filePatterns.isEmpty()) return failConfig(check, "filePatterns required");

        @SuppressWarnings("unchecked")
        List<Map<String, String>> validationRules = (List<Map<String, String>>) params.get("validationRules");

        if (propertyConstraints.isEmpty() && (validationRules == null || validationRules.isEmpty()) && 
            !params.containsKey("minVersions") && !params.containsKey("exactVersions") && !params.containsKey("requiredFields")) {
             return failConfig(check, "property, properties, validationRules, or standard validation params (minVersions, etc.) required");
        }

        int passedFileCount = 0;
        int totalFiles = 0;
        List<String> details = new ArrayList<>();
        List<String> successDetails = new ArrayList<>(); // Changed from Set to List
        Set<String> allFoundItems = new LinkedHashSet<>(); // Changed from HashSet to LinkedHashSet
        List<String> checkedFilesList = new ArrayList<>();
        List<String> passedFilesList = new ArrayList<>(); // Track passing files

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
                List<String> fileSuccesses = new ArrayList<>(); // Renamed to fileFoundItems in instruction, but keeping fileSuccesses for consistency with existing logic
                List<String> fileFoundItems = new ArrayList<>(); // Added to match instruction for successDetails.addAll

                try (InputStream is = Files.newInputStream(file)) {
                    Properties props = new Properties();
                    if (file.toString().endsWith(".xml")) props.loadFromXML(is);
                    else props.load(is);

                    // Standard: requiredFields (Exact Match)
                    @SuppressWarnings("unchecked")
                    Map<String, String> requiredFields = (Map<String, String>) params.get("requiredFields");
                    if (requiredFields != null) {
                        for (Map.Entry<String, String> entry : requiredFields.entrySet()) {
                            String key = entry.getKey();
                            String expected = entry.getValue();
                            String actual = props.getProperty(key);

                            if (actual == null) {
                                filePassed = false;
                                fileErrors.add("Missing field: " + key);
                            } else {
                                List<String> localResolutions = new ArrayList<>();
                                if (resolveProperties) {
                                    actual = resolve(actual, projectRoot, localResolutions);
                                }
                                if (!actual.equals(expected)) {
                                    filePassed = false;
                                    fileErrors.add(String.format("Field mismatch '%s': Found='%s', Expected='%s'", key, actual, expected));
                                    allFoundItems.add(String.format("%s=%s", key, actual));
                                    fileFoundItems.add(String.format("%s=%s", key, actual));
                                    // Record on failure because it identifies the mismatching property
                                    recordResolutions(localResolutions);
                                } else {
                                    fileSuccesses.add(String.format("%s=%s", key, actual));
                                    allFoundItems.add(String.format("%s=%s", key, actual));
                                    fileFoundItems.add(String.format("%s=%s", key, actual));
                                    // Record on match
                                    recordResolutions(localResolutions);
                                }
                            }
                        }
                    }

                    // Standard: minVersions (>=)
                    @SuppressWarnings("unchecked")
                    Map<String, String> minVersions = (Map<String, String>) params.get("minVersions");
                    if (minVersions != null) {
                        for (Map.Entry<String, String> entry : minVersions.entrySet()) {
                            String key = entry.getKey();
                            String minVer = entry.getValue();
                            String actual = props.getProperty(key);

                            if (actual == null) {
                                filePassed = false;
                                fileErrors.add("Missing version field: " + key);
                            } else {
                                List<String> localResolutions = new ArrayList<>();
                                if (resolveProperties) {
                                    actual = resolve(actual, projectRoot, localResolutions);
                                }
                                if (!compareValues(actual, minVer, "GTE", "SEMVER")) {
                                    filePassed = false;
                                    fileErrors.add(String.format("Version too low '%s': Found='%s', Min='%s'", key, actual, minVer));
                                    allFoundItems.add(String.format("%s=%s", key, actual));
                                    fileFoundItems.add(String.format("%s=%s", key, actual));
                                    recordResolutions(localResolutions);
                                } else {
                                    fileSuccesses.add(String.format("%s=%s", key, actual));
                                    allFoundItems.add(String.format("%s=%s", key, actual));
                                    fileFoundItems.add(String.format("%s=%s", key, actual));
                                    recordResolutions(localResolutions);
                                }
                            }
                        }
                    }

                    // Standard: exactVersions (==)
                    @SuppressWarnings("unchecked")
                    Map<String, String> exactVersions = (Map<String, String>) params.get("exactVersions");
                    if (exactVersions != null) {
                        for (Map.Entry<String, String> entry : exactVersions.entrySet()) {
                            String key = entry.getKey();
                            String exactVer = entry.getValue();
                            String actual = props.getProperty(key);

                            if (actual == null) {
                                filePassed = false;
                                fileErrors.add("Missing version field: " + key);
                            } else {
                                List<String> localResolutions = new ArrayList<>();
                                if (resolveProperties) {
                                    actual = resolve(actual, projectRoot, localResolutions);
                                }
                                if (!compareValues(actual, exactVer, "EQ", "SEMVER")) {
                                    filePassed = false;
                                    fileErrors.add(String.format("Version mismatch '%s': Found='%s', Expected='%s'", key, actual, exactVer));
                                    allFoundItems.add(String.format("%s=%s", key, actual));
                                    fileFoundItems.add(String.format("%s=%s", key, actual));
                                    recordResolutions(localResolutions);
                                } else {
                                    fileSuccesses.add(String.format("%s=%s", key, actual));
                                    allFoundItems.add(String.format("%s=%s", key, actual));
                                    fileFoundItems.add(String.format("%s=%s", key, actual));
                                    recordResolutions(localResolutions);
                                }
                            }
                        }
                    }

                    if (validationRules != null && !validationRules.isEmpty()) {
                        for (Map<String, String> rule : validationRules) {
                            String type = rule.getOrDefault("type", "REQUIRED");
                            String pattern = rule.get("pattern");
                            String customMsg = rule.get("message");

                            if ("REQUIRED".equalsIgnoreCase(type)) {
                                 if (!props.containsKey(pattern)) {
                                     filePassed = false;
                                     fileErrors.add(formatCustomRuleMessage(customMsg, "Missing required info: " + pattern));
                                 } else {
                                     fileSuccesses.add(pattern + " (Verified)");
                                     fileFoundItems.add(pattern + " (Verified)");
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
                                         allFoundItems.add(String.format("%s=%s", key, val));
                                         fileFoundItems.add(String.format("%s=%s", key, val));
                                    } else if (val != null) {
                                        fileSuccesses.add(key + "=" + val);
                                        allFoundItems.add(String.format("%s=%s", key, val));
                                        fileFoundItems.add(String.format("%s=%s", key, val));
                                    }
                                }
                            }
                        }
                    } else if (propertyConstraints != null && !propertyConstraints.isEmpty()) {
                        for (PropertyConstraint constraint : propertyConstraints) {
                            String propertyKey = constraint.name;
                            String actualValue = props.getProperty(propertyKey);

                            if ("EXISTS".equalsIgnoreCase(mode)) {
                                if (actualValue == null) {
                                    filePassed = false;
                                    fileErrors.add("Missing Key: " + propertyKey);
                                } else {
                                    fileSuccesses.add(propertyKey + "=" + actualValue);
                                    allFoundItems.add(propertyKey + "=" + actualValue);
                                    fileFoundItems.add(propertyKey + "=" + actualValue);
                                }
                            } else if ("NOT_EXISTS".equalsIgnoreCase(mode)) {
                                if (actualValue != null) {
                                    filePassed = false;
                                    fileErrors.add("Forbidden Key found: " + propertyKey);
                                    allFoundItems.add(propertyKey + "=" + actualValue);
                                    fileFoundItems.add(propertyKey + "=" + actualValue);
                                }
                            } else if ("VALUE_MATCH".equalsIgnoreCase(mode)) {
                                if (actualValue == null) {
                                    filePassed = false;
                                    fileErrors.add("Missing Key: " + propertyKey);
                                } else {

                                    boolean valueMatch = false;

                                    List<String> localResolutions = new ArrayList<>();
                                    if (resolveProperties) {
                                        actualValue = resolve(actualValue, projectRoot, localResolutions);
                                    }

                                    if (constraint.allowedValues != null && !constraint.allowedValues.isEmpty()) {

                                        for (String allowed : constraint.allowedValues) {
                                            if (compareValues(actualValue, allowed, operator, valueType)) {
                                                valueMatch = true;
                                                break;
                                            }
                                        }
                                        if (!valueMatch) {
                                            filePassed = false;
                                            fileErrors.add(String.format("Invalid value for '%s'. Found: '%s', Expected one of: %s", propertyKey, actualValue, constraint.allowedValues));
                                            allFoundItems.add(propertyKey + "=" + actualValue);
                                            fileFoundItems.add(propertyKey + "=" + actualValue);
                                            recordResolutions(localResolutions);
                                        } else {
                                            fileSuccesses.add(propertyKey + "=" + actualValue);
                                            allFoundItems.add(propertyKey + "=" + actualValue);
                                            fileFoundItems.add(propertyKey + "=" + actualValue);
                                            recordResolutions(localResolutions);
                                        }
                                    } else {

                                        if (!compareValues(actualValue, expectedValueRegex, operator, valueType)) {
                                            filePassed = false;
                                            fileErrors.add(String.format("Value mismatch for '%s'. Found: '%s', Expected: '%s' (Op: '%s')", 
                                                propertyKey, actualValue, expectedValueRegex, operator));
                                            allFoundItems.add(propertyKey + "=" + actualValue);
                                            fileFoundItems.add(String.format("%s=%s", propertyKey, actualValue));
                                            recordResolutions(localResolutions);
                                        } else {
                                            fileSuccesses.add(propertyKey + "=" + actualValue);
                                            allFoundItems.add(propertyKey + "=" + actualValue);
                                            fileFoundItems.add(String.format("%s=%s", propertyKey, actualValue));
                                            recordResolutions(localResolutions);
                                        }
                                    }
                                }
                            } else if ("OPTIONAL_MATCH".equalsIgnoreCase(mode)) {
                                if (actualValue != null) {
                                    boolean foundMismatch = false;
                                    List<String> localResolutions = new ArrayList<>();
                                    if (resolveProperties) {
                                        actualValue = resolve(actualValue, projectRoot, localResolutions);
                                    }
                                    if (!compareValues(actualValue, expectedValueRegex, operator, valueType)) {
                                        foundMismatch = true;
                                    }

                                    if (foundMismatch) {
                                        filePassed = false;
                                        fileErrors.add(String.format("Value mismatch: %s='%s', Expected='%s', Op='%s'", 
                                            propertyKey, actualValue, expectedValueRegex, operator));
                                        allFoundItems.add(propertyKey + "=" + actualValue);
                                        fileFoundItems.add(String.format("%s=%s", propertyKey, actualValue));
                                        recordResolutions(localResolutions);
                                    } else {
                                        fileSuccesses.add(propertyKey + "=" + actualValue);
                                        allFoundItems.add(propertyKey + "=" + actualValue);
                                        fileFoundItems.add(String.format("%s=%s", propertyKey, actualValue));
                                        recordResolutions(localResolutions);
                                    }
                                }
                            }
                        }
                    } 

                    checkedFilesList.add(projectRoot.relativize(file).toString()); // Always add checked file

                } catch (Exception e) {
                     filePassed = false;
                     fileErrors.add("Read Error: " + e.getMessage());
                }

                if (filePassed) {
                    passedFileCount++;
                    passedFilesList.add(projectRoot.relativize(file).toString()); // Add filename to passing list
                    successDetails.addAll(fileFoundItems);
                } else {
                    details.add(projectRoot.relativize(file) + " [" + String.join(", ", fileErrors) + "]");
                }
            }

            boolean uniqueCondition = evaluateMatchMode(matchMode, totalFiles, passedFileCount);

            String checkedFilesStr = String.join(", ", checkedFilesList);
            String foundItemsStr = String.join("; ", allFoundItems);
            // Use passedFilesList for matchingFilesStr to lists files that satisfied the local check
            String matchingFilesStr = passedFilesList.isEmpty() ? null : String.join(", ", passedFilesList);

            if (uniqueCondition) {
                String defaultSuccess = String.format("Property Check passed for %s files. (Mode: %s, Passed: %d/%d)", mode, matchMode, passedFileCount, totalFiles);
                return CheckResult.pass(check.getRuleId(), check.getDescription(), getCustomSuccessMessage(check, defaultSuccess, checkedFilesStr, matchingFilesStr), checkedFilesStr, matchingFilesStr, this.propertyResolutions);
            } else {
                String technicalMsg = String.format("Property Check failed for %s. (Mode: %s, Passed: %d/%d).\n• %s", 
                                mode, matchMode, passedFileCount, totalFiles, 
                                details.isEmpty() ? "Pattern mismatch" : String.join("\n• ", details));
                return CheckResult.fail(check.getRuleId(), check.getDescription(), getCustomMessage(check, technicalMsg, checkedFilesStr, foundItemsStr, matchingFilesStr), checkedFilesStr, foundItemsStr, matchingFilesStr, this.propertyResolutions);
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
