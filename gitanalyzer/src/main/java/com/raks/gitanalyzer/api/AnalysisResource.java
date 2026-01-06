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
            String apiName = (String) request.getOrDefault("apiName", "Unknown API");

            List<String> ignorePatterns = Collections.emptyList();
            if (ignoreText != null && !ignoreText.isBlank()) {
                ignorePatterns = Arrays.asList(ignoreText.split("\\n"));
                ignorePatterns.replaceAll(String::trim);
            }

            String providerType = ConfigManager.get("git.provider");
            GitProvider provider;
            if ("github".equalsIgnoreCase(providerType)) {
                provider = new GitHubProvider();
            } else {
                provider = new GitLabProvider();
            }

            AnalyzerService service = new AnalyzerService(provider);
            AnalysisResult result = service.analyze(apiName, codeRepo, configRepo, sourceBranch, targetBranch, ignorePatterns);

            return Response.ok(result).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }
}
