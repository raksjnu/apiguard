package com.raks.gitanalyzer.api;

import com.raks.gitanalyzer.provider.GitProvider;
import com.raks.gitanalyzer.provider.GitHubProvider;
import com.raks.gitanalyzer.provider.GitLabProvider;
import com.raks.gitanalyzer.core.SearchService;
import com.raks.gitanalyzer.core.ConfigManager;
import com.raks.gitanalyzer.model.ResultRow;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.File;
import java.util.List;
import java.util.Map;

@Path("/search")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SearchResource {

    @POST
    public Response search(Map<String, String> request) {
        try {
            String mode = request.get("mode");
            String token = request.get("token");
            String path = request.get("path");
            String providerType = ConfigManager.get("git.provider");

            GitProvider provider;
            // Simple factory logic (expand as needed)
            if ("github".equalsIgnoreCase(providerType)) {
                provider = new GitHubProvider();
            } else {
                provider = new GitLabProvider();
            }

            SearchService service = new SearchService(provider);
            List<SearchService.SearchResult> serviceResults;

            if ("remote".equalsIgnoreCase(mode)) {
                // Parse comma-separated repos
                List<String> repos = List.of(path.split("\\s*,\\s*"));
                // Swap args: token, list
                serviceResults = service.searchRemote(token, repos); 
            } else {
                // Swap args: token, file
                serviceResults = service.searchLocal(token, new File(path));
            }

            // Map to ResultRow
            List<ResultRow> results = serviceResults.stream()
                .map(r -> new ResultRow(r.filePath, r.lineNumber, r.content))
                .collect(java.util.stream.Collectors.toList());

            return Response.ok(results).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }
}
