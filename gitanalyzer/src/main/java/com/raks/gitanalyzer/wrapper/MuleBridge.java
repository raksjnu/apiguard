package com.raks.gitanalyzer.wrapper;

import com.raks.gitanalyzer.core.AnalyzerService;
import com.raks.gitanalyzer.core.ConfigManager;
import com.raks.gitanalyzer.model.AnalysisResult;
import com.raks.gitanalyzer.provider.GitProvider;
import com.raks.gitanalyzer.provider.GitHubProvider;
import com.raks.gitanalyzer.provider.GitLabProvider;
import java.util.*;
import java.io.File;
import org.eclipse.jgit.storage.file.WindowCacheConfig;

/**
 * MuleBridge - Static entry point for MuleSoft wrapper to invoke GitAnalyzer features.
 * Ensures cross-platform compatibility and externalized configuration support.
 */
public class MuleBridge {

    /**
     * Updates configuration properties dynamically.
     */
    static {
        // Disable JGit file locking (MMAP) on Windows
        // This must run before any JGit operations
        try {
            WindowCacheConfig jgitConfig = new WindowCacheConfig();
            jgitConfig.setPackedGitMMAP(false);
            jgitConfig.install();
        } catch (Exception ignored) {}
    }

    /**
     * Updates configuration properties dynamically.
     */
    public static void updateConfig(Map<String, Object> config) {
        if (config != null) {
            Map<String, String> stringProps = new HashMap<>();
            for (Map.Entry<String, Object> entry : config.entrySet()) {
                if (entry.getValue() != null) {
                    stringProps.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
            ConfigManager.setAll(stringProps);
        }
    }

    /**
     * Invokes the main analysis flow.
     * @param params Map containing request parameters (from Mule payload)
     * @return AnalysisResult object (Mule will serialize this to JSON)
     */
    public static AnalysisResult invokeAnalysis(Map<String, Object> params) {
        try {
            // Extract parameters
            String codeRepo = (String) params.get("codeRepo");
            String configRepo = (String) params.get("configRepo");
            String sourceBranch = (String) params.get("sourceBranch");
            String targetBranch = (String) params.get("targetBranch");
            String ignorePatternsStr = (String) params.get("ignorePatterns");
            String contentPatternsStr = (String) params.get("contentIgnorePatterns");
            if (contentPatternsStr == null) contentPatternsStr = (String) params.get("contentPatterns");
            String apiName = (String) params.getOrDefault("apiName", "Unknown API");
            String configSourceBranch = (String) params.get("configSourceBranch");
            String configTargetBranch = (String) params.get("configTargetBranch");
            boolean ignoreAttributeOrder = Boolean.TRUE.equals(params.get("ignoreAttributeOrder"));

            // Extract Token and Provider
            String token = (String) params.get("gitToken");
            if (token == null || token.isBlank()) {
                token = (String) params.get("token"); // Fallback for old standalone behavior
            }
            String providerType = (String) params.get("gitProvider");

            // Parse patterns
            List<String> filePatterns = parsePatterns(ignorePatternsStr);
            List<String> contentPatterns = parsePatterns(contentPatternsStr);

            GitProvider provider = getProvider(token, providerType);

            // Normalization Logic (reused from AnalysisResource)
            String defaultGroup = "";
            if (provider instanceof GitHubProvider) {
                defaultGroup = ConfigManager.get("github.owner");
            } else {
                String fullGroup = ConfigManager.get("gitlab.group");
                if (fullGroup != null && fullGroup.contains("/")) 
                     defaultGroup = fullGroup.substring(fullGroup.lastIndexOf("/") + 1);
                else 
                     defaultGroup = fullGroup;
            }
            
            // Sanitize standard repo paths if they are just names
            if (codeRepo != null && !codeRepo.contains("/") && defaultGroup != null && !defaultGroup.isBlank()) {
                codeRepo = defaultGroup + "/" + codeRepo;
            }
            if (configRepo != null && !configRepo.isBlank() && !configRepo.contains("/") && defaultGroup != null && !defaultGroup.isBlank()) {
                configRepo = defaultGroup + "/" + configRepo;
            }

            AnalyzerService service = new AnalyzerService(provider);
            return service.analyze(apiName, codeRepo, configRepo, sourceBranch, targetBranch, filePatterns, contentPatterns, configSourceBranch, configTargetBranch, ignoreAttributeOrder);

        } catch (Exception e) {
            e.printStackTrace();
            AnalysisResult errorResult = new AnalysisResult();
            String msg = e.getMessage() != null ? e.getMessage() : e.toString();
            // Sanitize message to prevent JSON issues
            msg = msg.replaceAll("[\\n\\r\\t]", " ");
            errorResult.setError("Bridge Analysis Failed: " + msg);
            return errorResult;
        }
    }

    /**
     * Returns UI configuration properties for the frontend.
     */
    public static Map<String, String> getUIConfig() {
        return ConfigManager.getPropertiesByPrefix("ui.");
    }
    
    /**
     * Helper to get provider based on config or override.
     */
    private static GitProvider getProvider(String token, String providerOverride) {
        String providerType = providerOverride;
        if (providerType == null || providerType.isBlank()) {
            providerType = ConfigManager.get("git.provider", "gitlab");
        }
        
        if ("github".equalsIgnoreCase(providerType)) {
            return new GitHubProvider(token);
        } else {
            return new GitLabProvider(token);
        }
    }

    /**
     * Bridges /api/download/list (Repo Discovery)
     */
    public static Map<String, Object> invokeRepoDiscovery(String groupName, String filterPattern, String token, String provider) {
        try {
            GitProvider gitProvider = getProvider(token, provider);
            
            // Default group logic
            if (groupName == null || groupName.isBlank()) {
                if (gitProvider instanceof GitHubProvider) {
                     groupName = ConfigManager.get("github.owner");
                } else {
                    String fullGroupUrl = ConfigManager.get("gitlab.group");
                    if (fullGroupUrl != null && fullGroupUrl.contains("/")) {
                        groupName = fullGroupUrl.substring(fullGroupUrl.lastIndexOf("/") + 1);
                    } else {
                        groupName = fullGroupUrl;
                    }
                }
            } else {
                groupName = groupName.trim();
                while(groupName.endsWith("/")) groupName = groupName.substring(0, groupName.length() - 1);
                if (groupName.contains("/")) {
                    groupName = groupName.substring(groupName.lastIndexOf("/") + 1);
                }
            }
            
            List<String> repos = gitProvider.listRepositories(groupName);
            
            // Regex Filter
            if (filterPattern != null && !filterPattern.isBlank()) {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(filterPattern, java.util.regex.Pattern.CASE_INSENSITIVE);
                repos = repos.stream()
                    .filter(r -> pattern.matcher(r).find())
                    .collect(java.util.stream.Collectors.toList());
            }
            
            return Map.of("group", groupName != null ? groupName : "", "repositories", repos);
            
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("error", "Discovery Failed: " + e.getMessage());
        }
    }

    /**
     * Bridges /api/download/branches
     */
    public static Object invokeBranchDiscovery(String repoName, String token, String provider) {
         try {
            if (repoName == null || repoName.isBlank()) {
                 return Map.of("error", "Repo name is required");
            }
            GitProvider gitProvider = getProvider(token, provider);
            return gitProvider.listBranches(repoName);
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Bridges /api/search
     */
    public static List<Map<String, Object>> invokeSearch(Map<String, Object> params) {
        try {
            String gitToken = (String) params.get("gitToken");
            String gitProvider = (String) params.get("gitProvider");
            String path = (String) params.get("path");
            
            // The search 'token' (keyword) should come from the 'token' key in params
            String searchKeyword = (String) params.get("token");

            GitProvider provider = getProvider(gitToken, gitProvider);
            com.raks.gitanalyzer.core.SearchService service = new com.raks.gitanalyzer.core.SearchService(provider);
            List<com.raks.gitanalyzer.core.SearchService.SearchResult> serviceResults;

            String mode = (String) params.get("mode");
            if ("remote".equalsIgnoreCase(mode)) {
                List<String> repos = List.of(path.split("\\s*,\\s*"));
                com.raks.gitanalyzer.core.SearchService.SearchParams searchParams = new com.raks.gitanalyzer.core.SearchService.SearchParams();
                searchParams.token = searchKeyword; 
                searchParams.caseSensitive = Boolean.parseBoolean(String.valueOf(params.get("caseSensitive")));
                searchParams.searchType = (String) params.getOrDefault("searchType", "substring");
                searchParams.ignoreComments = Boolean.parseBoolean(String.valueOf(params.getOrDefault("ignoreComments", "true")));
                
                // Parse Filters
                searchParams.includeFolders = getListParam(params, "includeFolders");
                searchParams.includeFiles = getListParam(params, "includeFiles");
                searchParams.ignoreFolders = getListParam(params, "ignoreFolders");
                searchParams.ignoreFiles = getListParam(params, "ignoreFiles");

                serviceResults = service.searchRemote(searchParams, repos); 
            } else {
                com.raks.gitanalyzer.core.SearchService.SearchParams searchParams = new com.raks.gitanalyzer.core.SearchService.SearchParams();
                searchParams.token = searchKeyword; // Use renamed keyword
                searchParams.caseSensitive = Boolean.parseBoolean(String.valueOf(params.get("caseSensitive")));
                searchParams.searchType = (String) params.getOrDefault("searchType", "substring");
                searchParams.ignoreComments = Boolean.parseBoolean(String.valueOf(params.getOrDefault("ignoreComments", "true")));
                
                serviceResults = service.searchLocal(searchParams, new File(path));
            }

            List<Map<String, Object>> results = new ArrayList<>();
            for (com.raks.gitanalyzer.core.SearchService.SearchResult r : serviceResults) {
                Map<String, Object> row = new HashMap<>();
                row.put("filePath", r.filePath);
                row.put("lineNumber", r.lineNumber);
                row.put("content", r.content);
                row.put("context", r.context);
                row.put("absolutePath", r.absolutePath);
                results.add(row);
            }
            return results;
            
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> getListParam(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val instanceof List) {
            return (List<String>) val;
        }
        return new ArrayList<>();
    }

    /**
     * Bridges /api/search/open
     */
    public static Map<String, String> invokeOpenFile(Map<String, String> params) {
        try {
            String path = params.get("path");
            if (path == null) return Map.of("error", "Path is required");
            File file = new File(path);
            if (file.exists()) {
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(file);
                    return Map.of("status", "success");
                } else {
                    return Map.of("error", "Desktop opening not supported on server");
                }
            } else {
                return Map.of("error", "File not found");
            }
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Bridges /api/download (Bulk Download)
     */
    public static Map<String, Object> invokeBulkDownload(Map<String, Object> request) {
        try {
            String gitToken = (String) request.get("gitToken");
            String gitProvider = (String) request.get("gitProvider");
            if (gitToken == null) gitToken = (String) request.get("token");

            // "repos" parsing
            Object reposObj = request.get("repos");
            List<String> repos = new ArrayList<>();
            if (reposObj instanceof List) {
                repos = (List<String>) reposObj;
            } else if (reposObj instanceof String) {
                repos = Arrays.asList(((String) reposObj).split("\\n"));
            }
            repos.replaceAll(String::trim);
            repos.removeIf(String::isBlank);

            // "branches" parsing
            Object branchesObj = request.get("branches"); 
            List<String> branches = new ArrayList<>();
             if (branchesObj instanceof List) {
                branches = (List<String>) branchesObj;
            } else if (branchesObj instanceof String) {
                 String branchesInput = (String) branchesObj;
                 if (branchesInput != null && !branchesInput.isBlank()) {
                    branches = Arrays.asList(branchesInput.split(","));
                 }
            }
            branches.replaceAll(String::trim);
            branches.removeIf(String::isBlank);
            
            if (branches.isEmpty()) {
                branches.add(null); // Default branch
            }

            if (repos.isEmpty()) {
                 return Map.of("error", "No repositories specified");
            }

            String customPath = (String) request.get("outputDir");
            File bulkDir;
            if (customPath != null && !customPath.isBlank()) {
               bulkDir = new File(customPath);
            } else {
               bulkDir = new File(ConfigManager.getTempDir(), "bulk_" + ConfigManager.getCurrentTimestamp().replace(" ", "_").replace(":", "-"));
            }
            if (!bulkDir.exists()) bulkDir.mkdirs();

            GitProvider provider = getProvider(gitToken, gitProvider);
            List<Map<String, String>> results = new ArrayList<>();
            int successCount = 0;
            boolean multiBranch = branches.size() > 1;

            for (String repoName : repos) {
                String baseFolderName = repoName.replace("/", "_");
                
                for (String branch : branches) {
                    Map<String, String> result = new HashMap<>();
                    result.put("repo", repoName);
                    String displayBranch = (branch == null) ? "Default" : branch;
                    
                    try {
                        String folderName = baseFolderName;
                        if (multiBranch) {
                            folderName += "_" + displayBranch.replace("/", "-");
                        }
                        
                        File repoDir = new File(bulkDir, folderName);
                        
                        // Overwrite Logic
                        boolean overwrite = Boolean.TRUE.equals(request.get("overwrite"));
                        if (repoDir.exists()) {
                            if (overwrite) {
                                // Recursive delete
                                deleteDirectory(repoDir);
                            } else {
                                // This specific message format is detected by Frontend
                                // Use a custom exception that won't log a stack trace (expected behavior)
                                ConflictException conflict = new ConflictException("Destination path \"" + repoDir.getName() + "\" already exists");
                                throw conflict;
                            }
                        }
                        
                        String fullRepoName = repoName;
                         if (!repoName.contains("/")) {
                             if (provider instanceof GitHubProvider) {
                                String owner = ConfigManager.get("github.owner");
                                if (owner != null && !owner.isBlank()) fullRepoName = owner + "/" + repoName;
                             } else {
                                String group = ConfigManager.get("gitlab.group");
                                if (group != null) {
                                     if (group.contains("/")) group = group.substring(group.lastIndexOf("/") + 1);
                                     fullRepoName = group + "/" + repoName;
                                }
                             }
                         }

                        provider.cloneRepository(fullRepoName, repoDir, branch);
                        
                        result.put("status", "SUCCESS"); // Standardize to UPPERCASE
                        result.put("branch", displayBranch);
                        result.put("path", repoDir.getAbsolutePath());
                        successCount++;
                    } catch (ConflictException e) {
                        // Expected conflict - don't log stack trace
                        result.put("status", "FAILURE: " + e.getMessage());
                        result.put("error", e.getMessage());
                        result.put("branch", displayBranch);
                    } catch (Exception e) {
                        // Unexpected error - log for debugging
                        System.err.println("Bulk download error for " + repoName + ": " + e.getMessage());
                        // Standardize error status for Frontend detection
                        result.put("status", "FAILURE: " + e.getMessage());
                        result.put("error", e.getMessage());
                        result.put("branch", displayBranch);
                    }
                    results.add(result);
                }
            }

            return Map.of(
                "outputDir", bulkDir.getAbsolutePath(),
                "total", repos.size() * branches.size(),
                "success", successCount,
                "details", results
            );

        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("error", "Bulk Download Failed: " + e.getMessage());
        }
    }

    private static List<String> parsePatterns(String patternStr) {
        if (patternStr == null || patternStr.isBlank()) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>(Arrays.asList(patternStr.split("\\n")));
        list.replaceAll(String::trim);
        return list;
    }

    // Helper for recursive deletion with force support
    private static boolean deleteDirectory(File file) {
        if (!file.exists()) return true;

        // Try to handle read-only files (common in .git)
        try {
            if (!file.canWrite()) {
                file.setWritable(true);
            }
        } catch (Exception ignored) {}

        if (file.isDirectory()) {
            File[] entries = file.listFiles();
            if (entries != null) {
                for (File entry : entries) {
                    if (!deleteDirectory(entry)) {
                        // Don't return false immediately, try allowing GC to run
                    }
                }
            }
        }

        // Expanded Retry Logic for Windows File Locking
        // 1. First attempt
        if (file.delete()) return true;

        // 2. Retry with small delays
        for (int i = 0; i < 3; i++) {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            if (file.delete()) return true;
        }

        // 3. Trigger GC to release memory mapped files (Git pack files)
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        if (file.delete()) return true;

        // 4. One final desperate attempt
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        if (file.delete()) return true;
        
        System.err.println("Failed to delete permanently: " + file.getAbsolutePath());
        return false;
    }

    /**
     * Bridges /api/git/validate
     */
    public static Map<String, Object> invokeValidation(Map<String, String> request) {
        try {
            String providerType = request.get("provider");
            String token = request.get("token");
            
            if (token == null || token.isBlank()) {
                return Map.of("status", "error", "message", "Token is required");
            }

            GitProvider provider = getProvider(token, providerType);
            provider.validateCredentials();
            
            return Map.of("status", "success", "message", "Credentials are valid.");
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("status", "error", "message", e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }
}
