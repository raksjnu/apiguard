package com.raks.muleguard.checks;

import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Generic token search check that consolidates multiple token-based validation
 * rules.
 * 
 * Replaces:
 * - RULE_000_GenericCodeTokenCheck
 * - RULE_011_DLPReferenceCheck
 * - RULE_014_tobase64TokenCheck
 * - RULE_100_GenericConfigTokenCheck
 * 
 * Supports multiple search modes:
 * - FORBIDDEN: Fail if tokens are found (default)
 * - REQUIRED: Fail if tokens are NOT found
 * 
 * Supports multiple match modes:
 * - SUBSTRING: Simple string contains check (default, fastest)
 * - REGEX: Regular expression matching (more flexible)
 * - ELEMENT_ATTRIBUTE: Search within XML elements (for XML-specific checks)
 * 
 * @author Rakesh Kumar (raksjnu@gmail.com)
 */
public class GenericTokenSearchCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) check.getParams().get("filePatterns");
        @SuppressWarnings("unchecked")
        List<String> tokens = (List<String>) check.getParams().get("tokens");

        // Optional: environment filtering (for config files)
        List<String> environments = null;
        Object envsParam = check.getParams().get("environments");
        if (envsParam != null) {
            environments = resolveEnvironments(check);
        }

        // Default to FORBIDDEN mode if not specified
        String searchMode = (String) check.getParams().getOrDefault("searchMode", "FORBIDDEN");

        // Default to SUBSTRING mode if not specified
        String matchMode = (String) check.getParams().getOrDefault("matchMode", "SUBSTRING");

        // Optional: element name for XML-specific searches
        String elementName = (String) check.getParams().get("elementName");

        if (filePatterns == null || filePatterns.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'filePatterns' parameter is required");
        }

        if (tokens == null || tokens.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'tokens' parameter is required");
        }

        File searchDir = projectRoot.toFile();
        if (!searchDir.exists() || !searchDir.isDirectory()) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    "Project directory not found: " + projectRoot);
        }

        // Create file filter based on patterns and optional environment filtering
        final List<String> finalEnvironments = environments;
        IOFileFilter fileFilter = new IOFileFilter() {
            @Override
            public boolean accept(File file) {
                String normalizedPath = FilenameUtils.separatorsToUnix(file.getAbsolutePath());

                // Check file pattern match
                boolean patternMatches = false;
                for (String pattern : filePatterns) {
                    if (FilenameUtils.wildcardMatch(normalizedPath, "**/" + pattern)) {
                        patternMatches = true;
                        break;
                    }
                }

                if (!patternMatches) {
                    return false;
                }

                // If environments are specified, check if file matches environment
                if (finalEnvironments != null && !finalEnvironments.isEmpty()) {
                    String fileName = file.getName();
                    String fileBaseName = fileName.contains(".")
                            ? fileName.substring(0, fileName.lastIndexOf('.'))
                            : fileName;

                    return finalEnvironments.contains(fileBaseName);
                }

                return true;
            }

            @Override
            public boolean accept(File dir, String name) {
                return accept(new File(dir, name));
            }
        };

        // Find all matching files
        Collection<File> files = FileUtils.listFiles(searchDir, fileFilter, TrueFileFilter.INSTANCE);

        if (files.isEmpty()) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    "No files found matching patterns: " + String.join(", ", filePatterns));
        }

        // Search for tokens in files
        boolean tokenFound = false;
        String foundToken = null;
        String foundInFile = null;

        for (File file : files) {
            try {
                String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);

                for (String token : tokens) {
                    boolean matches = false;

                    switch (matchMode.toUpperCase()) {
                        case "REGEX":
                            matches = matchesRegex(content, token);
                            break;

                        case "ELEMENT_ATTRIBUTE":
                            if (elementName != null) {
                                matches = matchesInElement(content, elementName, token);
                            } else {
                                matches = content.contains(token);
                            }
                            break;

                        case "SUBSTRING":
                        default:
                            matches = content.contains(token);
                            break;
                    }

                    if (matches) {
                        tokenFound = true;
                        foundToken = token;
                        foundInFile = projectRoot.relativize(file.toPath()).toString();
                        break;
                    }
                }

                if (tokenFound) {
                    break; // Stop searching once we find a match
                }

            } catch (IOException e) {
                // Log error but continue processing other files
                // In production, consider using a logger instead of silent failure
            }
        }

        // Return result based on search mode
        if ("REQUIRED".equalsIgnoreCase(searchMode)) {
            // REQUIRED mode: tokens MUST be present
            if (!tokenFound) {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        String.format("Required token(s) not found in files matching: %s",
                                String.join(", ", filePatterns)));
            }
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    String.format("Required token(s) found: '%s'", foundToken));

        } else {
            // FORBIDDEN mode (default): tokens must NOT be present
            if (tokenFound) {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        String.format("Forbidden token '%s' found in file: %s", foundToken, foundInFile));
            }
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    "No forbidden tokens found");
        }
    }

    /**
     * Check if content matches a regular expression pattern
     */
    private boolean matchesRegex(String content, String regexPattern) {
        try {
            Pattern pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(content);
            return matcher.find();
        } catch (Exception e) {
            // Invalid regex - fall back to substring match
            return content.contains(regexPattern);
        }
    }

    /**
     * Check if token appears within a specific XML element
     */
    private boolean matchesInElement(String content, String elementName, String token) {
        // Build regex to find token within element
        // Pattern: <elementName...>...token...</elementName>
        String regex = String.format("(?s)<(?:[a-zA-Z0-9-]+:)?%s\\b[^>]*?%s[^>]*?>",
                Pattern.quote(elementName), Pattern.quote(token));

        try {
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(content);
            return matcher.find();
        } catch (Exception e) {
            // Fall back to simple substring search
            return content.contains(token);
        }
    }
}
