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
        
        // Robust parameter parsing for boolean flags
        boolean isRegex = Boolean.parseBoolean(String.valueOf(params.getOrDefault("isRegex", "false")))
                          || "REGEX".equalsIgnoreCase(matchMode);
        boolean caseSensitive = Boolean.parseBoolean(String.valueOf(params.getOrDefault("caseSensitive", "true")));
        boolean wholeWord = Boolean.parseBoolean(String.valueOf(params.getOrDefault("wholeWord", "false")));
        boolean ignoreComments = Boolean.parseBoolean(String.valueOf(params.getOrDefault("ignoreComments", "false")));

        if (filePatterns == null || filePatterns.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Configuration error: 'filePatterns' is required");
        }
        if (tokens == null || tokens.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Configuration error: 'tokens' is required");
        }

        List<String> failureDetails = new ArrayList<>();
        int passedFileCount = 0;
        int totalFiles = 0;
        java.util.Set<String> allFoundItems = new java.util.LinkedHashSet<>();
        java.util.Set<String> successDetails = new java.util.LinkedHashSet<>();
        List<String> checkedFilesList = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            List<Path> matchingFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesAnyPattern(path, filePatterns, projectRoot))
                    .filter(path -> !shouldIgnorePath(projectRoot, path))
                    .toList();

            totalFiles = matchingFiles.size();

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
                List<String> fileFound = new ArrayList<>();
                checkedFilesList.add(projectRoot.relativize(file).toString());

                try {
                    List<String> lines = Files.readAllLines(file);
                    boolean resolveProperties = Boolean.parseBoolean(String.valueOf(params.getOrDefault("resolveProperties", "false")));
                    
                    if (!caseSensitive && !isRegex && !wholeWord) {
                        // Pre-process tokens for case-insensitive non-regex search
                        for (int i = 0; i < tokens.size(); i++) {
                            tokens.set(i, tokens.get(i).toLowerCase());
                        }
                    }

                    java.util.Set<String> thisFileMatchedConfigTokens = new java.util.HashSet<>();

                    for (int lineNum = 0; lineNum < lines.size(); lineNum++) {
                        String originalLine = lines.get(lineNum);
                        String processedLine = originalLine;

                        // 1. Resolve properties into a temporary list
                        List<String> lineResolutions = new ArrayList<>();
                        if (resolveProperties) {
                            processedLine = resolve(processedLine, projectRoot, lineResolutions);
                        }

                        // 2. Handle comments
                        String contentForMatch = processedLine;
                        if (ignoreComments) {
                            contentForMatch = removeCommentsFromLine(contentForMatch, file);
                        }

                        if (!caseSensitive && !isRegex && !wholeWord) {
                            contentForMatch = contentForMatch.toLowerCase();
                        }

                        // 3. Check tokens
                        for (int i = 0; i < tokens.size(); i++) {
                            String configToken = tokens.get(i);
                            String tokenToAdd = configToken;
                            boolean match = false;

                            if (isRegex || wholeWord) {
                                java.util.regex.Matcher m = patterns.get(i).matcher(contentForMatch);
                                if (m.find()) {
                                    match = true;
                                    tokenToAdd = m.group();
                                }
                            } else {
                                if (contentForMatch.contains(configToken)) {
                                    match = true;
                                }
                            }

                            if (match) {
                                fileFound.add(tokenToAdd);
                                thisFileMatchedConfigTokens.add(configToken);
                                allFoundItems.add(tokenToAdd);

                                // SIGNAL HIT: Record property resolutions for this line
                                recordResolutions(lineResolutions);
                            }
                        }
                    }

                    if ("REQUIRED".equalsIgnoreCase(mode)) {
                        if ("OR".equalsIgnoreCase(logic)) {
                            if (fileFound.isEmpty()) {
                                filePassed = false;
                                failureReason = "Missing any of: " + tokens;
                            }
                        } else {
                            if (thisFileMatchedConfigTokens.size() < tokens.size()) {
                                filePassed = false;
                                List<String> missing = new ArrayList<>(tokens);
                                missing.removeAll(thisFileMatchedConfigTokens);
                                
                                List<String> readableMissing = missing.stream()
                                    .map(t -> cleanToken(t, isRegex))
                                    .toList();
                                    
                                failureReason = "Missing: " + readableMissing;
                            }
                        }
                    } else { // FORBIDDEN
                        if ("AND".equalsIgnoreCase(logic)) {
                            if (fileFound.size() == tokens.size()) {
                                filePassed = false;
                                failureReason = "Found all forbidden: " + fileFound;
                            }
                        } else {
                            if (!fileFound.isEmpty()) {
                                filePassed = false;
                                failureReason = "Found forbidden: " + fileFound;
                            }
                        }
                    }

                } catch (Exception e) {
                    filePassed = false;
                    failureReason = "Read/Check Error: " + e.getMessage();
                }

                if (filePassed) {
                    passedFileCount++;
                    successDetails.add(file.getFileName().toString());
                } else {
                    failureDetails.add(projectRoot.relativize(file) + " [" + failureReason + "]");
                }
            }

            String checkedFilesStr = String.join(", ", checkedFilesList);
            String foundItemsStr = allFoundItems.isEmpty() ? null : String.join(", ", allFoundItems);
            String matchingFilesStr = successDetails.isEmpty() ? null : String.join(", ", successDetails);

            boolean overallPass = evaluateMatchMode(matchMode, totalFiles, passedFileCount);

            if (overallPass) {
                String defaultSuccess = String.format("Passed %s check in %d/%d files (Mode: %s)", mode, passedFileCount, totalFiles, matchMode);
                return CheckResult.pass(check.getRuleId(), check.getDescription(), 
                        getCustomSuccessMessage(check, defaultSuccess, checkedFilesStr, foundItemsStr, matchingFilesStr), 
                        checkedFilesStr, matchingFilesStr, this.propertyResolutions);
            } else {
                String technicalMsg = String.format("Validation failed for %s. (MatchMode: %s, Passed: %d/%d).\n• %s", 
                        mode, matchMode, passedFileCount, totalFiles, failureDetails.isEmpty() ? "No files matched criteria" : String.join("\n• ", failureDetails));
                return CheckResult.fail(check.getRuleId(), check.getDescription(), 
                        getCustomMessage(check, technicalMsg, checkedFilesStr, foundItemsStr, matchingFilesStr), 
                        checkedFilesStr, foundItemsStr, matchingFilesStr, this.propertyResolutions);
            }

        } catch (Exception e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Scan Error: " + e.getMessage());
        }
    }

    private String cleanToken(String token, boolean isRegex) {
        if (!isRegex) return token;
        return token.replace("\\b", "");
    }

    private String removeCommentsFromLine(String line, Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".xml") || fileName.endsWith(".html") || fileName.endsWith(".mule")) {
            return line.replaceAll("(?s)<!--.*?-->", "");
        } else if (fileName.endsWith(".java") || fileName.endsWith(".js") || fileName.endsWith(".c") || fileName.endsWith(".cpp")) {
            String temp = line.replaceAll("(?s)/\\*.*?\\*/", "");
            return temp.replaceAll("//.*", "");
        } else if (fileName.endsWith(".properties") || fileName.endsWith(".yaml") || fileName.endsWith(".yml") || fileName.endsWith(".sh")) {
            return line.replaceAll("^\\s*#.*", "");
        }
        return line;
    }
}
