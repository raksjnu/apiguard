package com.raks.muleguard.checks;
import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;
import com.raks.muleguard.model.PropertyConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
public class OptionalPropertyValueCheck extends AbstractCheck {
    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        @SuppressWarnings("unchecked")
        List<String> fileExtensions = (List<String>) check.getParams().get("fileExtensions");
        List<String> environments = resolveEnvironments(check);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> propertiesRaw = (List<Map<String, Object>>) check.getParams().get("properties");
        String delimiter = (String) check.getParams().getOrDefault("delimiter", "=");
        boolean caseSensitiveNames = (Boolean) check.getParams().getOrDefault("caseSensitiveNames", true);
        boolean caseSensitiveValues = (Boolean) check.getParams().getOrDefault("caseSensitiveValues", true);
        if (fileExtensions == null || fileExtensions.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'fileExtensions' parameter is required");
        }
        if (propertiesRaw == null || propertiesRaw.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'properties' parameter is required");
        }
        if (environments == null || environments.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'environments' parameter is required");
        }
        List<PropertyConfig> properties = convertToPropertyConfigs(propertiesRaw);
        List<String> failures = new ArrayList<>();
        List<String> validatedFiles = new ArrayList<>(); 
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> matchesEnvironmentFile(path, environments, fileExtensions))
                    .filter(path -> !shouldIgnorePath(projectRoot, path)) 
                    .forEach(file -> {
                        validatedFiles.add(projectRoot.relativize(file).toString()); 
                        validateOptionalPropertiesInFile(file, properties, delimiter,
                                caseSensitiveNames, caseSensitiveValues, projectRoot, failures);
                    });
        } catch (IOException e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error scanning files: " + e.getMessage());
        }
        if (failures.isEmpty()) {
            String fileList = String.join("; ", validatedFiles);
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    "All optional properties (if present) have correct values\nFiles validated: " + fileList);
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Validation failures:\n• " + String.join("\n• ", failures));
        }
    }
    @SuppressWarnings("unchecked")
    private List<PropertyConfig> convertToPropertyConfigs(List<Map<String, Object>> propertiesRaw) {
        List<PropertyConfig> configs = new ArrayList<>();
        for (Map<String, Object> propMap : propertiesRaw) {
            PropertyConfig config = new PropertyConfig();
            config.setName((String) propMap.get("name"));
            config.setValues((List<String>) propMap.get("values"));
            config.setCaseSensitiveName((Boolean) propMap.get("caseSensitiveName"));
            config.setCaseSensitiveValue((Boolean) propMap.get("caseSensitiveValue"));
            configs.add(config);
        }
        return configs;
    }
    private boolean matchesEnvironmentFile(Path path, List<String> environments, List<String> fileExtensions) {
        String fileName = path.getFileName().toString();
        String fileBaseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";
        return environments.contains(fileBaseName) && fileExtensions.contains(extension);
    }
    private void validateOptionalPropertiesInFile(Path file, List<PropertyConfig> properties, String delimiter,
            boolean globalCaseSensitiveNames, boolean globalCaseSensitiveValues,
            Path projectRoot, List<String> failures) {
        try {
            String content = Files.readString(file);
            String[] lines = content.split("\\r?\\n");
            for (PropertyConfig propConfig : properties) {
                boolean propertyFound = false;
                boolean valueMatched = false;
                boolean caseSensitiveName = propConfig.getCaseSensitiveName() != null
                        ? propConfig.getCaseSensitiveName()
                        : globalCaseSensitiveNames;
                boolean caseSensitiveValue = propConfig.getCaseSensitiveValue() != null
                        ? propConfig.getCaseSensitiveValue()
                        : globalCaseSensitiveValues;
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                        continue;
                    }
                    int delimiterIndex = line.indexOf(delimiter);
                    if (delimiterIndex > 0) {
                        String propName = line.substring(0, delimiterIndex).trim();
                        String propValue = line.substring(delimiterIndex + 1).trim();
                        if (matches(propName, propConfig.getName(), caseSensitiveName)) {
                            propertyFound = true;
                            for (String expectedValue : propConfig.getValues()) {
                                if (matches(propValue, expectedValue, caseSensitiveValue)) {
                                    valueMatched = true;
                                    break;
                                }
                            }
                            if (valueMatched) {
                                break; 
                            }
                        }
                    }
                }
                if (propertyFound && !valueMatched) {
                    failures.add(String.format(
                            "Optional property '%s' found but value does not match expected values %s in file: %s",
                            propConfig.getName(), propConfig.getValues(), projectRoot.relativize(file)));
                }
            }
        } catch (IOException e) {
            failures.add(String.format("Could not read file: %s (Error: %s)",
                    projectRoot.relativize(file), e.getMessage()));
        }
    }
    private boolean matches(String actual, String expected, boolean caseSensitive) {
        if (caseSensitive) {
            return actual.equals(expected);
        } else {
            return actual.equalsIgnoreCase(expected);
        }
    }
}
