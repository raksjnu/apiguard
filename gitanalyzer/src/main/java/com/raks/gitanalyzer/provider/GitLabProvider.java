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
        this.baseUrl = ConfigManager.get("gitlab.url");
        this.token = ConfigManager.get("gitlab.token");
        this.groupUrl = ConfigManager.get("gitlab.group");
    }

    @Override
    public void cloneRepository(String repoName, File destination) throws Exception {
        String cloneUrl = groupUrl + "/" + repoName + ".git";
        // Inject token into URL for auth if needed, or rely on credential helper
        // specific auth logic to be added
        Git.cloneRepository()
            .setURI(cloneUrl)
            .setDirectory(destination)
            .call();
    }

    @Override
    public String compareBranches(String repoName, String sourceBranch, String targetBranch) throws Exception {
        // Construct URI: /api/v4/projects/:id/repository/compare?from=source&to=target
        // Note: Project ID is usually integer, but GitLab allows URL-encoded path too (e.g. group%2Frepo)
        String encodedPath = java.net.URLEncoder.encode(repoName, java.nio.charset.StandardCharsets.UTF_8);
        String apiUrl = baseUrl + "/api/v4/projects/" + encodedPath + "/repository/compare?from=" + sourceBranch + "&to=" + targetBranch;

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
        // GET /projects/:id/search?scope=blobs&search=query
        String encodedPath = java.net.URLEncoder.encode(repoName, java.nio.charset.StandardCharsets.UTF_8);
        String encodedQuery = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        String apiUrl = baseUrl + "/api/v4/projects/" + encodedPath + "/search?scope=blobs&search=" + encodedQuery;

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
