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
            
            // 2. Process Diffs (Handle GitHub "files" vs GitLab "diffs")
            List<Map<String, Object>> diffList = (List<Map<String, Object>>) diffMap.get("diffs");
            if (diffList == null) {
                diffList = (List<Map<String, Object>>) diffMap.get("files");
            }
            processDiffs(diffList, result, filePatterns, contentPatterns, ignoreAttributeOrder);

            // 3. Config Repo Analysis (Optional)
            if (configRepo != null && !configRepo.isEmpty()) {
                try {
                    String cSource = (configSourceBranch != null && !configSourceBranch.isBlank()) ? configSourceBranch : sourceBranch;
                    String cTarget = (configTargetBranch != null && !configTargetBranch.isBlank()) ? configTargetBranch : targetBranch;
                    
                    String configDiffJson = gitProvider.compareBranches(configRepo, cSource, cTarget);
                    Map<String, Object> configDiff = objectMapper.readValue(configDiffJson, Map.class);
                    
                    List<Map<String, Object>> configDiffList = (List<Map<String, Object>>) configDiff.get("diffs");
                    if (configDiffList == null) {
                        configDiffList = (List<Map<String, Object>>) configDiff.get("files");
                    }
                    processConfigDiffs(configDiffList, result, filePatterns, contentPatterns, ignoreAttributeOrder);
                } catch (Exception e) {
                    // Suppress stack trace for Config failures (e.g. 404 Ref Not Found)
                    System.out.println("Warning: Config Repo analysis failed: " + e.getMessage() + ". Proceeding with Code Repo analysis only.");
                }
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
            // Normalize GitHub vs GitLab keys
            String newPath = (String) diff.getOrDefault("new_path", diff.get("filename"));
            String oldPath = (String) diff.getOrDefault("old_path", diff.get("previous_filename"));
            String diffContent = (String) diff.getOrDefault("diff", diff.get("patch"));
            
            // Check File Patterns (Exclusion)
            if (shouldIgnore(newPath, filePatterns) || (oldPath != null && shouldIgnore(oldPath, filePatterns))) {
                result.addIgnoredFile(newPath);
                continue;
            }

            FileChange change = new FileChange();
            change.setPath(newPath);
            
            // Normalize Status
            boolean isNew = false;
            boolean isDeleted = false;
            
            if (diff.containsKey("new_file")) {
                isNew = (boolean) diff.get("new_file");
                isDeleted = (boolean) diff.get("deleted_file");
            } else if (diff.containsKey("status")) {
                String status = (String) diff.get("status");
                isNew = "added".equals(status);
                isDeleted = "removed".equals(status);
            }

            change.setNewFile(isNew);
            change.setDeletedFile(isDeleted);
            change.setType("CODE");

            // Reconstruct Git Diff Header if it's missing (GitHub provides patch without header often)
            if (diffContent != null && !diffContent.isEmpty()) {
                StringBuilder fullDiff = new StringBuilder();
                String oldPathStr = (oldPath != null && !oldPath.isEmpty()) ? oldPath : newPath;
                
                // If it doesn't look like a full diff header, add it
                if (!diffContent.startsWith("diff --git")) {
                    fullDiff.append("diff --git a/").append(oldPathStr).append(" b/").append(newPath).append("\n");
                    if (isNew) {
                        fullDiff.append("new file mode 100644\n--- /dev/null\n+++ b/").append(newPath).append("\n");
                    } else if (isDeleted) {
                        fullDiff.append("deleted file mode 100644\n--- a/").append(oldPath).append("\n+++ /dev/null\n");
                    } else {
                        fullDiff.append("--- a/").append(oldPathStr).append("\n+++ b/").append(newPath).append("\n");
                    }
                }
                fullDiff.append(diffContent);
                change.setDiffContent(fullDiff.toString());
            }

            calculateLines(change, diffContent, contentPatterns, ignoreAttributeOrder);
            result.addFileChange(change);
        }
    }

    private void processConfigDiffs(List<Map<String, Object>> diffs, AnalysisResult result, List<String> filePatterns, List<String> contentPatterns, boolean ignoreAttributeOrder) {
        if (diffs == null) return;
        for (Map<String, Object> diff : diffs) {
            String newPath = (String) diff.getOrDefault("new_path", diff.get("filename"));
            String oldPath = (String) diff.getOrDefault("old_path", diff.get("previous_filename"));
            String diffContent = (String) diff.getOrDefault("diff", diff.get("patch"));
            
            if (shouldIgnore(newPath, filePatterns) || (oldPath != null && shouldIgnore(oldPath, filePatterns))) {
                result.addIgnoredFile(newPath); 
                continue;
            }
            
            FileChange change = new FileChange();
            change.setPath(newPath);
            change.setType("CONFIG");
            
            boolean isNew = false;
            boolean isDeleted = false;
            if (diff.containsKey("new_file")) {
                isNew = (boolean) diff.get("new_file");
                isDeleted = (boolean) diff.get("deleted_file");
            } else if (diff.containsKey("status")) {
                String status = (String) diff.get("status");
                isNew = "added".equals(status);
                isDeleted = "removed".equals(status);
            }

            change.setNewFile(isNew);
            change.setDeletedFile(isDeleted);

            if (diffContent != null && !diffContent.isEmpty()) {
                StringBuilder fullDiff = new StringBuilder();
                String oldPathStr = (oldPath != null && !oldPath.isEmpty()) ? oldPath : newPath;
                
                if (!diffContent.startsWith("diff --git")) {
                    fullDiff.append("diff --git a/").append(oldPathStr).append(" b/").append(newPath).append("\n");
                    if (isNew) {
                        fullDiff.append("new file mode 100644\n--- /dev/null\n+++ b/").append(newPath).append("\n");
                    } else if (isDeleted) {
                        fullDiff.append("deleted file mode 100644\n--- a/").append(oldPath).append("\n+++ /dev/null\n");
                    } else {
                        fullDiff.append("--- a/").append(oldPathStr).append("\n+++ b/").append(newPath).append("\n");
                    }
                }
                fullDiff.append(diffContent);
                change.setDiffContent(fullDiff.toString());
            }

            calculateLines(change, diffContent, contentPatterns, ignoreAttributeOrder);
            result.addFileChange(change);
         }
    }
    
    // Updated calculateLines with XML Canonicalization logic and multi-filter support
    private void calculateLines(FileChange change, String diffContent, List<String> patterns, boolean ignoreAttributeOrder) {
        if (diffContent == null || diffContent.isEmpty()) {
            return;
        }
        
        // 1. Parse Diff into Minus and Plus buckets
        List<String> minusLines = new ArrayList<>();
        List<String> plusLines = new ArrayList<>();
        
        String[] lines = diffContent.split("\n");
        for (String line : lines) {
            // Ignore headers
            if (line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff --git") || line.startsWith("index ") || line.startsWith("@@")) {
                continue;
            }
            
            if (line.startsWith("-")) {
                minusLines.add(line.substring(1).trim());
            } else if (line.startsWith("+")) {
                plusLines.add(line.substring(1).trim());
            }
        }
        
        int valid = 0;
        int ignored = 0;
        int additions = 0;
        int deletions = 0;
        
        // 2. Semantic Ignore Logic (XML) - Expand extensions to .policy, .mflow, etc.
        boolean isXmlLike = false;
        if (change.getPath() != null) {
            String p = change.getPath().toLowerCase();
            isXmlLike = p.endsWith(".xml") || p.endsWith(".policy") || p.endsWith(".mflow") || p.endsWith(".xapi");
        }

        List<String> semanticallyIgnoredPlus = new ArrayList<>();
        Map<String, Integer> plusCanonCountsForMinus = new HashMap<>();

        if (ignoreAttributeOrder && isXmlLike) {
            Map<String, Integer> minusCounts = new HashMap<>();
            for (String m : minusLines) {
                String canon = com.raks.gitanalyzer.util.XmlCanonicalizer.canonicalize(m);
                minusCounts.put(canon, minusCounts.getOrDefault(canon, 0) + 1);
            }
            
            for (String p : plusLines) {
                String canon = com.raks.gitanalyzer.util.XmlCanonicalizer.canonicalize(p);
                if (minusCounts.containsKey(canon) && minusCounts.get(canon) > 0) {
                     semanticallyIgnoredPlus.add(p);
                     minusCounts.put(canon, minusCounts.get(canon) - 1);
                     // Also track for minus matching
                     plusCanonCountsForMinus.put(canon, plusCanonCountsForMinus.getOrDefault(canon, 0) + 1);
                }
            }
        }
        
        // 3. Counting Logic
        
        // Use a Bag for matched items to consume.
        List<String> matchPool = new ArrayList<>(semanticallyIgnoredPlus);
        
        // Count Plus
        for (String p : plusLines) {
            boolean isSmartXml = matchPool.remove(p);
            boolean isContentFiltered = isIgnored(p, patterns);

            if (isSmartXml || isContentFiltered) {
                ignored++;
                if (isSmartXml) change.addMatchType("Smart XML");
                if (isContentFiltered) change.addMatchType("Content Filter");
            } else {
                valid++;
                additions++;
            }
        }
        
        // Count Minus
        for (String m : minusLines) {
             String canon = (ignoreAttributeOrder && isXmlLike) ? com.raks.gitanalyzer.util.XmlCanonicalizer.canonicalize(m) : null;
             boolean isSmartXml = false;
             if (canon != null && plusCanonCountsForMinus.containsKey(canon) && plusCanonCountsForMinus.get(canon) > 0) {
                 isSmartXml = true;
                 plusCanonCountsForMinus.put(canon, plusCanonCountsForMinus.get(canon) - 1);
             }
             
             boolean isContentFiltered = isIgnored(m, patterns);

             if (isSmartXml || isContentFiltered) {
                 ignored++;
                 if (isSmartXml) change.addMatchType("Smart XML");
                 if (isContentFiltered) change.addMatchType("Content Filter");
             } else {
                 valid++;
                 deletions++;
             }
        }
        
        change.setValidChangedLines(valid);
        change.setIgnoredLines(ignored);
        change.setAdditions(additions);
        change.setDeletions(deletions);
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
        
        int lowThreshold = Integer.parseInt(ConfigManager.get("severity.threshold.low", "10"));
        int mediumThreshold = Integer.parseInt(ConfigManager.get("severity.threshold.medium", "50"));
        
        if (lines < lowThreshold) return "LOW";
        if (lines < mediumThreshold) return "MEDIUM";
        return "HIGH";
    }
}
