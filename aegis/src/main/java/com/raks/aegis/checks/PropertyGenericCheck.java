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

public class PropertyGenericCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        if (!isRuleApplicable(projectRoot, check)) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(), "Pre-conditions not met.");
        }

        Map<String, Object> params = getEffectiveParams(check);
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) params.get("filePatterns");
        String expectedValueRegex = (String) params.get("value");
        String mode = (String) params.getOrDefault("mode", "EXISTS");
        String operator = (String) params.getOrDefault("operator", "MATCHES"); 
        String valueType = (String) params.getOrDefault("valueType", "STRING");
        String matchMode = (String) params.getOrDefault("matchMode", "ALL_FILES");
        boolean resolveProperties = Boolean.parseBoolean(String.valueOf(params.getOrDefault("resolveProperties", "false")));

        if (filePatterns == null || filePatterns.isEmpty()) return failConfig(check, "filePatterns required");

        boolean includeLinkedConfig = Boolean.parseBoolean(String.valueOf(params.getOrDefault("includeLinkedConfig", "false")));
        List<Path> matchingFiles = findFiles(projectRoot, filePatterns, includeLinkedConfig);
        List<Path> searchRoots = com.raks.aegis.util.ProjectContextHelper.getEffectiveSearchRoots(projectRoot, this.linkedConfigPath, includeLinkedConfig);

        int passedFileCount = 0;
        int totalFiles = matchingFiles.size();
        List<String> details = new ArrayList<>();
        List<String> passedFilesList = new ArrayList<>();

        for (Path file : matchingFiles) {
            boolean filePassed = true;
            List<String> fileErrors = new ArrayList<>();
            List<String> fileSuccesses = new ArrayList<>();
            
            Path currentRoot = searchRoots.stream().filter(file::startsWith).findFirst().orElse(projectRoot);
            String relativePath = currentRoot.relativize(file).toString().replace("\\", "/");
            if (currentRoot.equals(linkedConfigPath)) { relativePath = "[Config] " + relativePath; }

            try (InputStream is = Files.newInputStream(file)) {
                Properties props = new Properties();
                if (file.toString().endsWith(".xml")) props.loadFromXML(is);
                else props.load(is);

                @SuppressWarnings("unchecked")
                Map<String, String> requiredFields = (Map<String, String>) params.get("requiredFields");
                if (requiredFields != null) {
                    for (Map.Entry<String, String> entry : requiredFields.entrySet()) {
                        String key = entry.getKey();
                        String expected = entry.getValue();
                        String actual = props.getProperty(key);
                        if (actual == null) { filePassed = false; fileErrors.add("Missing field: " + key); }
                        else {
                            if (resolveProperties) actual = resolve(actual, currentRoot, this.propertyResolutions);
                            if (!actual.equals(expected)) { filePassed = false; fileErrors.add("Mismatch at " + key); }
                            else fileSuccesses.add(key + "=" + actual);
                        }
                    }
                }
                
                // Single property check
                if (params.containsKey("property")) {
                    String key = (String) params.get("property");
                    String val = props.getProperty(key);
                    if ("EXISTS".equalsIgnoreCase(mode)) {
                        if (val == null) { filePassed = false; fileErrors.add("Missing key: " + key); }
                        else fileSuccesses.add(key + "=" + val);
                    } else if ("VALUE_MATCH".equalsIgnoreCase(mode)) {
                        if (val == null) { filePassed = false; fileErrors.add("Missing key: " + key); }
                        else {
                            if (resolveProperties) val = resolve(val, currentRoot, this.propertyResolutions);
                            if (!compareValues(val, expectedValueRegex, operator, valueType)) {
                                filePassed = false; fileErrors.add("Value mismatch for " + key);
                            } else fileSuccesses.add(key + "=" + val);
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
            String msg = String.format("Passed Property check (%d/%d files)", passedFileCount, totalFiles);
            return CheckResult.pass(check.getRuleId(), check.getDescription(), getCustomSuccessMessage(check, msg, checkedFilesStr, matchingFilesStr), checkedFilesStr, matchingFilesStr, this.propertyResolutions);
        } else {
            String msg = String.format("Property check failed. (Passed: %d/%d)\n• %s", passedFileCount, totalFiles, String.join("\n• ", details));
            return CheckResult.fail(check.getRuleId(), check.getDescription(), getCustomMessage(check, msg, checkedFilesStr, null, matchingFilesStr), checkedFilesStr, null, matchingFilesStr, this.propertyResolutions);
        }
    }

    private CheckResult failConfig(Check check, String msg) {
        return CheckResult.fail(check.getRuleId(), check.getDescription(), "Config Error: " + msg);
    }
}
