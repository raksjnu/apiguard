package com.raks.gitanalyzer.provider;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.eclipse.jgit.api.Git;

import com.raks.gitanalyzer.core.ConfigManager;

public class GitLabProvider implements GitProvider {
    
    private final String baseUrl;
    private final String token;
    private final String groupUrl;

    public GitLabProvider() {
        this(null);
    }

    public GitLabProvider(String userToken) {
        String url = ConfigManager.get("gitlab.url");
        this.baseUrl = (url != null && !url.isBlank()) ? url : "https://gitlab.com";
        
        String group = ConfigManager.get("gitlab.group");
        this.groupUrl = (group != null) ? group : "";
        
        // Priority: User Token > Config Token
        if (userToken != null && !userToken.isBlank()) {
            this.token = userToken;
        } else {
            this.token = ConfigManager.get("gitlab.token");
        }
    }

    @Override
    public void cloneRepository(String repoName, File destination, String branch) throws Exception {
        String cloneUrl;
        
        // Smart URL Cleaning for Clone as well
        String cleanName = repoName.trim();
        if (cleanName.startsWith("http://") || cleanName.startsWith("https://")) {
            // It's a full URL, use it directly (but ensuring auth if needed)
            cloneUrl = cleanName;
            // Remove .git if present to ensure consistent appending later? 
            // Actually JGit handles it, but our logic below might double append or break auth replacement.
            // Let's rely on standard logic: strip to path, then rebuild with Auth.
             try {
                java.net.URI uri = java.net.URI.create(cleanName);
                String path = uri.getPath();
                if (path.startsWith("/")) path = path.substring(1);
                 
                // If the user provided a full URL that matches our Base URL, we can treat it as a relative path
                // But if it's external, we might need to handle it differently.
                // For now assuming same GitLab instance as configured.
                cleanName = path;
                if (cleanName.endsWith(".git")) cleanName = cleanName.substring(0, cleanName.length() - 4);
                
                // Reconstruct standard Clone URL
                String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
                cloneUrl = base + cleanName + ".git";
            } catch (Exception e) {
                 cloneUrl = cleanName; // Fallback
            }
        } else {
             // Standard Logic
            if (repoName.contains("/")) {
                String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
                cloneUrl = base + repoName + ".git";
            } else {
                String group = groupUrl.endsWith("/") ? groupUrl : groupUrl + "/";
                cloneUrl = group + repoName + ".git";
            }
        }

        // Add token for auth (simple replacement for HTTPS)
        // Format: https://oauth2:TOKEN@gitlab.com/...
        if (token != null && !token.isEmpty()) {
            // Only replace if it matches our expected base? Or generally replace https://
            // Replacing all https:// might break if there are redirects, but standard for this tool.
            if (cloneUrl.startsWith("https://")) {
                 cloneUrl = cloneUrl.replace("https://", "https://oauth2:" + token + "@");
            }
        }
        
        // Ensure clone URL is valid (handle cases where it might be relative)
        if (cloneUrl.startsWith("/")) { 
             cloneUrl = "https://gitlab.com" + cloneUrl;
        }

        org.eclipse.jgit.api.CloneCommand command = Git.cloneRepository()
            .setURI(cloneUrl)
            .setDirectory(destination);
            
        if (branch != null && !branch.isBlank()) {
            command.setBranch(branch);
            // If it's not the default branch, we might need to specify the array of branches to clone
            command.setBranchesToClone(java.util.Collections.singletonList("refs/heads/" + branch));
        }
        
        command.call();
    }

    @Override
    public java.util.List<String> listRepositories(String groupName) throws Exception {
        // GitLab API: GET /groups/:id/projects
        // We'll search for the group first to get ID, or use the path if it matches
        // For simplicity, assuming groupName is the Path (like 'raks-group')
        // URL: /groups/raks-group/projects?include_subgroups=true&per_page=100
        
        if (groupName == null || groupName.isBlank()) {
            throw new IllegalArgumentException("Group Name is required. Please check configuration or input.");
        }
        
        String encodedGroup = java.net.URLEncoder.encode(groupName, java.nio.charset.StandardCharsets.UTF_8);
        
        String cleanBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String apiUrl = cleanBase + "/api/v4/groups/" + encodedGroup + "/projects?include_subgroups=true&per_page=100";
        
        System.out.println("[DEBUG] ListRepos - URL: " + apiUrl);
        System.out.println("[DEBUG] ListRepos - Token Present: " + (token != null && !token.isEmpty()));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("PRIVATE-TOKEN", token)
                .GET()
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        System.out.println("[DEBUG] ListRepos - Response Code: " + response.statusCode());
        
        if (response.statusCode() == 200) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.body());
            
            java.util.List<String> content = new java.util.ArrayList<>();
            if (root.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : root) {
                    // path_with_namespace is the full path e.g. "raks-group/repo1-mule-api"
                    if (node.has("path_with_namespace")) {
                        content.add(node.get("path_with_namespace").asText());
                    }
                }
            }
            System.out.println("[DEBUG] ListRepos - Found: " + content.size() + " repos");
            return content;
        } else {
             throw new RuntimeException("Failed to fetch repositories. Status: " + response.statusCode());
        }
    }

    private final java.util.Map<String, String> projectIdCache = new java.util.concurrent.ConcurrentHashMap<>();

    private String resolveProjectId(String repoName) throws Exception {
        // Smart Parsing: Handle full URLs and .git suffixes
        String searchName = repoName.trim();
        
        // Remove .git suffix if present
        if (searchName.endsWith(".git")) {
            searchName = searchName.substring(0, searchName.length() - 4);
        }
        
        // Handle Full URLs
        // If it starts with http/https, try to extract the path
        if (searchName.startsWith("http://") || searchName.startsWith("https://")) {
            try {
                java.net.URI uri = java.net.URI.create(searchName);
                String path = uri.getPath();
                // Path usually starts with /, remove it
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                searchName = path;
                System.out.println("[INFO] Parsed URL to Path: " + repoName + " -> " + searchName);
            } catch (Exception e) {
                 System.err.println("[WARN] Failed to parse repo URL: " + repoName);
                 // Fallback to using it as is, or maybe splitting by /
            }
        }

        if (projectIdCache.containsKey(searchName)) return projectIdCache.get(searchName);
        
        System.out.println("[DEBUG] Resolving Project ID for: " + searchName);
        String cleanBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        HttpClient client = HttpClient.newHttpClient();
        
        // 1. Try Direct Path Lookup
        String encodedPath = java.net.URLEncoder.encode(searchName, java.nio.charset.StandardCharsets.UTF_8);
        String directUrl = cleanBase + "/api/v4/projects/" + encodedPath;
        
        HttpRequest directReq = HttpRequest.newBuilder()
                .uri(URI.create(directUrl))
                .header("PRIVATE-TOKEN", token)
                .GET()
                .build();
        
        HttpResponse<String> directRes = client.send(directReq, HttpResponse.BodyHandlers.ofString());
        if (directRes.statusCode() == 200) {
             com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
             com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(directRes.body());
             String id = String.valueOf(node.get("id").asInt());
             projectIdCache.put(searchName, id);
             // Also cache the original if different, to avoid future reparsing? Maybe not needed.
             return id;
        }
        
        // 2. Fallback: Search
        System.out.println("[WARN] Direct lookup failed (Status " + directRes.statusCode() + "). Trying Search Fallback...");
        // Extract project name from path (last part)
        String projectName = searchName.contains("/") ? searchName.substring(searchName.lastIndexOf("/") + 1) : searchName;
        String encodedSearch = java.net.URLEncoder.encode(projectName, java.nio.charset.StandardCharsets.UTF_8);
        String searchUrl = cleanBase + "/api/v4/projects?search=" + encodedSearch + "&simple=true&per_page=100";
        
        HttpRequest searchReq = HttpRequest.newBuilder()
                .uri(URI.create(searchUrl))
                .header("PRIVATE-TOKEN", token)
                .GET()
                .build();
                
        HttpResponse<String> searchRes = client.send(searchReq, HttpResponse.BodyHandlers.ofString());
        if (searchRes.statusCode() == 200) {
             com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
             com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(searchRes.body());
             if (root.isArray()) {
                 for (com.fasterxml.jackson.databind.JsonNode node : root) {
                      String path = node.get("path_with_namespace").asText();
                      // Case-insensitive match check
                      if (path.equalsIgnoreCase(searchName)) {
                           String id = String.valueOf(node.get("id").asInt());
                           projectIdCache.put(searchName, id);
                           System.out.println("[INFO] Resolved Project via Search: " + searchName + " -> " + id);
                           return id;
                      }
                 }
             }
        }
        
        throw new RuntimeException("Project not found: " + searchName + " (Direct: " + directRes.statusCode() + ")");
    }

    @Override
    public java.util.List<String> listBranches(String repoName) throws Exception {
        String projectId = resolveProjectId(repoName);
        String cleanBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String apiUrl = cleanBase + "/api/v4/projects/" + projectId + "/repository/branches";
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("PRIVATE-TOKEN", token)
                .GET()
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.body());
            
            java.util.List<String> branches = new java.util.ArrayList<>();
            if (root.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : root) {
                    if (node.has("name")) {
                        branches.add(node.get("name").asText());
                    }
                }
            }
            return branches;
        } else {
             throw new RuntimeException("Failed to fetch branches. Status: " + response.statusCode());
        }
    }

    @Override
    public String compareBranches(String repoName, String sourceBranch, String targetBranch) throws Exception {
        String projectId = resolveProjectId(repoName);
        String cleanBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String apiUrl = cleanBase + "/api/v4/projects/" + projectId + "/repository/compare?from=" + sourceBranch + "&to=" + targetBranch;

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("PRIVATE-TOKEN", token)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new RuntimeException("Failed to compare branches. Status: " + response.statusCode() + ", Body: " + response.body());
        }
    }
    @Override
    public String searchRepository(String repoName, String query) throws Exception {
        String projectId = resolveProjectId(repoName);
        String encodedQuery = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        String cleanBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String apiUrl = cleanBase + "/api/v4/projects/" + projectId + "/search?scope=blobs&search=" + encodedQuery;

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("PRIVATE-TOKEN", token)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new RuntimeException("Failed to search repository. Status: " + response.statusCode());
        }
    }
}
