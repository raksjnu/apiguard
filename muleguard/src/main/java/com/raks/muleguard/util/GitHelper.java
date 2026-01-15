package com.raks.muleguard.util;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class GitHelper {
    private static final Logger logger = LoggerFactory.getLogger(GitHelper.class);
    
    // Limits
    private static final long MAX_REPO_SIZE_BYTES = 500 * 1024 * 1024; // 500 MB
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

    static {
        // Prevent file locking issues on Windows by disabling memory mapping
        WindowCacheConfig config = new WindowCacheConfig();
        config.setPackedGitMMAP(false);
        config.install();
    }

    /**
     * Clones a Git repository to the specified destination.
     * Supports token-based authentication via URL injection or CredentialsProvider.
     * Enforces restrictions: Depth=1, No 'target' folders, No '*.jar' files, size limits.
     *
     * @param repoUrl     The URL of the repository (https).
     * @param branch      The branch to clone (optional, defaults to HEAD).
     * @param token       The authentication token (optional).
     * @param destinationPath The directory path where the repo should be cloned.
     * @throws Exception If cloning fails or limits are exceeded.
     */
    public static void cloneRepository(String repoUrl, String branch, String token, String destinationPath) throws Exception {
        Path destination = Path.of(destinationPath);
        logger.info("Cloning repository: {} (Branch: {}) to {}", repoUrl, branch, destination);

        String finalUrl = repoUrl.trim();
        CredentialsProvider credentials = null;

        if (token != null && !token.isBlank()) {
            if (finalUrl.contains("gitlab")) {
                credentials = new UsernamePasswordCredentialsProvider("oauth2", token);
            } else if (finalUrl.contains("github")) {
                 credentials = new UsernamePasswordCredentialsProvider(token, ""); 
            } else {
                 credentials = new UsernamePasswordCredentialsProvider("", token);
            }
        }

        org.eclipse.jgit.api.CloneCommand command = Git.cloneRepository()
                .setURI(finalUrl)
                .setDirectory(destination.toFile())
                .setCloneAllBranches(false)
                .setDepth(1); // Enforce shallow clone

        if (branch != null && !branch.isBlank()) {
            command.setBranch(branch);
            command.setBranchesToClone(java.util.Collections.singletonList("refs/heads/" + branch));
        }

        if (credentials != null) {
            command.setCredentialsProvider(credentials);
        }

        try (Git git = command.call()) {
            logger.info("Clone successful. Enforcing restrictions...");
            
            // Explicitly close to release file handles before we delete things
            if (git.getRepository() != null) {
                git.getRepository().close();
            }
            
            // Enforce size and file type restrictions
            enforceRestrictions(destination);
            
        } catch (GitAPIException e) {
            logger.error("Git clone failed: {}", e.getMessage());
            throw new Exception("Failed to clone repository: " + e.getMessage(), e);
        }
    }
    

    private static void enforceRestrictions(Path repoRoot) throws IOException {
        AtomicLong totalSize = new AtomicLong(0);
        List<String> warnings = new ArrayList<>();
        
        Files.walkFileTree(repoRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                // Skip .git directory
                if (dir.getFileName().toString().equals(".git")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                
                // Skip/Delete 'target' directories
                if (dir.getFileName().toString().equals("target")) {
                    String msg = "Excluded directory: " + repoRoot.relativize(dir).toString();
                    logger.info("Removing restricted directory: {}", dir);
                    warnings.add(msg);
                    deleteDirectory(dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                long size = attrs.size();
                String fileName = file.getFileName().toString();
                
                // Check restrictions
                if (fileName.endsWith(".jar")) {
                    String msg = "Excluded file (type): " + repoRoot.relativize(file).toString();
                    logger.info("Removing restricted file (extension): {}", file);
                    warnings.add(msg);
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                
                if (size > MAX_FILE_SIZE_BYTES) {
                     String msg = "Excluded file (size > 10MB): " + repoRoot.relativize(file).toString() + " (" + (size/1024/1024) + "MB)";
                    logger.warn("Removing restricted file (size > 10MB): {} ({} bytes)", file, size);
                    warnings.add(msg);
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                
                totalSize.addAndGet(size);
                
                if (totalSize.get() > MAX_REPO_SIZE_BYTES) {
                    // Fail fast? Or just warn? "limit total git folder size limit as 500 mb"
                    // If we fail, we should delete the whole repo.
                    throw new IOException("Repository size limit exceeded (Max 500MB).");
                }
                
                return FileVisitResult.CONTINUE;
            }
        });
        
        logger.info("Restriction check complete. Final Repo Size: {} bytes", totalSize.get());
        
        if (!warnings.isEmpty()) {
            writeWarnings(repoRoot, warnings);
        }
    }
    
    private static void writeWarnings(Path repoRoot, List<String> warnings) {
        try {
            Path warningFile = repoRoot.resolve(".muleguard_git_warnings");
            Files.write(warningFile, warnings);
        } catch (IOException e) {
            logger.error("Failed to write git warning file", e);
        }
    }
    
    private static void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
        }
    }

    /**
     * Lists repositories for discovery.
     */
    public static List<String> listRepositories(String provider, String token, String group, String filter, String gitlabUrl, String githubUrl) throws Exception {
        if ("github".equalsIgnoreCase(provider)) {
            return listGitHubRepos(token, group, filter, githubUrl);
        } else {
            return listGitLabRepos(token, group, filter, gitlabUrl);
        }
    }

    private static List<String> listGitLabRepos(String token, String group, String filter, String baseUrl) throws Exception {
        String cleanBase = (baseUrl == null || baseUrl.isBlank()) ? "https://gitlab.com" : (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        String encodedGroup = URLEncoder.encode(group, StandardCharsets.UTF_8);
        String apiUrl = cleanBase + "/api/v4/groups/" + encodedGroup + "/projects?include_subgroups=true&per_page=100";
        
        // If filter is simple (no commas), let GitLab handle it efficiently.
        // If complex, fetch list and filter client-side.
        boolean clientSideFilter = false;
        if (filter != null && !filter.isBlank()) {
            if (filter.contains(",")) {
                clientSideFilter = true;
            } else {
                apiUrl += "&search=" + URLEncoder.encode(filter, StandardCharsets.UTF_8);
            }
        }
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("PRIVATE-TOKEN", token)
                .GET()
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401) throw new Exception("GitLab Authentication Failed (401). Check your Token.");
        if (response.statusCode() == 404) throw new Exception("GitLab Group/Org not found (404). Check your search query.");
        if (response.statusCode() != 200) throw new Exception("GitLab API Error: " + response.statusCode());
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.body());
        List<String> repos = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode node : root) {
                if (node.has("path_with_namespace")) {
                    String repoName = node.get("path_with_namespace").asText();
                    if (clientSideFilter) {
                        for (String f : filter.split(",")) {
                            if (repoName.toLowerCase().contains(f.trim().toLowerCase())) {
                                repos.add(repoName);
                                break;
                            }
                        }
                    } else {
                        repos.add(repoName);
                    }
                }
            }
        }
        return repos;
    }

    private static List<String> listGitHubRepos(String token, String group, String filter, String baseUrl) throws Exception {
        String cleanBase = (baseUrl == null || baseUrl.isBlank()) ? "https://api.github.com" : (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        String apiUrl = cleanBase + "/orgs/" + group + "/repos?per_page=100";
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "token " + token)
                .GET()
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            // Try users
            apiUrl = cleanBase + "/users/" + group + "/repos?per_page=100";
            request = HttpRequest.newBuilder().uri(URI.create(apiUrl)).header("Authorization", "Bearer " + token).GET().build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }
        
        if (response.statusCode() == 401) throw new Exception("GitHub Authentication Failed (401). Check your Token.");
        if (response.statusCode() == 404) throw new Exception("GitHub Group/Org not found (404). Check your search query.");
        if (response.statusCode() != 200) throw new Exception("GitHub API Error: " + response.statusCode());
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.body());
        List<String> repos = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode node : root) {
                if (node.has("full_name")) {
                    String repoName = node.get("full_name").asText();
                    if (filter == null || filter.isBlank()) {
                        repos.add(repoName);
                    } else {
                         String[] terms = filter.split(",");
                         for (String term : terms) {
                             if (repoName.toLowerCase().contains(term.trim().toLowerCase())) {
                                 repos.add(repoName);
                                 break;
                             }
                         }
                    }
                }
            }
        }
        return repos;
    }

    /**
     * Lists branches for discovery.
     */
    public static List<String> listBranches(String provider, String token, String repo, String gitlabUrl, String githubUrl) throws Exception {
        if ("github".equalsIgnoreCase(provider)) {
            return listGitHubBranches(token, repo, githubUrl);
        } else {
            return listGitLabBranches(token, repo, gitlabUrl);
        }
    }

    private static List<String> listGitLabBranches(String token, String repo, String baseUrl) throws Exception {
        String cleanBase = (baseUrl == null || baseUrl.isBlank()) ? "https://gitlab.com" : (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        String repoPath = repo;
        if (repo.startsWith("http")) {
             // Handle full URL: https://gitlab.com/group/subgroup/project.git
             repoPath = repo.replaceFirst("https?://[^/]+/", "").replace(".git", "");
        }
        String encodedRepo = URLEncoder.encode(repoPath, StandardCharsets.UTF_8);
        String apiUrl = cleanBase + "/api/v4/projects/" + encodedRepo + "/repository/branches";
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("PRIVATE-TOKEN", token)
                .GET()
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401) throw new Exception("GitLab Authentication Failed (401). Check your Token.");
        if (response.statusCode() == 404) throw new Exception("GitLab Repository not found (404).");
        if (response.statusCode() != 200) throw new Exception("GitLab API Error: " + response.statusCode());
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.body());
        List<String> branches = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode node : root) {
                if (node.has("name")) branches.add(node.get("name").asText());
            }
        }
        return branches;
    }

    private static List<String> listGitHubBranches(String token, String repo, String baseUrl) throws Exception {
        String cleanBase = (baseUrl == null || baseUrl.isBlank()) ? "https://api.github.com" : (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        String repoPath = repo;
        if (repo.startsWith("http")) {
             // Handle full URL: https://github.com/owner/repo.git
             repoPath = repo.replaceFirst("https?://[^/]+/", "").replace(".git", "");
        }
        String apiUrl = cleanBase + "/repos/" + repoPath + "/branches";
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "token " + token)
                .GET()
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401) throw new Exception("GitHub Authentication Failed (401). Check your Token.");
        if (response.statusCode() == 404) throw new Exception("GitHub Repository not found (404).");
        if (response.statusCode() != 200) throw new Exception("GitHub API Error: " + response.statusCode());
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.body());
        List<String> branches = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode node : root) {
                if (node.has("name")) branches.add(node.get("name").asText());
            }
        }
        return branches;
    }
}
