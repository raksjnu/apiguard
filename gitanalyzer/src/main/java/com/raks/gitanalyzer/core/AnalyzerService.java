package com.raks.gitanalyzer.core;

import com.raks.gitanalyzer.provider.GitProvider;
import com.raks.gitanalyzer.model.AnalysisResult;
import com.raks.gitanalyzer.model.FileChange;
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

    public AnalysisResult analyze(String apiName, String codeRepo, String configRepo, String sourceBranch, String targetBranch, List<String> filePatterns, List<String> contentPatterns, String configSourceBranch, String configTargetBranch, boolean ignoreAttributeOrder) {
        AnalysisResult result = new AnalysisResult();
        result.setApiName(apiName);
        result.setCodeRepo(codeRepo);
        result.setConfigRepo(configRepo);
        result.setSourceBranch(sourceBranch);
        result.setTargetBranch(targetBranch);

        try {
            // 1. Comparison
            String diffJson = gitProvider.compareBranches(codeRepo, sourceBranch, targetBranch);
            Map<String, Object> diffMap = objectMapper.readValue(diffJson, Map.class);
            
            // 2. Process Diffs
            List<Map<String, Object>> diffs = (List<Map<String, Object>>) diffMap.get("diffs");
            processDiffs(diffs, result, filePatterns, contentPatterns, ignoreAttributeOrder);

            // 3. Config Repo Analysis (Optional)
            if (configRepo != null && !configRepo.isEmpty()) {
                String cSource = (configSourceBranch != null && !configSourceBranch.isBlank()) ? configSourceBranch : sourceBranch;
                String cTarget = (configTargetBranch != null && !configTargetBranch.isBlank()) ? configTargetBranch : targetBranch;
                
                String configDiffJson = gitProvider.compareBranches(configRepo, cSource, cTarget);
                Map<String, Object> configDiff = objectMapper.readValue(configDiffJson, Map.class);
                List<Map<String, Object>> configDiffs = (List<Map<String, Object>>) configDiff.get("diffs");
                processConfigDiffs(configDiffs, result, filePatterns);
            }

        } catch (Exception e) {
            e.printStackTrace();
            result.setError("Analysis failed: " + e.getMessage());
        }

        return result;
    }

    private void processDiffs(List<Map<String, Object>> diffs, AnalysisResult result, List<String> filePatterns, List<String> contentPatterns, boolean ignoreAttributeOrder) {
        if (diffs == null) return;

        for (Map<String, Object> diff : diffs) {
            String newPath = (String) diff.get("new_path");
            String oldPath = (String) diff.get("old_path");
            
            // Check File Patterns (Exclusion)
            if (shouldIgnore(newPath, filePatterns) || (oldPath != null && shouldIgnore(oldPath, filePatterns))) {
                continue; // Skip this file entirely
            }

            FileChange change = new FileChange();
            change.setPath(newPath);
            
            boolean isNew = (boolean) diff.get("new_file");
            boolean isDeleted = (boolean) diff.get("deleted_file");

            change.setNewFile(isNew);
            change.setDeletedFile(isDeleted);
            
            change.setType("CODE"); // Default to CODE

            // Reconstruct Git Diff Header
            String diffContent = (String) diff.get("diff");
             if (diffContent != null && !diffContent.isEmpty()) {
                StringBuilder fullDiff = new StringBuilder();
                // Replace StringUtils.isNotEmpty with standard check
                String oldPathStr = (oldPath != null && !oldPath.isEmpty()) ? oldPath : newPath;
                
                fullDiff.append("diff --git a/").append(oldPathStr)
                        .append(" b/").append(newPath).append("\n");
                if (isNew) {
                    fullDiff.append("new file mode 100644\n");
                    fullDiff.append("--- /dev/null\n");
                    fullDiff.append("+++ b/").append(newPath).append("\n");
                } else if (isDeleted) {
                    fullDiff.append("deleted file mode 100644\n");
                    fullDiff.append("--- a/").append(oldPath).append("\n");
                    fullDiff.append("+++ /dev/null\n");
                } else {
                    fullDiff.append("--- a/").append(oldPathStr).append("\n");
                    fullDiff.append("+++ b/").append(newPath).append("\n");
                }
                fullDiff.append(diffContent);
                change.setDiffContent(fullDiff.toString());
            }

            calculateLines(change, diffContent, contentPatterns, ignoreAttributeOrder);
            
            result.addFileChange(change);
        }
    }

    private void processConfigDiffs(List<Map<String, Object>> diffs, AnalysisResult result, List<String> filePatterns) {
        if (diffs == null) return;
        // For simplicity, let's just loop and add basic changes, marking as CONFIG.
         for (Map<String, Object> diff : diffs) {
            String newPath = (String) diff.get("new_path");
            if (shouldIgnore(newPath, filePatterns)) continue;
            
            FileChange change = new FileChange();
            change.setPath(newPath);
            change.setType("CONFIG");
            // ... (fill other fields if needed, but mostly we care about count)
            result.addFileChange(change); // This increments Total and Config stats in AnalysisResult
         }
    }
    
    // Updated calculateLines with XML Canonicalization logic
    private void calculateLines(FileChange change, String diffContent, List<String> patterns, boolean ignoreAttributeOrder) {
        if (diffContent == null || diffContent.isEmpty()) {
            return;
        }
        
        // 1. Parse Diff into Minus and Plus buckets
        List<String> minusLines = new ArrayList<>();
        List<String> plusLines = new ArrayList<>();
        List<String> rawDiffLines = new ArrayList<>(); // Store raw lines to map back
        
        String[] lines = diffContent.split("\n");
        for (String line : lines) {
            // Ignore headers
            if (line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff --git") || line.startsWith("index ")) {
                continue;
            }
            
            if (line.startsWith("-")) {
                minusLines.add(line.substring(1).trim());
                rawDiffLines.add(line);
            } else if (line.startsWith("+")) {
                plusLines.add(line.substring(1).trim());
                rawDiffLines.add(line);
            }
        }
        
        int valid = 0;
        int ignored = 0;
        
        // 2. Semantic Ignore Logic (XML)
        List<String> semanticallyIgnoredPlus = new ArrayList<>();
        
        // Only run if Feature Enabled AND is XML
        if (ignoreAttributeOrder && change.getPath() != null && change.getPath().toLowerCase().endsWith(".xml")) {
            // Bag matching: Count occurrences of Canonical Strings
            Map<String, Integer> minusCounts = new HashMap<>();
            
            for (String m : minusLines) {
                String canon = com.raks.gitanalyzer.util.XmlCanonicalizer.canonicalize(m);
                minusCounts.put(canon, minusCounts.getOrDefault(canon, 0) + 1);
            }
            
            for (String p : plusLines) {
                String canon = com.raks.gitanalyzer.util.XmlCanonicalizer.canonicalize(p);
                if (minusCounts.containsKey(canon) && minusCounts.get(canon) > 0) {
                     // Found a semantic match! It's a reordering or format change.
                     semanticallyIgnoredPlus.add(p);
                     // Decrement match count (remove one instance)
                     minusCounts.put(canon, minusCounts.get(canon) - 1);
                }
            }
        }
        
        // 3. Final count pass
        
        // Strategy: 
        // Iterate Plus Lines: If in semanticallyIgnoredPlus -> Ignored++. Else -> check regex.
        // Iterate Minus Lines: If it was matched -> Ignored++. Else -> check regex (usually deletions count as valid changes? or just ignored?)
        
        // We need to match specific Minus string instances.
        // Let's use a Bag for matched items to consume.
        List<String> matchPool = new ArrayList<>(semanticallyIgnoredPlus);
        
        // Count Plus
        for (String p : plusLines) {
            String canon = (ignoreAttributeOrder && change.getPath() != null && change.getPath().toLowerCase().endsWith(".xml")) 
                           ? com.raks.gitanalyzer.util.XmlCanonicalizer.canonicalize(p) : null;
            
            boolean semanticMatch = false;
            // Check if this P's canonical form was matched
             if (canon != null) {
                 if (matchPool.remove(p)) { 
                     semanticMatch = true;
                 }
             }

            if (semanticMatch) {
                ignored++;
            } else {
                if (isIgnored(p, patterns)) ignored++;
                else valid++;
            }
        }
        
        // Count Minus (We assume equal number of minus lines were matched)
        // Rebuild Matching Map
        Map<String, Integer> plusCanonCounts = new HashMap<>();
        if (ignoreAttributeOrder && change.getPath() != null && change.getPath().toLowerCase().endsWith(".xml")) {
             for (String p : semanticallyIgnoredPlus) { // These are the Ps that found a match
                 String c = com.raks.gitanalyzer.util.XmlCanonicalizer.canonicalize(p);
                 plusCanonCounts.put(c, plusCanonCounts.getOrDefault(c, 0) + 1);
             }
        }
        
        for (String m : minusLines) {
             boolean semanticMatch = false;
             if (ignoreAttributeOrder && change.getPath() != null && change.getPath().toLowerCase().endsWith(".xml")) {
                 String c = com.raks.gitanalyzer.util.XmlCanonicalizer.canonicalize(m);
                 if (plusCanonCounts.containsKey(c) && plusCanonCounts.get(c) > 0) {
                     semanticMatch = true;
                     plusCanonCounts.put(c, plusCanonCounts.get(c) - 1);
                 }
             }
             
             if (semanticMatch) {
                 ignored++;
             } else {
                 if (isIgnored(m, patterns)) ignored++;
                 else valid++; // Deletion is a change
             }
        }
        
        change.setValidChangedLines(valid);
        change.setIgnoredLines(ignored);
        change.setSeverity(calculateSeverity(valid));
    }


    // Add back shouldIgnore
    private boolean shouldIgnore(String path, List<String> patterns) {
         if (path == null) return false;
         return isIgnored(path, patterns);
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
