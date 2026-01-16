package com.raks.muleguard.checks;

import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class GenericTokenSearchForbiddenCheck extends AbstractCheck {
    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) check.getParams().get("filePatterns");
        @SuppressWarnings("unchecked")
        List<String> excludePatterns = (List<String>) check.getParams().getOrDefault("excludePatterns",
                new ArrayList<>());
        @SuppressWarnings("unchecked")
        List<String> tokens = (List<String>) check.getParams().get("tokens");
        
        String matchMode = (String) check.getParams().getOrDefault("matchMode", "SUBSTRING");
        Boolean caseSensitive = (Boolean) check.getParams().getOrDefault("caseSensitive", true);
        
        boolean anyFileMode = "ANY_FILE".equalsIgnoreCase(matchMode);
        if (anyFileMode) {
            matchMode = "SUBSTRING"; // Reset content match mode to default
        }

        if (filePatterns == null || filePatterns.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'filePatterns' parameter is required");
        }
        if (tokens == null || tokens.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'tokens' parameter is required");
        }
        
        List<String> failures = new ArrayList<>();
        List<Path> matchingFiles = new ArrayList<>();
        int passedFiles = 0;

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            matchingFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesAnyPattern(path, filePatterns, projectRoot))
                    .filter(path -> !matchesAnyPattern(path, excludePatterns, projectRoot))
                    .filter(path -> !shouldIgnorePath(projectRoot, path)) 
                    .toList();
                    
            if (matchingFiles.isEmpty()) {
                return CheckResult.pass(check.getRuleId(), check.getDescription(),
                        "No files found matching patterns (nothing to validate)");
            }
            
            for (Path file : matchingFiles) {
                List<String> fileFailures = new ArrayList<>();
                validateForbiddenTokens(file, tokens, matchMode, caseSensitive, projectRoot, fileFailures);
                
                if (fileFailures.isEmpty()) {
                    passedFiles++;
                } else {
                    failures.addAll(fileFailures);
                }
            }
            
        } catch (IOException e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error scanning files: " + e.getMessage());
        }
        
        if (anyFileMode) {
            if (passedFiles > 0) {
                return CheckResult.pass(check.getRuleId(), check.getDescription(),
                        "At least one file is free of forbidden tokens (" + passedFiles + "/" + matchingFiles.size() + " files passed)");
            } else {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        "Forbidden tokens found in EVERY matching file (" + matchingFiles.size() + " files checked)");
            }
        } else {
            if (failures.isEmpty()) {
                String fileList = matchingFiles.stream()
                        .map(projectRoot::relativize)
                        .map(Path::toString)
                        .collect(java.util.stream.Collectors.joining("; "));
                return CheckResult.pass(check.getRuleId(), check.getDescription(),
                        "No forbidden tokens found\nFiles validated: " + fileList);
            } else {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        "Forbidden tokens found:\n• " + String.join("\n• ", failures));
            }
        }
    }

    private void validateForbiddenTokens(Path file, List<String> tokens, String matchMode,
            boolean caseSensitive, Path projectRoot, List<String> failures) {
        try {
            String content = Files.readString(file);
            for (String token : tokens) {
                if (containsToken(content, token, matchMode, caseSensitive)) {
                    failures.add("Forbidden token '" + token + "' found in file: " + projectRoot.relativize(file));
                }
            }
        } catch (IOException e) {
            failures.add("Error reading file " + projectRoot.relativize(file) + ": " + e.getMessage());
        }
    }

    private boolean containsToken(String content, String token, String matchMode, boolean caseSensitive) {
        if ("REGEX".equalsIgnoreCase(matchMode)) {
            try {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.MULTILINE;
                Pattern pattern = Pattern.compile(token, flags);
                return pattern.matcher(content).find();
            } catch (Exception e) {
                return containsSubstring(content, token, caseSensitive);
            }
        } else {
            return containsSubstring(content, token, caseSensitive);
        }
    }

    private boolean containsSubstring(String content, String token, boolean caseSensitive) {
        if (caseSensitive) {
            return content.contains(token);
        } else {
            return content.toLowerCase().contains(token.toLowerCase());
        }
    }
}
