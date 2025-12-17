package com.raks.muleguard.checks;

import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Substring check for configuration files.
 * Validates that tokens exist or do not exist as exact substrings in specified
 * files.
 * 
 * Supports:
 * - Search modes: REQUIRED (token must exist) or FORBIDDEN (token must NOT
 * exist)
 * - Configurable case sensitivity
 * - Multiple file extensions (.properties, .policy, etc.)
 * - Environment-specific file filtering
 * - Exact substring matching (no leading/trailing spaces)
 * 
 * @author Rakesh Kumar (raksjnu@gmail.com)
 */
public class MandatorySubstringCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        @SuppressWarnings("unchecked")
        List<String> fileExtensions = (List<String>) check.getParams().get("fileExtensions");
        @SuppressWarnings("unchecked")
        List<String> tokens = (List<String>) check.getParams().get("tokens");
        List<String> environments = resolveEnvironments(check);

        // Default to case-sensitive if not specified
        boolean caseSensitive = (Boolean) check.getParams().getOrDefault("caseSensitive", true);

        // Default to REQUIRED mode if not specified
        String searchMode = (String) check.getParams().getOrDefault("searchMode", "REQUIRED");

        // Validation
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
        List<String> validatedFiles = new ArrayList<>(); // Track validated files

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> matchesEnvironmentFile(path, environments, fileExtensions))
                    .filter(path -> !shouldIgnorePath(projectRoot, path)) // Filter ignored folders
                    .forEach(file -> {
                        validatedFiles.add(projectRoot.relativize(file).toString()); // Track file
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

    /**
     * Check if file matches environment and extension criteria
     */
    private boolean matchesEnvironmentFile(Path path, List<String> environments, List<String> fileExtensions) {
        String fileName = path.getFileName().toString();
        String fileBaseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";

        return environments.contains(fileBaseName) && fileExtensions.contains(extension);
    }

    /**
     * Validate tokens in file based on search mode
     * REQUIRED mode: Token must exist (fail if not found)
     * FORBIDDEN mode: Token must NOT exist (fail if found)
     */
    private void validateTokensInFile(Path file, List<String> tokens, boolean caseSensitive,
            String searchMode, Path projectRoot, List<String> failures) {
        try {
            String content = Files.readString(file);
            String[] lines = content.split("\\r?\\n");

            for (String token : tokens) {
                boolean found = false;

                // Check each line, skipping comments
                for (String line : lines) {
                    line = line.trim();

                    // Skip comments and empty lines
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                        continue;
                    }

                    // Check if token exists in this non-commented line
                    if (containsToken(line, token, caseSensitive)) {
                        found = true;
                        break;
                    }
                }

                if (searchMode.equals("REQUIRED")) {
                    // REQUIRED mode: Fail if token is NOT found
                    if (!found) {
                        failures.add(String.format(
                                "Required token '%s' not found in file: %s (case-sensitive: %s)",
                                token, projectRoot.relativize(file), caseSensitive));
                    }
                } else if (searchMode.equals("FORBIDDEN")) {
                    // FORBIDDEN mode: Fail if token IS found
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

    /**
     * Check if content contains token with configurable case sensitivity
     */
    private boolean containsToken(String content, String token, boolean caseSensitive) {
        if (caseSensitive) {
            return content.contains(token);
        } else {
            return content.toLowerCase().contains(token.toLowerCase());
        }
    }
}
