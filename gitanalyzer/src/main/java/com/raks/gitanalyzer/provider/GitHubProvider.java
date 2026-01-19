package com.raks.gitanalyzer.provider;

import com.raks.gitanalyzer.core.ConfigManager;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.eclipse.jgit.api.Git;

public class GitHubProvider implements GitProvider {
    
    private final String baseUrl;
    private final String token;
    private final String owner;

    public GitHubProvider() {
        this(null);
    }

    public GitHubProvider(String userToken) {
        this.baseUrl = ConfigManager.get("github.url");
        this.owner = ConfigManager.get("github.owner");
        
        if (userToken != null && !userToken.isBlank()) {
            this.token = userToken;
        } else {
             this.token = ConfigManager.get("github.token");
        }
    }

    @Override
    public void cloneRepository(String repoName, File destination, String branch) throws Exception {
        String cloneUrl;
        if (repoName.contains("/")) {
            // "owner/repo" specified
            cloneUrl = "https://github.com/" + repoName + ".git";
        } else {
            // default to config owner
            cloneUrl = "https://github.com/" + owner + "/" + repoName + ".git";
        }

        if (token != null && !token.isEmpty()) {
            // GitHub Token Auth in URL: https://TOKEN@github.com/...
            cloneUrl = cloneUrl.replace("https://", "https://" + token + "@");
        }

        org.eclipse.jgit.api.CloneCommand command = Git.cloneRepository()
            .setURI(cloneUrl)
            .setDirectory(destination);

        if (branch != null && !branch.isBlank()) {
            command.setBranch(branch);
            command.setBranchesToClone(java.util.Collections.singletonList("refs/heads/" + branch));
        }


        try (Git git = command.call()) {
            // Explicitly close repository to release all file handles
            // This is critical on Windows to allow directory deletion
            if (git != null && git.getRepository() != null) {
                git.getRepository().close();
            }
        }
    }

    @Override
    public java.util.List<String> listBranches(String repoName) throws Exception {
        // GET /repos/:owner/:repo/branches
        String apiUrl = baseUrl + "/repos/" + repoName + "/branches";
        
        HttpClient client = HttpClient.newHttpClient();
        
        // Try with "token" prefix first (for Personal Access Tokens - PATs)
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "token " + token)
                .GET()
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        // If 401 Unauthorized, try with "Bearer" prefix (for OAuth tokens)
        if (response.statusCode() == 401) {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }
        
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
    public java.util.List<String> listRepositories(String groupName) throws Exception {
        // GitHub API: GET /orgs/:org/repos or /users/:user/repos
        // We'll start with Orgs. 
        // URL: /orgs/:org/repos?per_page=100
        
        String apiUrl = baseUrl + "/orgs/" + groupName + "/repos?per_page=100";
        
        HttpClient client = HttpClient.newHttpClient();
        
        // Try with "token" prefix first (for Personal Access Tokens)
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Accept", "application/vnd.github.v3+json")
                .GET();
        
        if (token != null && !token.isBlank()) {
            requestBuilder.header("Authorization", "token " + token);
        }
        
        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        // If 401, try with Bearer
        if (response.statusCode() == 401 && token != null && !token.isBlank()) {
            requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .GET();
            response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        }
        
        // If 404, try Users
        if (response.statusCode() == 404) {
             apiUrl = baseUrl + "/users/" + groupName + "/repos?per_page=100";
             requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Accept", "application/vnd.github.v3+json")
                .GET();
             
             if (token != null && !token.isBlank()) {
                 requestBuilder.header("Authorization", "token " + token);
             }
             
             response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
             
             // If still 401, try Bearer for users endpoint
             if (response.statusCode() == 401 && token != null && !token.isBlank()) {
                 requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github.v3+json")
                        .GET();
                 response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
             }
        }

        if (response.statusCode() == 200) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.body());
            
            java.util.List<String> content = new java.util.ArrayList<>();
            if (root.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : root) {
                    if (node.has("full_name")) {
                        content.add(node.get("full_name").asText());
                    }
                }
            }
            return content;
        } else {
             throw new RuntimeException("Failed to fetch repositories. Status: " + response.statusCode());
        }
    }

    @Override
    public String compareBranches(String repoName, String sourceBranch, String targetBranch) throws Exception {
        // GitHub API: GET /repos/{owner}/{repo}/compare/{base}...{head}
        // repoName can be "owner/repo" format, need to parse it
        String ownerName = this.owner;
        String repoOnly = repoName;
        
        if (repoName.contains("/")) {
            String[] parts = repoName.split("/", 2);
            ownerName = parts[0];
            repoOnly = parts[1];
        }
        
        String apiUrl = baseUrl + "/repos/" + ownerName + "/" + repoOnly + "/compare/" + targetBranch + "..." + sourceBranch;
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Accept", "application/vnd.github.v3+json")
                .GET();
        
        if (token != null && !token.isBlank()) {
            requestBuilder.header("Authorization", "token " + token);
        }
        
        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        // If 401, try with Bearer
        if (response.statusCode() == 401 && token != null && !token.isBlank()) {
            requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .GET();
            response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        }
        
        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new RuntimeException("Failed to compare branches. Status: " + response.statusCode());
        }
    }

    @Override
    public String searchRepository(String repoName, String query) throws Exception {
        // GitHub Search Code API: GET /search/code?q={query}+repo:{owner}/{repo}
        String q = java.net.URLEncoder.encode(query + " repo:" + owner + "/" + repoName, java.nio.charset.StandardCharsets.UTF_8);
        String apiUrl = baseUrl + "/search/code?q=" + q;

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "token " + token)
                .header("Accept", "application/vnd.github.v3+json")
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
