package com.raks.aegis.checks;

import com.raks.aegis.model.Check;
import com.raks.aegis.model.CheckResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class TokenSearchCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        if (!isRuleApplicable(projectRoot, check)) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(), "Rule skipped: Pre-conditions not met.");
        }

        Map<String, Object> params = getEffectiveParams(check);
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) params.get("filePatterns");
        @SuppressWarnings("unchecked")
        List<String> tokens = (List<String>) params.get("tokens");

        String mode = (String) params.getOrDefault("mode", "FORBIDDEN"); 
        String matchMode = (String) params.getOrDefault("matchMode", "ALL_FILES");
        boolean isRegex = (Boolean) params.getOrDefault("isRegex", false);
        boolean caseSensitive = (Boolean) params.getOrDefault("caseSensitive", true);
        boolean wholeWord = (Boolean) params.getOrDefault("wholeWord", false);

        if (filePatterns == null || filePatterns.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Configuration error: 'filePatterns' is required");
        }
        if (tokens == null || tokens.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Configuration error: 'tokens' is required");
        }

        List<String> failures = new ArrayList<>();
        List<Path> matchingFiles = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            matchingFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesAnyPattern(path, filePatterns, projectRoot))
                    .filter(path -> !shouldIgnorePath(projectRoot, path))
                    .toList();

            if (matchingFiles.isEmpty()) {
                if ("REQUIRED".equalsIgnoreCase(mode) && ("ALL_FILES".equalsIgnoreCase(matchMode) || "AT_LEAST_N".equalsIgnoreCase(matchMode))) {
                    return CheckResult.fail(check.getRuleId(), check.getDescription(), "No matching files found for pattern: " + filePatterns);
                }

            }

            List<Pattern> patterns = new ArrayList<>();
            if (isRegex) {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                for (String t : tokens) {
                    patterns.add(Pattern.compile(t, flags));
                }
            } else if (wholeWord) {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                for (String t : tokens) {
                    patterns.add(Pattern.compile("\\b" + Pattern.quote(t) + "\\b", flags));
                }
            }

            for (Path file : matchingFiles) {
                String content = Files.readString(file);

                Object debugProp = params.getOrDefault("resolveProperties", "false");
                boolean resolveProperties = Boolean.parseBoolean(String.valueOf(debugProp));
                if (resolveProperties) {

                    content = com.raks.aegis.util.PropertyResolver.resolve(content, projectRoot);

                }

                if (!caseSensitive && !isRegex && !wholeWord) {
                    content = content.toLowerCase();
                }

                boolean foundInThisFile = false;
                List<String> foundTokens = new ArrayList<>();

                for (int i = 0; i < tokens.size(); i++) {
                    String tokenStr = tokens.get(i);
                    boolean match = false;

                    if (isRegex || wholeWord) {
                        match = patterns.get(i).matcher(content).find();
                    } else {
                        String target = caseSensitive ? tokenStr : tokenStr.toLowerCase();
                        match = content.contains(target);
                    }

                    if (match) {
                        foundTokens.add(tokenStr);
                        foundInThisFile = true;
                    }
                }

                if ("REQUIRED".equalsIgnoreCase(mode)) {

                    if (foundTokens.size() < tokens.size()) {

                        List<String> missing = new ArrayList<>(tokens);
                        missing.removeAll(foundTokens);
                        failures.add("Missing required tokens " + missing + " in " + projectRoot.relativize(file));
                    }
                } else {

                    if (foundInThisFile) {
                        failures.add("Found forbidden tokens " + foundTokens + " in " + projectRoot.relativize(file));
                    }
                }
            }

        } catch (Exception e) {
             return CheckResult.fail(check.getRuleId(), check.getDescription(), "Error reading files: " + e.getMessage());
        }

        return executeWithMatchMode(projectRoot, check, filePatterns, tokens, mode, matchMode, isRegex, caseSensitive);
    }

    private CheckResult executeWithMatchMode(Path projectRoot, Check check, List<String> filePatterns, List<String> tokens, String mode, String matchMode, boolean isRegex, boolean caseSensitive) {
        List<String> failureDetails = new ArrayList<>();
        int passedFileCount = 0;
        int totalFiles = 0;
        java.util.Set<String> allFoundItems = new java.util.HashSet<>();

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            List<Path> matchingFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesAnyPattern(path, filePatterns, projectRoot))
                    .filter(path -> !shouldIgnorePath(projectRoot, path))
                    .toList();

            totalFiles = matchingFiles.size();
            Map<String, Object> params = getEffectiveParams(check);
            boolean wholeWord = (Boolean) params.getOrDefault("wholeWord", false);

            List<Pattern> patterns = new ArrayList<>();
            if (isRegex) {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                for (String t : tokens) {
                    patterns.add(Pattern.compile(t, flags));
                }
            } else if (wholeWord) {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                for (String t : tokens) {
                     patterns.add(Pattern.compile("\\b" + Pattern.quote(t) + "\\b", flags));
                }
            }

        String defaultLogic = "FORBIDDEN".equalsIgnoreCase(mode) ? "OR" : "AND";
        String logic = (String) params.getOrDefault("logic", defaultLogic);

            for (Path file : matchingFiles) {
                boolean filePassed = true;
                String failureReason = "";

                try {
                    String content = Files.readString(file);

                    Object debugProp = params.getOrDefault("resolveProperties", "false");
                    boolean resolveProperties = Boolean.parseBoolean(String.valueOf(debugProp));
                    if (resolveProperties) {
                        content = com.raks.aegis.util.PropertyResolver.resolve(content, projectRoot);
                    }

                    if (!caseSensitive && !isRegex && !wholeWord) content = content.toLowerCase();

                    List<String> found = new ArrayList<>();
                    for (int i=0; i<tokens.size(); i++) {
                        String tokenToAdd = tokens.get(i);
                        boolean match = false;

                        if (isRegex || wholeWord) {
                             java.util.regex.Matcher m = patterns.get(i).matcher(content);
                             if (m.find()) {
                                 match = true;
                                 tokenToAdd = m.group(); 
                             }
                        } else {
                            String t = caseSensitive ? tokens.get(i) : tokens.get(i).toLowerCase();
                            if (content.contains(t)) {
                                match = true;

                                tokenToAdd = tokens.get(i); 
                            }
                        }

                        if (match) {
                            found.add(tokenToAdd);
                        }
                    }

                    if ("REQUIRED".equalsIgnoreCase(mode)) {
                        if ("OR".equalsIgnoreCase(logic)) {

                            if (found.isEmpty()) {
                                filePassed = false;
                                failureReason = "Missing any of: " + tokens;
                            }
                        } else {

                            if (found.size() < tokens.size()) {
                                filePassed = false;
                                List<String> missing = new ArrayList<>(tokens);
                                missing.removeAll(found);
                                failureReason = "Missing: " + missing;
                            }
                        }
                    } else {

                        if ("AND".equalsIgnoreCase(logic)) {

                            if (found.size() == tokens.size()) {
                                filePassed = false;
                                failureReason = "Found all forbidden: " + found;
                                allFoundItems.addAll(found);
                            }
                        } else {

                            if (!found.isEmpty()) {
                                filePassed = false;
                                failureReason = "Found: " + found;
                                allFoundItems.addAll(found);
                            }
                        }
                    }

                } catch (Exception e) {
                    filePassed = false;
                    failureReason = "Read Error: " + e.toString();
                }

                if (filePassed) {
                    passedFileCount++;
                } else {
                    failureDetails.add(projectRoot.relativize(file) + " [" + failureReason + "]");
                }
            }

            String checkedFilesStr = matchingFiles.stream()
                .map(p -> projectRoot.relativize(p).toString())
                .collect(java.util.stream.Collectors.joining(", "));

            String foundItemsStr = allFoundItems.isEmpty() ? null : String.join(", ", allFoundItems);

            boolean overallPass = evaluateMatchMode(matchMode, totalFiles, passedFileCount);

            if (overallPass) {
                String defaultSuccess = String.format("Passed %s check in %d/%d files (Mode: %s)", mode, passedFileCount, totalFiles, matchMode);
                return CheckResult.pass(check.getRuleId(), check.getDescription(), getCustomSuccessMessage(check, defaultSuccess, checkedFilesStr), checkedFilesStr);
            } else {
                 String technicalMsg = String.format("Validation failed for %s. (MatchMode: %s, Passed: %d/%d). Details:\n• %s", 
                        mode, matchMode, passedFileCount, totalFiles, String.join("\n• ", failureDetails));
                 return CheckResult.fail(check.getRuleId(), check.getDescription(), getCustomMessage(check, technicalMsg, checkedFilesStr, foundItemsStr), checkedFilesStr, foundItemsStr);
            }

        } catch (Exception e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Scan Error: " + e.getMessage());
        }
    }
}
