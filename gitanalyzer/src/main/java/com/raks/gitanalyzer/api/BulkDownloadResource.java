package com.raks.gitanalyzer.api;

import com.raks.gitanalyzer.core.ConfigManager;
import com.raks.gitanalyzer.provider.GitProvider;
import com.raks.gitanalyzer.provider.GitHubProvider;
import com.raks.gitanalyzer.provider.GitLabProvider;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Path("/download")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BulkDownloadResource {

    @GET
    @Path("/list")
    public Response listRepos(@QueryParam("group") String groupName, @QueryParam("filter") String filterPattern, @Context jakarta.ws.rs.core.HttpHeaders headers) {
        try {
            GitProvider provider = getProvider(headers);

            if (groupName == null || groupName.isBlank()) {
                // Determine default group based on provider type if not provided
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
                // Sanitize input
                groupName = groupName.trim();
                while(groupName.endsWith("/")) groupName = groupName.substring(0, groupName.length() - 1);
                if (groupName.contains("/")) {
                    groupName = groupName.substring(groupName.lastIndexOf("/") + 1);
                }
            }
            
            List<String> repos = provider.listRepositories(groupName);
            
            // Apply Regex Filter if provided
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
            
            return Response.ok(Map.of("group", groupName, "repositories", repos)).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/branches")
    public Response listBranches(@QueryParam("repo") String repoName, @Context jakarta.ws.rs.core.HttpHeaders headers) {
        try {
            if (repoName == null || repoName.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "Repo name is required")).build();
            }
            GitProvider provider = getProvider(headers);
            List<String> branches = provider.listBranches(repoName);
            return Response.ok(branches).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    @POST
    public Response downloadRepos(Map<String, Object> request, @Context jakarta.ws.rs.core.HttpHeaders headers) {
        try {
            // "repos" is now a JSON array or list from the frontend
            Object reposObj = request.get("repos");
            List<String> repos = new ArrayList<>();
            if (reposObj instanceof List) {
                repos = (List<String>) reposObj;
            } else if (reposObj instanceof String) {
                // Fallback for legacy text input
                repos = Arrays.asList(((String) reposObj).split("\\n"));
            }
            repos.replaceAll(String::trim);
            repos.removeIf(String::isBlank);

            // "branches" is now an array from the frontend checklist
            Object branchesObj = request.get("branches"); 
            List<String> branches = new ArrayList<>();
             if (branchesObj instanceof List) {
                branches = (List<String>) branchesObj;
            } else if (branchesObj instanceof String) {
                 String branchesInput = (String) branchesObj;
                 if (!branchesInput.isBlank()) {
                    branches = Arrays.asList(branchesInput.split(","));
                 }
            }
            branches.replaceAll(String::trim);
            branches.removeIf(String::isBlank);
            
            // If no branch specified, use default (null) but we will try to find the actual name later if needed? 
            // Actually, for "Default" we just pass null to cloneRepository.
            if (branches.isEmpty()) {
                branches.add(null);
            }

            if (repos.isEmpty()) {
                 return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "No repositories specified")).build();
            }

            // Target Directory Logic...
            String customPath = (String) request.get("outputDir");
            File bulkDir;
            if (customPath != null && !customPath.isBlank()) {
               bulkDir = new File(customPath);
            } else {
               bulkDir = new File(ConfigManager.getTempDir(), "bulk_" + ConfigManager.getCurrentTimestamp().replace(" ", "_").replace(":", "-"));
            }
            if (!bulkDir.exists()) bulkDir.mkdirs();

            GitProvider provider = getProvider(headers);

            List<Map<String, String>> results = new ArrayList<>();
            AtomicInteger successCount = new AtomicInteger(0);
            boolean multiBranch = branches.size() > 1;

            for (String repoName : repos) {
                String baseFolderName = repoName.replace("/", "_");
                
                for (String branch : branches) {
                    Map<String, String> result = new HashMap<>();
                    result.put("repo", repoName);
                    
                    // Show actual branch name in status, or "Default" if null
                    String displayBranch = (branch == null) ? "Default" : branch;
                    result.put("branch", displayBranch);

                    try {
                        File targetDir;
                        // NEW LOGIC: OutputDir / Branch / Repo
                        String branchFolder = (branch == null) ? "default_branch" : branch.replace("/", "_");
                        // We always nest by branch now as requested
                        targetDir = new File(new File(bulkDir, branchFolder), baseFolderName);

                        // Overwrite Logic
                        boolean overwrite = Boolean.TRUE.equals(request.get("overwrite"));
                        if (targetDir.exists()) {
                            if (overwrite) {
                                deleteRecursively(targetDir);
                            } else {
                                // Check if empty, otherwise JGit throws standard exception which is caught below
                                if (targetDir.list() != null && targetDir.list().length > 0) {
                                     throw new RuntimeException("Destination path \"" + targetDir.getName() + "\" already exists and is not empty. Use overwrite option.");
                                }
                            }
                        }

                        provider.cloneRepository(repoName, targetDir, branch);
                        
                        result.put("status", "SUCCESS");
                        result.put("path", targetDir.getAbsolutePath());
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        result.put("status", "FAILURE");
                        result.put("error", e.getMessage());
                        // e.printStackTrace(); // Optional: reduce noise
                    }
                    results.add(result);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("total", repos.size() * branches.size());
            response.put("successful", successCount.get());
            response.put("downloadDir", bulkDir.getAbsolutePath());
            response.put("details", results);

            return Response.ok(response).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            for (File c : file.listFiles()) {
                deleteRecursively(c);
            }
        }
        file.delete();
    }

    private GitProvider getProvider(jakarta.ws.rs.core.HttpHeaders headers) {
        // Check Headers for override
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
