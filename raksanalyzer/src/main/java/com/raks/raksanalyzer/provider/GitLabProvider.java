package com.raks.raksanalyzer.provider;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.raks.raksanalyzer.core.config.ConfigurationManager;

public class GitLabProvider implements GitProvider {
    
    private final String baseUrl;
    private final String token;
    private final String groupUrl;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> projectIdCache = new ConcurrentHashMap<>();
    private final ConfigurationManager config = ConfigurationManager.getInstance();

    public GitLabProvider() {
        this(null);
    }

    public GitLabProvider(String userToken) {
        String url = config.getProperty("gitlab.url");
        this.baseUrl = (url != null && !url.isBlank()) ? (url.endsWith("/") ? url.substring(0, url.length() - 1) : url) : "https://gitlab.com";
        
        String group = config.getProperty("gitlab.group");
        this.groupUrl = (group != null) ? group : "";
        
        if (userToken != null && !userToken.isBlank()) {
            this.token = userToken;
        } else {
            this.token = config.getProperty("gitlab.token");
        }
    }

    private String cleanRepoName(String repoName) {
        String clean = repoName.trim();
        if (clean.endsWith(".git")) {
            clean = clean.substring(0, clean.length() - 4);
        }
        if (clean.startsWith("http://") || clean.startsWith("https://")) {
            try {
                URI uri = URI.create(clean);
                String path = uri.getPath();
                if (path.startsWith("/")) path = path.substring(1);
                return path;
            } catch (Exception e) {
                return clean;
            }
        }
        return clean;
    }

    private HttpResponse<String> sendWithAuthFallback(HttpClient client, String url) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET();

        if (token == null || token.isBlank()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpRequest request = builder.copy().header("PRIVATE-TOKEN", token).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            request = builder.copy().header("Authorization", "Bearer " + token).build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        return response;
    }

    @Override
    public void cloneRepository(String repoName, File destination, String branch) throws Exception {
        String fullPath = cleanRepoName(repoName);
        String cloneUrl;
        
        String base = baseUrl;
        if (fullPath.contains("/")) {
            cloneUrl = (fullPath.startsWith("http") ? "" : (base.replace("api/v4", "") + "/")) + fullPath + ".git";
            // Normalize clone URL if it looks like an API URL
            cloneUrl = cloneUrl.replace("/api/v4/", "/");
        } else {
             String group = groupUrl.endsWith("/") ? groupUrl.substring(0, groupUrl.length() - 1) : groupUrl;
             if (group.isEmpty()) {
                 cloneUrl = base.replace("/api/v4", "") + "/" + fullPath + ".git";
             } else {
                 cloneUrl = (group.startsWith("http") ? group : (base.replace("/api/v4", "") + "/" + group)) + "/" + fullPath + ".git";
             }
        }
        
        // Final normalization
        if (!cloneUrl.startsWith("http")) {
            cloneUrl = "https://gitlab.com/" + fullPath + ".git";
        }

        org.eclipse.jgit.api.CloneCommand command = Git.cloneRepository()
            .setURI(cloneUrl)
            .setDirectory(destination)
            .setDepth(1) // Efficient cloning: Shallow clone
            .setCloneAllBranches(false);

        if (token != null && !token.isEmpty()) {
            command.setCredentialsProvider(new UsernamePasswordCredentialsProvider("oauth2", token));
        }
            
        if (branch != null && !branch.isBlank()) {
            command.setBranch(branch);
            command.setBranchesToClone(Collections.singletonList("refs/heads/" + branch));
        }
        
        try (Git git = command.call()) {
            // Success
        }
    }

    @Override
    public List<Map<String, String>> listRepositories(String groupName) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String url;
        if (groupName == null || groupName.isBlank()) {
             url = baseUrl + "/api/v4/projects?per_page=100&membership=true&simple=true";
        } else {
             String encodedGroup = java.net.URLEncoder.encode(groupName, java.nio.charset.StandardCharsets.UTF_8);
             url = baseUrl + "/api/v4/groups/" + encodedGroup + "/projects?include_subgroups=true&per_page=100&simple=true";
        }
        
        HttpResponse<String> response = sendWithAuthFallback(client, url);
        
        if (response.statusCode() == 200) {
            JsonNode root = mapper.readTree(response.body());
            List<Map<String, String>> repos = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    if (node.has("path_with_namespace") && node.has("http_url_to_repo")) {
                        Map<String, String> repo = new java.util.HashMap<>();
                        repo.put("name", node.get("path_with_namespace").asText());
                        repo.put("cloneUrl", node.get("http_url_to_repo").asText());
                        repos.add(repo);
                    }
                }
            }
            return repos;
        }
        return Collections.emptyList();
    }

    private String resolveProjectId(String repoName) throws Exception {
        String searchName = cleanRepoName(repoName);
        if (projectIdCache.containsKey(searchName)) return projectIdCache.get(searchName);
        
        HttpClient client = HttpClient.newHttpClient();
        String encodedPath = java.net.URLEncoder.encode(searchName, java.nio.charset.StandardCharsets.UTF_8);
        String directUrl = baseUrl + "/api/v4/projects/" + encodedPath;
        
        HttpResponse<String> response = sendWithAuthFallback(client, directUrl);
        if (response.statusCode() == 200) {
             JsonNode node = mapper.readTree(response.body());
             String id = String.valueOf(node.get("id").asInt());
             projectIdCache.put(searchName, id);
             return id;
        }
        throw new RuntimeException("GitLab Project not found: " + searchName);
    }

    @Override
    public void validateCredentials() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = sendWithAuthFallback(client, baseUrl + "/api/v4/user");
        if (response.statusCode() != 200) {
             throw new RuntimeException("Invalid credentials for GitLab. Status: " + response.statusCode());
        }
    }

    @Override
    public List<String> listBranches(String repoName) throws Exception {
        String projectId = resolveProjectId(repoName);
        String apiUrl = baseUrl + "/api/v4/projects/" + projectId + "/repository/branches?per_page=100";
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = sendWithAuthFallback(client, apiUrl);
        if (response.statusCode() == 200) {
            JsonNode root = mapper.readTree(response.body());
            List<String> branches = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    if (node.has("name")) branches.add(node.get("name").asText());
                }
            }
            return branches;
        }
        return Collections.emptyList();
    }
}
