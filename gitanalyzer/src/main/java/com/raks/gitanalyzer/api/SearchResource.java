package com.raks.gitanalyzer.api;

import com.raks.gitanalyzer.provider.GitProvider;
import com.raks.gitanalyzer.provider.GitHubProvider;
import com.raks.gitanalyzer.provider.GitLabProvider;
import com.raks.gitanalyzer.core.SearchService;
import com.raks.gitanalyzer.core.ConfigManager;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.awt.Desktop;

@Path("/search")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SearchResource {
    
    @GET
    @Path("/repos")
    public Response listRepos(@QueryParam("group") String groupName, @QueryParam("filter") String filterPattern, @Context HttpHeaders headers) {
        try {
            GitProvider provider = getProvider(headers);

            if (groupName == null || groupName.isBlank()) {
                if (provider instanceof GitHubProvider) {
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
            
            List<String> repos = provider.listRepositories(groupName);
            
            if (filterPattern != null && !filterPattern.isBlank()) {
                try {
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(filterPattern, java.util.regex.Pattern.CASE_INSENSITIVE);
                    repos = repos.stream()
                        .filter(r -> pattern.matcher(r).find())
                        .collect(java.util.stream.Collectors.toList());
                } catch (Exception e) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Invalid Regex Pattern: " + e.getMessage())).build();
                }
            }
            
            return Response.ok(repos).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    @POST
    public Response search(Map<String, Object> request, @Context HttpHeaders headers) {
        try {
            String mode = (String) request.get("mode");
            String path = (String) request.get("path");
            
            SearchService.SearchParams params = new SearchService.SearchParams();
            params.token = (String) request.get("token");
            params.caseSensitive = Boolean.TRUE.equals(request.get("caseSensitive"));
            params.searchType = (String) request.get("searchType");
            params.ignoreComments = Boolean.TRUE.equals(request.get("ignoreComments"));
            
            if (request.get("ignoreFolders") instanceof List) {
                params.ignoreFolders = (List<String>) request.get("ignoreFolders");
            }
            if (request.get("ignoreFiles") instanceof List) {
                params.ignoreFiles = (List<String>) request.get("ignoreFiles");
            }
            if (request.get("includeFolders") instanceof List) {
                params.includeFolders = (List<String>) request.get("includeFolders");
            }
            if (request.get("includeFiles") instanceof List) {
                params.includeFiles = (List<String>) request.get("includeFiles");
            }

            GitProvider provider = getProvider(headers);
            SearchService service = new SearchService(provider);
            List<SearchService.SearchResult> serviceResults;

            if ("remote".equalsIgnoreCase(mode)) {
                List<String> repos = List.of(path.split("\\s*,\\s*"));
                serviceResults = service.searchRemote(params, repos); 
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

    private GitProvider getProvider(HttpHeaders headers) {
        String providerType = headers.getHeaderString("X-Git-Provider");
        String headerToken = headers.getHeaderString("X-Git-Token");
        
        if (providerType == null || providerType.isBlank()) {
            providerType = ConfigManager.get("git.provider", "gitlab");
        }
        
        if ("github".equalsIgnoreCase(providerType)) {
            return new GitHubProvider(headerToken);
        } else {
            return new GitLabProvider(headerToken);
        }
    }
}
