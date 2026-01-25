package com.raks.gitanalyzer.api;

import com.raks.gitanalyzer.core.AnalyzerService;
import com.raks.gitanalyzer.core.ConfigManager;
import com.raks.gitanalyzer.model.AnalysisResult;
import com.raks.gitanalyzer.provider.GitProvider;
import com.raks.gitanalyzer.provider.GitHubProvider;
import com.raks.gitanalyzer.provider.GitLabProvider;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Collections;

@Path("/analyze")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AnalysisResource {

    @POST
    @Path("/validate")
    public Response validate(Map<String, String> request) {
        try {
            String providerType = request.get("provider");
            String token = request.get("token");
            
            GitProvider provider;
            if ("github".equalsIgnoreCase(providerType)) {
                provider = new GitHubProvider(token);
            } else {
                provider = new GitLabProvider(token);
            }
            
            provider.validateCredentials();
            return Response.ok(Map.of("status", "VALID", "message", "Credentials are valid.")).build();
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity(Map.of("status", "INVALID", "error", e.getMessage()))
                           .build();
        }
    }

    @POST
    public Response analyze(Map<String, Object> request, @Context HttpHeaders headers) {
        try {
            String codeRepo = (String) request.get("codeRepo");
            String configRepo = (String) request.get("configRepo");
            String sourceBranch = (String) request.get("sourceBranch");
            String targetBranch = (String) request.get("targetBranch");
            String ignoreText = (String) request.get("ignorePatterns"); 
            String contentIgnoreText = (String) request.get("contentIgnorePatterns");
            String apiName = (String) request.getOrDefault("apiName", "Unknown API");

            // Extract Auth Context from Headers
            String headerProvider = headers.getHeaderString("X-Git-Provider");
            String headerToken = headers.getHeaderString("X-Git-Token");

            List<String> filePatterns = Collections.emptyList();
            if (ignoreText != null && !ignoreText.isBlank()) {
                filePatterns = Arrays.asList(ignoreText.split("\\n"));
                filePatterns.replaceAll(String::trim);
            }

            List<String> contentPatterns = Collections.emptyList();
            if (contentIgnoreText != null && !contentIgnoreText.isBlank()) {
                contentPatterns = Arrays.asList(contentIgnoreText.split("\\n"));
                contentPatterns.replaceAll(String::trim);
            }

            String defaultGroup = "";
            String providerType = (headerProvider != null && !headerProvider.isBlank()) ? headerProvider : ConfigManager.get("git.provider");
            
            if ("github".equalsIgnoreCase(providerType)) {
                defaultGroup = ConfigManager.get("github.owner");
            } else {
                String fullGroup = ConfigManager.get("gitlab.group");
                if(fullGroup != null && fullGroup.contains("/")) defaultGroup = fullGroup.substring(fullGroup.lastIndexOf("/") + 1);
                else defaultGroup = fullGroup;
            }

            if (codeRepo != null && !codeRepo.contains("/") && defaultGroup != null && !defaultGroup.isBlank()) {
                codeRepo = defaultGroup + "/" + codeRepo;
            }
            if (configRepo != null && !configRepo.isBlank() && !configRepo.contains("/") && defaultGroup != null && !defaultGroup.isBlank()) {
                configRepo = defaultGroup + "/" + configRepo;
            }

            GitProvider provider;
            if ("github".equalsIgnoreCase(providerType)) {
                provider = new GitHubProvider(headerToken);
            } else {
                provider = new GitLabProvider(headerToken);
            }

            String configSourceBranch = (String) request.get("configSourceBranch");
            String configTargetBranch = (String) request.get("configTargetBranch");
            
            boolean ignoreAttributeOrder = request.containsKey("ignoreAttributeOrder") && Boolean.TRUE.equals(request.get("ignoreAttributeOrder"));

            AnalyzerService service = new AnalyzerService(provider);
            // Standalone mode: Pass empty lists for advanced filters (future enhancement if needed)
            List<String> empty = java.util.Collections.emptyList();
            AnalysisResult result = service.analyze(apiName, codeRepo, configRepo, sourceBranch, targetBranch, 
                empty, empty, empty, filePatterns, 
                contentPatterns, configSourceBranch, configTargetBranch, ignoreAttributeOrder);

            return Response.ok(result).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }
}
