package com.raks.gitanalyzer.core;

import com.raks.gitanalyzer.provider.GitProvider;
import com.raks.gitanalyzer.model.AnalysisResult;
import com.raks.gitanalyzer.model.FileChange;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnalyzerService {
    private final GitProvider gitProvider;
    private final ObjectMapper objectMapper;

    // Severity Thresholds
    private static final int SEVERITY_LOW = 10;
    private static final int SEVERITY_MEDIUM = 50;

    public AnalyzerService(GitProvider gitProvider) {
        this.gitProvider = gitProvider;
        this.objectMapper = new ObjectMapper();
    }

    public AnalysisResult analyze(String apiName, String codeRepo, String configRepo, String sourceBranch, String targetBranch, List<String> ignorePatterns) {
        System.out.println("Analyzing API: " + apiName);
        AnalysisResult result = new AnalysisResult();
        result.setApiName(apiName);
        result.setSourceBranch(sourceBranch);
        result.setTargetBranch(targetBranch);
        
        try {
            // 1. Analyze Code Repo
            System.out.println("  Checking Code Repo: " + codeRepo);
            String codeDiffJson = gitProvider.compareBranches(codeRepo, sourceBranch, targetBranch);
            processDiff(result, "CODE", codeDiffJson, ignorePatterns);

            // 2. Analyze Config Repo
            if (configRepo != null && !configRepo.isEmpty()) {
                System.out.println("  Checking Config Repo: " + configRepo);
                String configDiffJson = gitProvider.compareBranches(configRepo, sourceBranch, targetBranch);
                processDiff(result, "CONFIG", configDiffJson, ignorePatterns);
            }

        } catch (Exception e) {
            System.err.println("Analysis failed for " + apiName + ": " + e.getMessage());
            e.printStackTrace();
            // Could set functionality in result to indicate failure
        }
        return result;
    }

    private void processDiff(AnalysisResult result, String type, String jsonResponse, List<String> ignorePatterns) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode diffs = root.get("diffs");

        if (diffs != null && diffs.isArray()) {
            for (JsonNode diff : diffs) {
                String newPath = diff.get("new_path").asText();
                boolean newFile = diff.get("new_file").asBoolean();
                boolean deletedFile = diff.get("deleted_file").asBoolean();
                String diffContent = diff.has("diff") ? diff.get("diff").asText() : "";
                
                int validChangedLines = 0;
                int ignoredLines = 0;

                String[] lines = diffContent.split("\n");
                for (String line : lines) {
                    // Check only added (+) or removed (-) lines, ignoring headers (+++/---)
                    if ((line.startsWith("+") || line.startsWith("-")) && 
                        !line.startsWith("+++") && !line.startsWith("---")) {
                        
                        String content = line.substring(1).trim();
                        if (isIgnored(content, ignorePatterns)) {
                            ignoredLines++;
                        } else {
                            validChangedLines++;
                        }
                    }
                }

                String severity = calculateSeverity(validChangedLines);

                FileChange change = new FileChange(newPath, type, validChangedLines, ignoredLines, severity);
                change.setNewFile(newFile);
                change.setDeletedFile(deletedFile);
                // Optionally store diffContent if we want to show it in UI later
                // change.setDiffContent(diffContent); 
                
                result.addFileChange(change);

                System.out.println(String.format("    [%s] %s - Valid Change: %d, Ignored: %d, Severity: %s", 
                    type, newPath, validChangedLines, ignoredLines, severity));
            }
        }
    }

    private boolean isIgnored(String content, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) return false;
        for (String pattern : patterns) {
            // Simple contains check or Regex? Let's support regex if it starts with regex:
            if (pattern.startsWith("regex:")) {
                 if (content.matches(pattern.substring(6))) return true;
            } else {
                 if (content.contains(pattern)) return true;
            }
        }
        return false;
    }

    private String calculateSeverity(int lines) {
        if (lines == 0) return "NONE"; // All ignored
        if (lines < SEVERITY_LOW) return "LOW";
        if (lines < SEVERITY_MEDIUM) return "MEDIUM";
        return "CRITICAL";
    }
}
