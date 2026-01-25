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

    private static final Map<String, DownloadStatus> activeTasks = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(4);

    @GET
    @Path("/list")
    public Response listRepos(@QueryParam("group") String groupName, @QueryParam("filter") String filterPattern, @Context jakarta.ws.rs.core.HttpHeaders headers) {
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
        String taskId = UUID.randomUUID().toString();
        DownloadStatus status = new DownloadStatus();
        
        try {
            Object reposObj = request.get("repos");
            List<String> repos = new ArrayList<>();
            if (reposObj instanceof List) {
                repos = (List<String>) reposObj;
            } else if (reposObj instanceof String) {
                repos = Arrays.asList(((String) reposObj).split("\\n"));
            }
            repos.replaceAll(String::trim);
            repos.removeIf(String::isBlank);

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
            if (branches.isEmpty()) branches.add(null);

            if (repos.isEmpty()) {
                 return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "No repositories specified")).build();
            }

            status.setTotal(repos.size() * branches.size());
            
            final List<String> reposFinal = repos;
            final List<String> branchesFinal = branches;

            String customPath = (String) request.get("outputDir");
            File bulkDir;
            if (customPath != null && !customPath.isBlank()) {
               bulkDir = new File(customPath);
            } else {
               bulkDir = new File(ConfigManager.getTempDir(), "bulk_" + ConfigManager.getCurrentTimestamp().replace(" ", "_").replace(":", "-"));
            }
            if (!bulkDir.exists()) bulkDir.mkdirs();
            status.setOutputDir(bulkDir.getAbsolutePath());

            String providerType = headers.getHeaderString("X-Git-Provider");
            String headerToken = headers.getHeaderString("X-Git-Token");

            activeTasks.put(taskId, status);

            executor.submit(() -> {
                try {
                    GitProvider provider;
                    if ("github".equalsIgnoreCase(providerType)) {
                        provider = new GitHubProvider(headerToken);
                    } else {
                        provider = new GitLabProvider(headerToken);
                    }

                    int successfulCount = 0;
                    for (String repoName : reposFinal) {
                        String baseFolderName = repoName.replace("/", "_");
                        for (String branch : branchesFinal) {
                            status.setCurrentRepo(repoName + (branch == null ? "" : " [" + branch + "]"));
                            
                            Map<String, String> result = new HashMap<>();
                            result.put("repo", repoName);
                            result.put("branch", (branch == null) ? "Default" : branch);

                            try {
                                String branchFolder = (branch == null) ? "default_branch" : branch.replace("/", "_");
                                File targetDir = new File(new File(bulkDir, branchFolder), baseFolderName);

                                Object ovr = request.get("overwrite");
                                boolean overwrite = Boolean.TRUE.equals(ovr) || "true".equalsIgnoreCase(String.valueOf(ovr));
                                
                                if (targetDir.exists()) {
                                    if (overwrite) {
                                        deleteRecursively(targetDir);
                                    } else if (targetDir.list() != null && targetDir.list().length > 0) {
                                             throw new RuntimeException("Destination path already exists and is not empty.");
                                    }
                                }

                                provider.cloneRepository(repoName, targetDir, branch);
                                
                                result.put("status", "SUCCESS");
                                result.put("path", targetDir.getAbsolutePath());
                                successfulCount++;
                            } catch (Exception e) {
                                result.put("status", "FAILURE");
                                result.put("error", e.getMessage());
                            }
                            status.addDetail(result);
                            status.setSuccess(successfulCount);
                        }
                    }
                } catch (Exception e) {
                    status.setError(e.getMessage());
                    e.printStackTrace();
                } finally {
                    status.setFinished(true);
                    status.setCurrentRepo("Finished");
                }
            });

            return Response.ok(Map.of("taskId", taskId)).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/status/{taskId}")
    public Response getDownloadStatus(@PathParam("taskId") String taskId) {
        DownloadStatus status = activeTasks.get(taskId);
        if (status == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(status).build();
    }

    private void deleteRecursively(File file) throws java.io.IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File c : children) {
                    deleteRecursively(c);
                }
            }
        }
        file.delete();
    }

    private GitProvider getProvider(jakarta.ws.rs.core.HttpHeaders headers) {
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
