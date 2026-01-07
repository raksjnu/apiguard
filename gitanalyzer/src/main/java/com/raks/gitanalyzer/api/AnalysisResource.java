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
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Collections;

@Path("/analyze")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AnalysisResource {

    @POST
    public Response analyze(Map<String, Object> request) {
        try {
            String codeRepo = (String) request.get("codeRepo");
            String configRepo = (String) request.get("configRepo");
            String sourceBranch = (String) request.get("sourceBranch");
            String targetBranch = (String) request.get("targetBranch");
            String ignoreText = (String) request.get("ignorePatterns"); 
            String contentIgnoreText = (String) request.get("contentIgnorePatterns");
            String apiName = (String) request.getOrDefault("apiName", "Unknown API");

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
            String providerType = ConfigManager.get("git.provider");
            // ... (existing logic for defaultGroup)
            
            if ("github".equalsIgnoreCase(providerType)) {
                defaultGroup = ConfigManager.get("github.owner");
            } else {
                String fullGroup = ConfigManager.get("gitlab.group");
                if(fullGroup.contains("/")) defaultGroup = fullGroup.substring(fullGroup.lastIndexOf("/") + 1);
                else defaultGroup = fullGroup;
            }

            if (codeRepo != null && !codeRepo.contains("/") && !defaultGroup.isBlank()) {
                codeRepo = defaultGroup + "/" + codeRepo;
            }
            if (configRepo != null && !configRepo.isBlank() && !configRepo.contains("/") && !defaultGroup.isBlank()) {
                configRepo = defaultGroup + "/" + configRepo;
            }

            GitProvider provider;
            if ("github".equalsIgnoreCase(providerType)) {
                provider = new GitHubProvider();
            } else {
                provider = new GitLabProvider();
            }

            String configSourceBranch = (String) request.get("configSourceBranch");
            String configTargetBranch = (String) request.get("configTargetBranch");

            AnalyzerService service = new AnalyzerService(provider);
            AnalysisResult result = service.analyze(apiName, codeRepo, configRepo, sourceBranch, targetBranch, filePatterns, contentPatterns, configSourceBranch, configTargetBranch);

            return Response.ok(result).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }
}
