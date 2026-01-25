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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.awt.Desktop;

@Path("/search")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SearchResource {

    @POST
    public Response search(Map<String, Object> request) {
        try {
            String mode = (String) request.get("mode");
            String path = (String) request.get("path");
            
            SearchService.SearchParams params = new SearchService.SearchParams();
            params.token = (String) request.get("token");
            params.caseSensitive = Boolean.TRUE.equals(request.get("caseSensitive"));
            params.searchType = (String) request.get("searchType");
            params.ignoreComments = Boolean.TRUE.equals(request.get("ignoreComments"));

            String providerType = ConfigManager.get("git.provider");
            GitProvider provider = "github".equalsIgnoreCase(providerType) ? new GitHubProvider() : new GitLabProvider();

            SearchService service = new SearchService(provider);
            List<SearchService.SearchResult> serviceResults;

            if ("remote".equalsIgnoreCase(mode)) {
                List<String> repos = List.of(path.split("\\s*,\\s*"));
                serviceResults = service.searchRemote(params.token, repos); 
            } else {
                serviceResults = service.searchLocal(params, new File(path));
            }

            return Response.ok(serviceResults).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    @POST
    @Path("/open")
    public Response openFile(Map<String, String> request) {
        try {
            String path = request.get("path");
            File file = new File(path);
            if (file.exists()) {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(file);
                    return Response.ok(Map.of("status", "success")).build();
                } else {
                    return Response.status(405).entity(Map.of("error", "Desktop opening not supported on server")).build();
                }
            } else {
                return Response.status(404).entity(Map.of("error", "File not found")).build();
            }
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }
}
