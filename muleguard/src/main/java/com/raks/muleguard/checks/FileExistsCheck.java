package com.raks.muleguard.checks;

import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Validates existence of specific files.
 */
public class FileExistsCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) check.getParams().get("filePatterns");
        String matchMode = (String) check.getParams().getOrDefault("matchMode", "ANY"); // ANY or ALL
        boolean ignoreCase = (Boolean) check.getParams().getOrDefault("ignoreCase", true);
        boolean caseSensitive = (Boolean) check.getParams().getOrDefault("caseSensitive", !ignoreCase);

        if (filePatterns == null || filePatterns.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Configuration error: 'filePatterns' is required");
        }

        long matchCount = 0;
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            matchCount = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesAnyPattern(path, filePatterns, projectRoot, caseSensitive))
                    .filter(path -> !shouldIgnorePath(projectRoot, path))
                    .count();
        } catch (Exception e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Error scanning files: " + e.getMessage());
        }

        boolean passed = "ALL".equalsIgnoreCase(matchMode) ? 
            (matchCount >= filePatterns.size()) : 
            (matchCount > 0);

        if (passed) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(), "File existence check passed.");
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), 
                "Required file(s) " + filePatterns + " not found (Mode: " + matchMode + ")");
        }
    }
}
