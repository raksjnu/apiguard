package com.raks.raksanalyzer.api.rest;

import com.raks.raksanalyzer.provider.GitProvider;
import com.raks.raksanalyzer.provider.GitHubProvider;
import com.raks.raksanalyzer.provider.GitLabProvider;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Path("/git")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GitResource {

    @POST
    @Path("/validate")
    public Response validate(Map<String, String> request) {
        try {
            String providerType = request.get("provider");
            String token = request.get("token");
            
            if (token == null || token.isBlank()) {
                 return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("status", "error", "message", "Token is required"))
                            .build();
            }

            GitProvider provider = getProvider(providerType, token);
            provider.validateCredentials();
            
            return Response.ok(Map.of("status", "success", "message", "Credentials are valid.")).build();
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity(Map.of("status", "error", "message", e.getMessage()))
                           .build();
        }
    }

    @GET
    @Path("/repos")
    public Response listRepos(@QueryParam("provider") String providerType, 
                             @QueryParam("token") String token,
                             @QueryParam("group") String group) {
        try {
            GitProvider provider = getProvider(providerType, token);
            List<Map<String, String>> repos = provider.listRepositories(group);
            return Response.ok(repos).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity(Map.of("error", e.getMessage()))
                           .build();
        }
    }

    @GET
    @Path("/branches")
    public Response listBranches(@QueryParam("provider") String providerType, 
                                @QueryParam("token") String token,
                                @QueryParam("repo") String repo) {
        try {
            GitProvider provider = getProvider(providerType, token);
            List<String> branches = provider.listBranches(repo);
            return Response.ok(branches).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity(Map.of("error", e.getMessage()))
                           .build();
        }
    }

    private GitProvider getProvider(String type, String token) {
        if ("github".equalsIgnoreCase(type)) {
            return new GitHubProvider(token);
        } else {
            return new GitLabProvider(token);
        }
    }
}
