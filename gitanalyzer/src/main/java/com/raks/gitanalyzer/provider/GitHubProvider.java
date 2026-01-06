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
        this.baseUrl = ConfigManager.get("github.url");
        this.token = ConfigManager.get("github.token");
        this.owner = ConfigManager.get("github.owner");
    }

    @Override
    public void cloneRepository(String repoName, File destination) throws Exception {
        // GitHub Clone URL: https://github.com/owner/repo.git
        // If private, authentication is needed via token injection or cred helper
        String cloneUrl = "https://github.com/" + owner + "/" + repoName + ".git";
        Git.cloneRepository()
            .setURI(cloneUrl)
            .setDirectory(destination)
            // .setCredentialsProvider(...) // if needed
            .call();
    }

    @Override
    public String compareBranches(String repoName, String sourceBranch, String targetBranch) throws Exception {
        // GitHub API: GET /repos/{owner}/{repo}/compare/{base}...{head}
        String apiUrl = baseUrl + "/repos/" + owner + "/" + repoName + "/compare/" + targetBranch + "..." + sourceBranch;
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
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
                .header("Authorization", "Bearer " + token)
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
