package com.raks.gitanalyzer.api;

import com.raks.gitanalyzer.provider.GitProvider;
import com.raks.gitanalyzer.provider.GitHubProvider;
import com.raks.gitanalyzer.provider.GitLabProvider;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

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

            GitProvider provider;
            if ("github".equalsIgnoreCase(providerType)) {
                provider = new GitHubProvider(token);
            } else {
                provider = new GitLabProvider(token);
            }
            
            provider.validateCredentials();
            return Response.ok(Map.of("status", "success", "message", "Credentials are valid.")).build();
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity(Map.of("status", "error", "message", e.getMessage()))
                           .build();
        }
    }
}
