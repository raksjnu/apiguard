package com.raks.gitanalyzer.provider;

import com.raks.gitanalyzer.core.ConfigManager;
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
import java.util.HashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

public class GitHubProvider implements GitProvider {
    
    private final String baseUrl;
    private final String token;
    private final String owner;
    private final ObjectMapper mapper = new ObjectMapper();

    public GitHubProvider() {
        this(null);
    }

    public GitHubProvider(String userToken) {
        String url = ConfigManager.get("github.url");
        this.baseUrl = (url != null && !url.isBlank()) ? url : "https://api.github.com";
        this.owner = ConfigManager.get("github.owner");
        
        if (userToken != null && !userToken.isBlank()) {
            this.token = userToken;
        } else {
             this.token = ConfigManager.get("github.token");
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
                .header("Accept", "application/vnd.github.v3+json")
                .GET();

        if (token == null || token.isBlank()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpRequest request = builder.copy().header("Authorization", "token " + token).build();
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
        
        if (fullPath.contains("/")) {
            cloneUrl = "https://github.com/" + fullPath + ".git";
        } else {
            cloneUrl = "https://github.com/" + owner + "/" + fullPath + ".git";
        }

        org.eclipse.jgit.api.CloneCommand command = Git.cloneRepository()
            .setURI(cloneUrl)
            .setDirectory(destination);

        if (token != null && !token.isEmpty()) {
            command.setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""));
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
    public List<String> listBranches(String repoName) throws Exception {
        String fullPath = cleanRepoName(repoName);
        String apiUrl = baseUrl + "/repos/" + fullPath + "/branches";
        
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = sendWithAuthFallback(client, apiUrl);
        
        if (response.statusCode() == 200) {
            JsonNode root = mapper.readTree(response.body());
            List<String> branches = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
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
    public void validateCredentials() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = sendWithAuthFallback(client, baseUrl + "/user");
        if (response.statusCode() != 200) {
             throw new RuntimeException("Invalid credentials for GitHub. Status: " + response.statusCode());
        }
    }

    @Override
    public List<String> listRepositories(String groupName) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        List<String> allRepos = new ArrayList<>();
        
        String url = (groupName == null || groupName.isBlank()) ? (baseUrl + "/user/repos?per_page=100") : (baseUrl + "/orgs/" + groupName + "/repos?per_page=100");
        HttpResponse<String> response = sendWithAuthFallback(client, url);
        
        if (response.statusCode() == 404 && groupName != null) {
            response = sendWithAuthFallback(client, baseUrl + "/users/" + groupName + "/repos?per_page=100");
        }

        if (response.statusCode() == 200) {
            JsonNode root = mapper.readTree(response.body());
            if (root.isArray()) {
                for (JsonNode node : root) {
                    if (node.has("full_name")) allRepos.add(node.get("full_name").asText());
                }
            }
            return allRepos;
        }
        return Collections.emptyList();
    }

    @Override
    public String compareBranches(String repoName, String sourceBranch, String targetBranch) throws Exception {
        String fullPath = cleanRepoName(repoName);
        String apiUrl = baseUrl + "/repos/" + fullPath + "/compare/" + sourceBranch + "..." + targetBranch;
        
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = sendWithAuthFallback(client, apiUrl);
        
        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new RuntimeException("Failed to compare branches. Status: " + response.statusCode());
        }
    }

    @Override
    public String searchRepository(String repoName, String query) throws Exception {
        String fullPath = cleanRepoName(repoName);
        String q = java.net.URLEncoder.encode(query + " repo:" + fullPath, java.nio.charset.StandardCharsets.UTF_8);
        String apiUrl = baseUrl + "/search/code?q=" + q;

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = sendWithAuthFallback(client, apiUrl);
        
        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new RuntimeException("Failed to search repository. Status: " + response.statusCode());
        }
    }
}
