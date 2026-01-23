package com.raks.aegis.checks;

import com.raks.aegis.model.Check;
import com.raks.aegis.model.CheckResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
        
        boolean includeLinkedConfig = Boolean.parseBoolean(String.valueOf(params.getOrDefault("includeLinkedConfig", "false")));
        boolean isRegex = Boolean.parseBoolean(String.valueOf(params.getOrDefault("isRegex", "false")))
                          || "REGEX".equalsIgnoreCase(matchMode);
        boolean caseSensitive = Boolean.parseBoolean(String.valueOf(params.getOrDefault("caseSensitive", "true")));
        boolean wholeWord = Boolean.parseBoolean(String.valueOf(params.getOrDefault("wholeWord", "false")));
        boolean wholeFile = Boolean.parseBoolean(String.valueOf(params.getOrDefault("wholeFile", "false")));


        if (filePatterns == null || filePatterns.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Configuration error: 'filePatterns' is required");
        }
        if (tokens == null || tokens.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Configuration error: 'tokens' is required");
        }

        List<Path> matchingFiles = findFiles(projectRoot, filePatterns, includeLinkedConfig);
        List<Path> searchRoots = com.raks.aegis.util.ProjectContextHelper.getEffectiveSearchRoots(projectRoot, this.linkedConfigPath, includeLinkedConfig);

        List<String> failureDetails = new ArrayList<>();
        int passedFileCount = 0;
        int totalFiles = matchingFiles.size();
        java.util.Set<String> allFoundItems = new java.util.LinkedHashSet<>();
        java.util.Set<String> successDetails = new java.util.LinkedHashSet<>();
        List<String> checkedFilesList = new ArrayList<>();

        List<Pattern> patterns = new ArrayList<>();
        if (isRegex) {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            for (String t : tokens) patterns.add(Pattern.compile(t, flags));
        } else if (wholeWord) {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            for (String t : tokens) patterns.add(Pattern.compile("\\b" + Pattern.quote(t) + "\\b", flags));
        }

        String defaultLogic = "FORBIDDEN".equalsIgnoreCase(mode) ? "OR" : "AND";
        String logic = (String) params.getOrDefault("logic", defaultLogic);
        java.util.Set<Path> matchedPathsSet = new java.util.HashSet<>();

        for (Path file : matchingFiles) {
            boolean filePassed = true;
            String failureReason = "";
            java.util.Set<String> fileFound = new java.util.HashSet<>();
            
            // Relativization logic with source labeling
            Path currentRoot = searchRoots.stream().filter(file::startsWith).findFirst().orElse(projectRoot);
            String relativePath = currentRoot.relativize(file).toString().replace("\\", "/");
            if (currentRoot.equals(linkedConfigPath)) {
                relativePath = "[Config] " + relativePath;
            }
            checkedFilesList.add(relativePath);

            try {
                java.util.Set<String> thisFileMatchedTokens = new java.util.HashSet<>();
                String content = readFileContent(file, params);

                if (wholeFile) {
                    searchAll(content, currentRoot, tokens, caseSensitive, isRegex, wholeWord, patterns, fileFound, thisFileMatchedTokens, allFoundItems);
                } else {
                    String[] lines = content.split("\\r?\\n");
                    for (String line : lines) {
                        searchAll(line, currentRoot, tokens, caseSensitive, isRegex, wholeWord, patterns, fileFound, thisFileMatchedTokens, allFoundItems);
                    }
                }

                if ("REQUIRED".equalsIgnoreCase(mode)) {
                    if ("OR".equalsIgnoreCase(logic)) {
                        if (fileFound.isEmpty()) { filePassed = false; failureReason = "Missing any of: " + tokens; }
                    } else {
                        if (thisFileMatchedTokens.size() < tokens.size()) {
                            filePassed = false;
                            List<String> missing = new ArrayList<>(tokens);
                            missing.removeAll(thisFileMatchedTokens);
                            failureReason = "Missing: " + missing;
                        }
                    }
                } else { // FORBIDDEN
                    if ("AND".equalsIgnoreCase(logic)) {
                        if (thisFileMatchedTokens.size() == tokens.size()) { filePassed = false; failureReason = "Found all forbidden tokens"; }
                    } else {
                        if (!fileFound.isEmpty()) { filePassed = false; failureReason = "Found forbidden: " + fileFound; }
                    }
                }
                if (!fileFound.isEmpty() || !thisFileMatchedTokens.isEmpty()) {
                    matchedPathsSet.add(file);
                }
            } catch (Exception e) {
                filePassed = false;
                failureReason = "Read Error: " + e.getMessage();
            }

            if (filePassed) {
                passedFileCount++;
                successDetails.add(relativePath);
            } else {
                failureDetails.add(relativePath + " [" + failureReason + "]");
            }
        }

        String checkedFilesStr = String.join(", ", checkedFilesList);
        String foundItemsStr = allFoundItems.isEmpty() ? null : String.join(", ", allFoundItems);
        String matchingFilesStr = successDetails.isEmpty() ? null : String.join(", ", successDetails);

        boolean overallPass = evaluateMatchMode(matchMode, totalFiles, passedFileCount);

        if (overallPass) {
            String defaultSuccess = String.format("Passed %s check in %d/%d files", mode, passedFileCount, totalFiles);
            return finalizePass(check, defaultSuccess, checkedFilesStr, foundItemsStr, matchingFilesStr, matchedPathsSet);
        } else {
            String technicalMsg = String.format("Validation failed for %s. (Passed: %d/%d)\n• %s", 
                    mode, passedFileCount, totalFiles, failureDetails.isEmpty() ? "No files matched" : String.join("\n• ", failureDetails));
            return finalizeFail(check, technicalMsg, checkedFilesStr, foundItemsStr, matchingFilesStr, matchedPathsSet);
        }
    }
}
