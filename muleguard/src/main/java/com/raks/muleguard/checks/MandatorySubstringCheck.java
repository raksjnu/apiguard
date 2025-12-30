package com.raks.muleguard.checks;
import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
public class MandatorySubstringCheck extends AbstractCheck {
    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        @SuppressWarnings("unchecked")
        List<String> fileExtensions = (List<String>) check.getParams().get("fileExtensions");
        @SuppressWarnings("unchecked")
        List<String> tokens = (List<String>) check.getParams().get("tokens");
        List<String> environments = resolveEnvironments(check);
        boolean caseSensitive = (Boolean) check.getParams().getOrDefault("caseSensitive", true);
        String searchMode = (String) check.getParams().getOrDefault("searchMode", "REQUIRED");
        if (fileExtensions == null || fileExtensions.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'fileExtensions' parameter is required");
        }
        if (tokens == null || tokens.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'tokens' parameter is required");
        }
        if (environments == null || environments.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'environments' parameter is required");
        }
        if (!searchMode.equals("REQUIRED") && !searchMode.equals("FORBIDDEN")) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'searchMode' must be either 'REQUIRED' or 'FORBIDDEN'");
        }
        List<String> failures = new ArrayList<>();
        List<String> validatedFiles = new ArrayList<>(); 
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> matchesEnvironmentFile(path, environments, fileExtensions))
                    .filter(path -> !shouldIgnorePath(projectRoot, path)) 
                    .forEach(file -> {
                        validatedFiles.add(projectRoot.relativize(file).toString()); 
                        validateTokensInFile(file, tokens, caseSensitive, searchMode, projectRoot, failures);
                    });
        } catch (IOException e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error scanning files: " + e.getMessage());
        }
        if (failures.isEmpty()) {
            String fileList = String.join("; ", validatedFiles);
            String message = searchMode.equals("REQUIRED")
                    ? String.format("All required tokens found in environment files (case-sensitive: %s)",
                            caseSensitive)
                    : String.format("No forbidden tokens found in environment files (case-sensitive: %s)",
                            caseSensitive);
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    message + "\nFiles validated: " + fileList);
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Validation failures:\n• " + String.join("\n• ", failures));
        }
    }
    private boolean matchesEnvironmentFile(Path path, List<String> environments, List<String> fileExtensions) {
        String fileName = path.getFileName().toString();
        String fileBaseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";
        return environments.contains(fileBaseName) && fileExtensions.contains(extension);
    }
    private void validateTokensInFile(Path file, List<String> tokens, boolean caseSensitive,
            String searchMode, Path projectRoot, List<String> failures) {
        try {
            String content = Files.readString(file);
            String[] lines = content.split("\\r?\\n");
            for (String token : tokens) {
                boolean found = false;
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                        continue;
                    }
                    if (containsToken(line, token, caseSensitive)) {
                        found = true;
                        break;
                    }
                }
                if (searchMode.equals("REQUIRED")) {
                    if (!found) {
                        failures.add(String.format(
                                "Required token '%s' not found in file: %s (case-sensitive: %s)",
                                token, projectRoot.relativize(file), caseSensitive));
                    }
                } else if (searchMode.equals("FORBIDDEN")) {
                    if (found) {
                        failures.add(String.format(
                                "Forbidden token '%s' found in file: %s (case-sensitive: %s)",
                                token, projectRoot.relativize(file), caseSensitive));
                    }
                }
            }
        } catch (IOException e) {
            failures.add(String.format("Could not read file: %s (Error: %s)",
                    projectRoot.relativize(file), e.getMessage()));
        }
    }
    private boolean containsToken(String content, String token, boolean caseSensitive) {
        if (caseSensitive) {
            return content.contains(token);
        } else {
            return content.toLowerCase().contains(token.toLowerCase());
        }
    }
}
