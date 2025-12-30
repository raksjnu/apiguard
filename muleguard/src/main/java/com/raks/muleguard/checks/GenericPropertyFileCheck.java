package com.raks.muleguard.checks;
import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Stream;
public class GenericPropertyFileCheck extends AbstractCheck {
    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        @SuppressWarnings("unchecked")
        List<String> propertyNames = (List<String>) check.getParams().get("propertyNames");
        @SuppressWarnings("unchecked")
        List<String> fileExtensions = (List<String>) check.getParams().get("fileExtensions");
        @SuppressWarnings("unchecked")
        List<String> environments = (List<String>) check.getParams().get("environments");
        @SuppressWarnings("unchecked")
        List<String> regexPatterns = (List<String>) check.getParams().get("regexPatterns");
        String parseMode = (String) check.getParams().getOrDefault("parseMode", "PROPERTIES_FORMAT");
        if ("REGEX_PATTERN".equalsIgnoreCase(parseMode)) {
            if (regexPatterns == null || regexPatterns.isEmpty()) {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        "Configuration error: 'regexPatterns' parameter is required for REGEX_PATTERN mode");
            }
        } else {
            if (propertyNames == null || propertyNames.isEmpty()) {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        "Configuration error: 'propertyNames' parameter is required");
            }
        }
        if (fileExtensions == null || fileExtensions.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'fileExtensions' parameter is required");
        }
        if (environments == null || environments.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'environments' parameter is required");
        }
        List<String> failures = new ArrayList<>();
        List<String> validatedFiles = new ArrayList<>(); 
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> matchesEnvironmentFile(path, environments, fileExtensions))
                    .filter(path -> !shouldIgnorePath(projectRoot, path)) 
                    .forEach(file -> {
                        validatedFiles.add(projectRoot.relativize(file).toString()); 
                        if ("REGEX_PATTERN".equalsIgnoreCase(parseMode)) {
                            validateRegexPatterns(file, regexPatterns, projectRoot, failures);
                        } else if ("PROPERTIES_FORMAT".equalsIgnoreCase(parseMode)) {
                            validatePropertiesFile(file, propertyNames, projectRoot, failures);
                        } else {
                            validateSubstringSearch(file, propertyNames, projectRoot, failures);
                        }
                    });
        } catch (IOException e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error scanning files: " + e.getMessage());
        }
        if (failures.isEmpty()) {
            String fileList = String.join("; ", validatedFiles);
            String successMessage = "REGEX_PATTERN".equalsIgnoreCase(parseMode)
                    ? "All regex patterns matched successfully"
                    : String.format("Required properties (%s) found in all relevant files",
                            String.join(", ", propertyNames));
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    successMessage + "\\nFiles validated: " + fileList);
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Validation failures:\\n• " + String.join("\\n• ", failures));
        }
    }
    private boolean matchesEnvironmentFile(Path path, List<String> environments, List<String> fileExtensions) {
        String fileName = path.getFileName().toString();
        String fileBaseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";
        return environments.contains(fileBaseName) && fileExtensions.contains(extension);
    }
    private void validateRegexPatterns(Path file, List<String> regexPatterns,
            Path projectRoot, List<String> failures) {
        try {
            String content = Files.readString(file);
            String[] lines = content.split("\\r?\\n");
            for (String regexPattern : regexPatterns) {
                boolean patternMatched = false;
                String[] parts = regexPattern.split("=", 2);
                if (parts.length != 2) {
                    failures.add(String.format(
                            "Invalid regex pattern format '%s'. Expected 'namePattern=valuePattern'",
                            regexPattern));
                    continue;
                }
                String namePattern = parts[0].trim();
                String valuePattern = parts[1].trim();
                try {
                    Pattern nameRegex = Pattern.compile(namePattern);
                    Pattern valueRegex = Pattern.compile(valuePattern);
                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                            continue;
                        }
                        int equalsIndex = line.indexOf('=');
                        if (equalsIndex > 0) {
                            String propName = line.substring(0, equalsIndex).trim();
                            String propValue = line.substring(equalsIndex + 1).trim();
                            Matcher nameMatcher = nameRegex.matcher(propName);
                            Matcher valueMatcher = valueRegex.matcher(propValue);
                            if (nameMatcher.matches() && valueMatcher.matches()) {
                                patternMatched = true;
                                break;
                            }
                        }
                    }
                    if (!patternMatched) {
                        failures.add(String.format(
                                "Pattern '%s' not matched in file: %s",
                                regexPattern, projectRoot.relativize(file)));
                    }
                } catch (Exception e) {
                    failures.add(String.format(
                            "Invalid regex pattern '%s': %s",
                            regexPattern, e.getMessage()));
                }
            }
        } catch (IOException e) {
            failures.add(String.format("Could not read file: %s (Error: %s)",
                    projectRoot.relativize(file), e.getMessage()));
        }
    }
    private void validatePropertiesFile(Path file, List<String> propertyNames,
            Path projectRoot, List<String> failures) {
        try {
            Properties props = new Properties();
            props.load(Files.newInputStream(file));
            for (String propertyName : propertyNames) {
                if (!props.containsKey(propertyName)) {
                    failures.add(String.format("Property '%s' not found in file: %s",
                            propertyName, projectRoot.relativize(file)));
                }
            }
        } catch (IOException e) {
            failures.add(String.format("Could not read file: %s (Error: %s)",
                    projectRoot.relativize(file), e.getMessage()));
        }
    }
    private void validateSubstringSearch(Path file, List<String> propertyNames,
            Path projectRoot, List<String> failures) {
        try {
            String content = Files.readString(file);
            for (String propertyName : propertyNames) {
                if (!content.contains(propertyName)) {
                    failures.add(String.format("Property '%s' not found in file: %s",
                            propertyName, projectRoot.relativize(file)));
                }
            }
        } catch (IOException e) {
            failures.add(String.format("Could not read file: %s (Error: %s)",
                    projectRoot.relativize(file), e.getMessage()));
        }
    }
}
