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
public class GenericTokenSearchRequiredCheck extends AbstractCheck {
    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) check.getParams().get("filePatterns");
        @SuppressWarnings("unchecked")
        List<String> excludePatterns = (List<String>) check.getParams().getOrDefault("excludePatterns",
                new ArrayList<>());
        @SuppressWarnings("unchecked")
        List<String> tokens = (List<String>) check.getParams().get("tokens");
        Boolean requireAll = (Boolean) check.getParams().getOrDefault("requireAll", true);
        Boolean caseSensitive = (Boolean) check.getParams().getOrDefault("caseSensitive", true);
        String matchMode = (String) check.getParams().getOrDefault("matchMode", "SUBSTRING");
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
        List<String> successes = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            matchingFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesAnyPattern(path, filePatterns, projectRoot))
                    .filter(path -> !matchesAnyPattern(path, excludePatterns, projectRoot))
                    .filter(path -> !shouldIgnorePath(projectRoot, path)) 
                    .toList();
            if (matchingFiles.isEmpty()) {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        "No files found matching patterns: " + filePatterns);
            }
            for (Path file : matchingFiles) {
                validateTokensInFile(file, tokens, matchMode, caseSensitive, requireAll,
                        projectRoot, failures, successes);
            }
        } catch (IOException e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error scanning files: " + e.getMessage());
        }
        if (requireAll) {
            if (failures.isEmpty()) {
                String fileList = matchingFiles.stream()
                        .map(projectRoot::relativize)
                        .map(Path::toString)
                        .collect(java.util.stream.Collectors.joining("; "));
                return CheckResult.pass(check.getRuleId(), check.getDescription(),
                        "All required tokens found\nFiles validated: " + fileList);
            } else {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        "Token validation failures:\n• " + String.join("\n• ", failures));
            }
        } else {
            if (!successes.isEmpty()) {
                return CheckResult.pass(check.getRuleId(), check.getDescription(),
                        "At least one required token found");
            } else {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        "No tokens found:\n• " + String.join("\n• ", failures));
            }
        }
    }
    private void validateTokensInFile(Path file, List<String> tokens, String matchMode,
            boolean caseSensitive, boolean requireAll,
            Path projectRoot, List<String> failures, List<String> successes) {
        try {
            String content = Files.readString(file);
            for (String token : tokens) {
                if (containsToken(content, token, matchMode, caseSensitive)) {
                    successes.add(file.getFileName().toString() + " has required token: " + token);
                } else {
                    failures.add(file.getFileName().toString() + " is missing required token: " + token);
                }
            }
        } catch (IOException e) {
            failures.add("Error reading file " + file.getFileName().toString() + ": " + e.getMessage());
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
    private boolean matchesAnyPattern(Path path, List<String> patterns, Path projectRoot) {
        if (patterns.isEmpty())
            return false;
        String relativePath = projectRoot.relativize(path).toString().replace("\\", "/");
        for (String pattern : patterns) {
            if (matchesPattern(relativePath, pattern)) {
                return true;
            }
        }
        return false;
    }
    private boolean matchesPattern(String path, String pattern) {
        String regex = pattern
                .replace(".", "\\.")
                .replace("**/", ".*")
                .replace("**", ".*")
                .replace("*", "[^/]*")
                .replace("?", ".");
        return path.matches(regex);
    }
}
