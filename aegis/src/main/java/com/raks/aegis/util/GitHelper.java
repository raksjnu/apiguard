package com.raks.aegis.util;

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

    private static final long MAX_REPO_SIZE_BYTES = 500 * 1024 * 1024; 
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; 

    static {

        WindowCacheConfig config = new WindowCacheConfig();
        config.setPackedGitMMAP(false);
        config.install();
    }

    public static void cloneRepository(String repoUrl, String branch, String token, String destinationPath) throws Exception {
        Path destination = Path.of(destinationPath);
        logger.info("[GitHelper] Cloning repository: {} (Branch: {}) to {}", repoUrl, branch, destination);

        if (token != null && !token.isBlank()) {
             String masked = token.length() > 4 ? token.substring(0, 2) + "***" + token.substring(token.length()-2) : "***";
             logger.info("[GitHelper] Using Auth Token: {}", masked);
        } else {
             logger.info("[GitHelper] No Auth Token provided.");
        }

        String finalUrl = repoUrl.trim();
        CredentialsProvider credentials = null;

        if (token != null && !token.isBlank()) {
            if (finalUrl.contains("gitlab")) {
                logger.info("[GitHelper] Detected GitLab URL. Using 'oauth2' username strategy.");
                credentials = new UsernamePasswordCredentialsProvider("oauth2", token);
            } else if (finalUrl.contains("github")) {
                 logger.info("[GitHelper] Detected GitHub URL. Using token-as-username strategy.");
                 credentials = new UsernamePasswordCredentialsProvider(token, ""); 
            } else {
                 logger.info("[GitHelper] Unknown Provider. Using generic token strategy.");
                 credentials = new UsernamePasswordCredentialsProvider("", token);
            }
        }

        org.eclipse.jgit.api.CloneCommand command = Git.cloneRepository()
                .setURI(finalUrl)
                .setDirectory(destination.toFile())
                .setCloneAllBranches(false)
                .setDepth(1);

        if (branch != null && !branch.isBlank()) {
            command.setBranch(branch);
            command.setBranchesToClone(java.util.Collections.singletonList("refs/heads/" + branch));
        }

        if (credentials != null) {
            command.setCredentialsProvider(credentials);
        }

        try (Git git = command.call()) {
            logger.info("Clone successful. Enforcing restrictions...");
            if (git.getRepository() != null) {
                git.getRepository().close();
            }
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
                if (dir.getFileName().toString().equals(".git")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
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
            Path warningFile = repoRoot.resolve(".Aegis_git_warnings");
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

    public static List<String> listRepositories(String provider, String token, String group, String filter, String gitlabUrl, String githubUrl) throws Exception {
        logger.info("[GitHelper] listRepositories: Provider={}, Group={}, Filter={}", provider, group, filter);
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

        if (filter != null && !filter.isBlank() && !filter.contains(",")) {
            apiUrl += "&search=" + URLEncoder.encode(filter, StandardCharsets.UTF_8);
        }

        logger.info("[GitHelper] GitLab API Request: {}", apiUrl);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder().uri(URI.create(apiUrl));
        if (token != null && !token.isBlank()) {
            reqBuilder.header("PRIVATE-TOKEN", token);
        }

        HttpResponse<String> response = client.send(reqBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
             logger.info("[GitHelper] Group not found. Trying User lookup: {}", group);
             String userApiUrl = cleanBase + "/api/v4/users?username=" + encodedGroup;
             HttpRequest.Builder userReqBuilder = HttpRequest.newBuilder().uri(URI.create(userApiUrl));
             if (token != null && !token.isBlank()) userReqBuilder.header("PRIVATE-TOKEN", token);
             HttpResponse<String> userResp = client.send(userReqBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

             if (userResp.statusCode() == 200) {
                 ObjectMapper mapper = new ObjectMapper();
                 JsonNode userRoot = mapper.readTree(userResp.body());
                 if (userRoot.isArray() && userRoot.size() > 0) {
                     String userId = userRoot.get(0).get("id").asText();
                     String projectsUrl = cleanBase + "/api/v4/users/" + userId + "/projects?per_page=100";
                     if (filter != null && !filter.isBlank() && !filter.contains(",")) projectsUrl += "&search=" + URLEncoder.encode(filter, StandardCharsets.UTF_8);
                     HttpRequest.Builder projReqBuilder = HttpRequest.newBuilder().uri(URI.create(projectsUrl));
                     if (token != null && !token.isBlank()) projReqBuilder.header("PRIVATE-TOKEN", token);
                     response = client.send(projReqBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());
                 }
             }
        }

        if (response.statusCode() != 200) throw new Exception("GitLab API Error: " + response.statusCode());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.body());
        List<String> repos = new ArrayList<>();
        String[] filters = (filter != null && !filter.isBlank()) ? filter.toLowerCase().split(",") : null;

        if (root.isArray()) {
            for (JsonNode node : root) {
                String pathWithNamespace = node.get("path_with_namespace").asText();
                if (filters != null) {
                    for (String f : filters) {
                        if (pathWithNamespace.toLowerCase().contains(f.trim())) {
                            repos.add(pathWithNamespace);
                            break;
                        }
                    }
                } else {
                    repos.add(pathWithNamespace);
                }
            }
        }
        logger.info("[GitHelper] Found {} GitLab repos.", repos.size());
        return repos;
    }

    private static List<String> listGitHubRepos(String token, String group, String filter, String baseUrl) throws Exception {
        String cleanBase = (baseUrl == null || baseUrl.isBlank()) ? "https://api.github.com" : (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        String apiUrl = cleanBase + "/orgs/" + group + "/repos?per_page=100";

        HttpClient client = HttpClient.newHttpClient();
        
        // Try with "token" prefix first (for Personal Access Tokens)
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder().uri(URI.create(apiUrl)).GET();
        if (token != null && !token.isBlank()) reqBuilder.header("Authorization", "token " + token);

        HttpResponse<String> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        
        // If 401, try with Bearer prefix (for OAuth tokens)
        if (response.statusCode() == 401 && token != null && !token.isBlank()) {
            reqBuilder = HttpRequest.newBuilder().uri(URI.create(apiUrl)).GET();
            reqBuilder.header("Authorization", "Bearer " + token);
            response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        }

        if (response.statusCode() == 404) {
            apiUrl = cleanBase + "/users/" + group + "/repos?per_page=100";
            HttpRequest.Builder userReqBuilder = HttpRequest.newBuilder().uri(URI.create(apiUrl)).GET();
            if (token != null && !token.isBlank()) userReqBuilder.header("Authorization", "token " + token);
            response = client.send(userReqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            
            // If still 401, try Bearer for users endpoint
            if (response.statusCode() == 401 && token != null && !token.isBlank()) {
                userReqBuilder = HttpRequest.newBuilder().uri(URI.create(apiUrl)).GET();
                userReqBuilder.header("Authorization", "Bearer " + token);
                response = client.send(userReqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            }
        }

        if (response.statusCode() != 200) throw new Exception("GitHub API Error: " + response.statusCode());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.body());
        List<String> repos = new ArrayList<>();
        String[] filters = (filter != null && !filter.isBlank()) ? filter.toLowerCase().split(",") : null;

        if (root.isArray()) {
            for (JsonNode node : root) {
                String fullName = node.get("full_name").asText();
                if (filters != null) {
                    for (String f : filters) {
                        if (fullName.toLowerCase().contains(f.trim())) {
                            repos.add(fullName);
                            break;
                        }
                    }
                } else {
                    repos.add(fullName);
                }
            }
        }
        logger.info("[GitHelper] Found {} GitHub repos.", repos.size());
        return repos;
    }

    public static List<String> listBranches(String provider, String token, String repo, String gitlabUrl, String githubUrl) throws Exception {
        if ("github".equalsIgnoreCase(provider)) {
            return listGitHubBranches(token, repo, githubUrl);
        } else {
            return listGitLabBranches(token, repo, gitlabUrl);
        }
    }

    private static List<String> listGitLabBranches(String token, String repo, String baseUrl) throws Exception {
        String cleanBase = (baseUrl == null || baseUrl.isBlank()) ? "https://gitlab.com" : (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        String repoPath = repo.contains("http") ? repo.replaceFirst("https?://[^/]+/", "").replace(".git", "") : repo;
        String apiUrl = cleanBase + "/api/v4/projects/" + URLEncoder.encode(repoPath, StandardCharsets.UTF_8) + "/repository/branches";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder().uri(URI.create(apiUrl)).GET();
        if (token != null && !token.isBlank()) reqBuilder.header("PRIVATE-TOKEN", token);

        HttpResponse<String> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new Exception("GitLab Branch API Error: " + response.statusCode());

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
        String repoPath = repo.contains("http") ? repo.replaceFirst("https?://[^/]+/", "").replace(".git", "") : repo;
        String apiUrl = cleanBase + "/repos/" + repoPath + "/branches";

        HttpClient client = HttpClient.newHttpClient();
        
        // Try with "token" prefix first (for Personal Access Tokens)
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder().uri(URI.create(apiUrl)).GET();
        if (token != null && !token.isBlank()) reqBuilder.header("Authorization", "token " + token);

        HttpResponse<String> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        
        // If 401, try with Bearer prefix (for OAuth tokens)
        if (response.statusCode() == 401 && token != null && !token.isBlank()) {
            reqBuilder = HttpRequest.newBuilder().uri(URI.create(apiUrl)).GET();
            reqBuilder.header("Authorization", "Bearer " + token);
            response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        }
        
        if (response.statusCode() != 200) throw new Exception("GitHub Branch API Error: " + response.statusCode());

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
