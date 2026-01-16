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

/**
 * Universal Token Search Check.
 * Replaces: GenericTokenSearchCheck, GenericTokenSearchRequiredCheck, GenericTokenSearchForbiddenCheck, MandatorySubstringCheck.
 * Parameters:
 *  - filePatterns (List<String>): Target files.
 *  - tokens (List<String>): Strings/Regex to search for.
 *  - mode (String): REQUIRED (must exist) or FORBIDDEN (must not exist).
 *  - matchMode (String): ALL_FILES, ANY_FILE, NONE_OF_FILES, etc.
 *  - isRegex (boolean): Treat tokens as Regex? Default false.
 *  - caseSensitive (boolean): Default true.
 */
public class TokenSearchCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        if (!isRuleApplicable(projectRoot, check)) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(), "Rule skipped: Pre-conditions not met.");
        }

        Map<String, Object> params = check.getParams();
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) params.get("filePatterns");
        @SuppressWarnings("unchecked")
        List<String> tokens = (List<String>) params.get("tokens");
        
        String mode = (String) params.getOrDefault("mode", "FORBIDDEN"); // Default to FORBIDDEN for backward compatibility with simple search
        String matchMode = (String) params.getOrDefault("matchMode", "ALL_FILES");
        boolean isRegex = (Boolean) params.getOrDefault("isRegex", false);
        boolean caseSensitive = (Boolean) params.getOrDefault("caseSensitive", true);

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
            
            // Legacy handling for "files must exist" if mode is REQUIRED
            if (matchingFiles.isEmpty()) {
                if ("REQUIRED".equalsIgnoreCase(mode) && ("ALL_FILES".equalsIgnoreCase(matchMode) || "AT_LEAST_N".equalsIgnoreCase(matchMode))) {
                    return CheckResult.fail(check.getRuleId(), check.getDescription(), "No matching files found for pattern: " + filePatterns);
                }
                 // If forbidden, 0 files is a pass.
            }

            // Pre-compile regexes if needed
            List<Pattern> patterns = new ArrayList<>();
            if (isRegex) {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                for (String t : tokens) {
                    patterns.add(Pattern.compile(t, flags));
                }
            }

            // Scan files
            for (Path file : matchingFiles) {
                String content = Files.readString(file);
                if (!caseSensitive && !isRegex) {
                    content = content.toLowerCase();
                }

                boolean foundInThisFile = false;
                List<String> foundTokens = new ArrayList<>();

                for (int i = 0; i < tokens.size(); i++) {
                    String tokenStr = tokens.get(i);
                    boolean match = false;
                    
                    if (isRegex) {
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

                // Evaluate per file based on Mode
                if ("REQUIRED".equalsIgnoreCase(mode)) {
                    // In REQUIRED mode, usually ALL tokens must exist? Or ANY? 
                    // Let's assume ALL tokens in the list must exist in the file for it to "pass" that file.
                    // Or is it "At least one"? 
                    // Legacy GenericTokenSearchRequiredCheck iterates tokens. If any missing update result?
                    // Typically 'tokens' list implies "Any of these is bad" (Forbidden) or "All of these needed" (Required).
                    // Let's assume if multiple tokens are provided in REQUIRED, ALL must be present.
                    if (foundTokens.size() < tokens.size()) {
                        // Missing some tokens
                        List<String> missing = new ArrayList<>(tokens);
                        missing.removeAll(foundTokens);
                        failures.add("Missing required tokens " + missing + " in " + projectRoot.relativize(file));
                    }
                } else {
                    // FORBIDDEN
                    if (foundInThisFile) {
                        failures.add("Found forbidden tokens " + foundTokens + " in " + projectRoot.relativize(file));
                    }
                }
            }
            
            // Standard Match Mode Evaluation is Tricky here because "failures" list might differ based on mode.
            // If REQUIRED: failures = files that were missing tokens.
            // If FORBIDDEN: failures = files that had tokens.
            
            // To use evaluateMatchMode, we need count of "Passing Files".
            // filePassed = !failures.contains(file related error) ?
            
            // Let's re-calculate pass count.
            int passedFilesCount = matchingFiles.size(); // Start with all passing
            // But we didn't track failures per file cleanly above, just added strings.
            // Let's refine the loop below.
            
        } catch (Exception e) {
             return CheckResult.fail(check.getRuleId(), check.getDescription(), "Error reading files: " + e.getMessage());
        }
        
        // Re-implementing logic with evaluateMatchMode in mind
        return executeWithMatchMode(projectRoot, check, filePatterns, tokens, mode, matchMode, isRegex, caseSensitive);
    }
    
    private CheckResult executeWithMatchMode(Path projectRoot, Check check, List<String> filePatterns, List<String> tokens, String mode, String matchMode, boolean isRegex, boolean caseSensitive) {
        List<String> failureDetails = new ArrayList<>();
        int passedFileCount = 0;
        int totalFiles = 0;
        
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
            }

        @SuppressWarnings("unchecked")
        Map<String, Object> params = check.getParams();
        String logic = (String) params.getOrDefault("logic", "AND");

        // ... existing setup ...

            for (Path file : matchingFiles) {
                boolean filePassed = true;
                String failureReason = "";
                
                try {
                    String content = Files.readString(file);
                    if (!caseSensitive && !isRegex) content = content.toLowerCase();
                    
                    List<String> found = new ArrayList<>();
                    for (int i=0; i<tokens.size(); i++) {
                        boolean match = false;
                        if (isRegex) {
                             match = patterns.get(i).matcher(content).find();
                        } else {
                            String t = caseSensitive ? tokens.get(i) : tokens.get(i).toLowerCase();
                            match = content.contains(t);
                        }
                        if (match) found.add(tokens.get(i));
                    }
                    
                    if ("REQUIRED".equalsIgnoreCase(mode)) {
                        if ("OR".equalsIgnoreCase(logic)) {
                            // OR: At least one must be present
                            if (found.isEmpty()) {
                                filePassed = false;
                                failureReason = "Missing any of: " + tokens;
                            }
                        } else {
                            // AND (Default): All must be present
                            if (found.size() < tokens.size()) {
                                filePassed = false;
                                List<String> missing = new ArrayList<>(tokens);
                                missing.removeAll(found);
                                failureReason = "Missing: " + missing;
                            }
                        }
                    } else {
                        // FORBIDDEN
                        if ("AND".equalsIgnoreCase(logic)) {
                            // AND: Fail only if ALL are present
                            if (found.size() == tokens.size()) {
                                filePassed = false;
                                failureReason = "Found all forbidden: " + found;
                            }
                        } else {
                            // OR (Default for forbidden): Fail if ANY is present
                            if (!found.isEmpty()) {
                                filePassed = false;
                                failureReason = "Found: " + found;
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
            
        } catch (Exception e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Scan Error: " + e.getMessage());
        }
        
        boolean overallPass = evaluateMatchMode(matchMode, totalFiles, passedFileCount);
        
        if (overallPass) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(), 
                String.format("Passed %s check in %d/%d files (Mode: %s)", mode, passedFileCount, totalFiles, matchMode));
        } else {
             return CheckResult.fail(check.getRuleId(), check.getDescription(), 
                String.format("Validation failed for %s. (MatchMode: %s, Passed: %d/%d). Details:\n• %s", 
                    mode, matchMode, passedFileCount, totalFiles, String.join("\n• ", failureDetails)));
        }
    }
}
