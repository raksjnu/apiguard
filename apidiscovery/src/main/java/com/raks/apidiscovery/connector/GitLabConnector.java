package com.raks.apidiscovery.connector;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.raks.apidiscovery.ScannerEngine;
import com.raks.apidiscovery.model.DiscoveryReport;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FS;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
public class GitLabConnector {
    private static final String GITLAB_API_BASE = "https://gitlab.com/api/v4";
    private static final int HTTP_TIMEOUT_MS = 30000; 
    private static final int GIT_CLONE_TIMEOUT_SEC = 120; 
    static {
        try {
            org.eclipse.jgit.storage.file.WindowCacheConfig config = new org.eclipse.jgit.storage.file.WindowCacheConfig();
            config.setPackedGitMMAP(false);
            config.install();
        } catch (Exception e) {
            System.err.println("[GitLab] Failed to configure JGit: " + e.getMessage());
        }
    }
    public List<DiscoveryReport> scanGroup(String groupPath, String token, String scanFolderName, File parentTempDir, BiConsumer<String, Integer> progressCallback) {
        List<DiscoveryReport> allReports = new ArrayList<>();
        ScannerEngine engine = new ScannerEngine();
        try {
            List<GitLabProject> projects = fetchProjects(groupPath, token);
            if (projects.isEmpty()) {
                DiscoveryReport emptyReport = new DiscoveryReport();
                emptyReport.setRepoName("No Projects Found");
                emptyReport.setRepoPath(groupPath);
                emptyReport.setClassification("Empty Group");
                emptyReport.setConfidenceScore(100);
                emptyReport.addEvidence("Group exists but contains no projects");
                allReports.add(emptyReport);
                return allReports;
            }
            int totalProjects = projects.size();
            int currentProject = 0;
            File tempDir = (parentTempDir != null) ? parentTempDir : new File("temp");
            if (!tempDir.exists()) tempDir.mkdirs();
            File tempCloneDir = new File(tempDir, scanFolderName);
            if (tempCloneDir.mkdirs()) {
            } else {
            }
            try {
                if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                    FS.DETECTED.setUserHome(tempDir);
                } else {
                }
            } catch (Exception e) {
                System.err.println("[GitLab] Failed to set JGit User Home: " + e.getMessage());
            }
            try {
                for (GitLabProject p : projects) {
                    currentProject++;
                    int percent = 10 + (int)((currentProject / (float)totalProjects) * 80);
                    if (progressCallback != null) {
                        progressCallback.accept("Scanning " + p.name + " (" + currentProject + "/" + totalProjects + ")", percent);
                    }
                    try {
                        File repoDir = new File(tempCloneDir, p.name);
                        try (Git git = Git.cloneRepository()
                            .setURI(p.http_url_to_repo)
                            .setDirectory(repoDir)
                            .setCredentialsProvider(new UsernamePasswordCredentialsProvider("oauth2", token))
                            .setDepth(1) 
                            .setTimeout(GIT_CLONE_TIMEOUT_SEC)
                            .call()) {
                            git.getRepository().getConfig().setInt("core", null, "repositoryCacheExpireAfter", 0);
                            git.getRepository().getConfig().save();
                            git.getRepository().close();
                        }
                        System.gc();
                        try { Thread.sleep(500); } catch (InterruptedException e) {}
                        File gitDir = new File(repoDir, ".git");
                        if (gitDir.exists()) {
                           deleteDirectory(gitDir);
                        }
                        DiscoveryReport report = engine.scanRepository(repoDir);
                        report.setRepoName(p.name);
                        report.setRepoPath(p.web_url);
                        report.addMetadata("gitlab_id", String.valueOf(p.id));
                        report.addMetadata("visibility", p.visibility != null ? p.visibility : "unknown");
                        allReports.add(report);
                    } catch (Exception e) {
                        System.err.println("[GitLab] Failed to clone/scan " + p.name + ": " + e.getMessage());
                        DiscoveryReport errorReport = new DiscoveryReport();
                        errorReport.setRepoName(p.name);
                        errorReport.setRepoPath(p.web_url); 
                        errorReport.setClassification("Scan Failed");
                        errorReport.setConfidenceScore(0);
                        errorReport.addEvidence("Error: " + e.getMessage());
                        errorReport.addMetadata("gitlab_id", String.valueOf(p.id));
                        errorReport.addMetadata("visibility", p.visibility != null ? p.visibility : "unknown");
                        allReports.add(errorReport);
                    }
                }
            } finally {
            }
            if (progressCallback != null) {
                progressCallback.accept("Finalizing results...", 95);
            }
            System.out.println("[GitLab] Scan complete. Processed " + allReports.size() + " projects.");
        } catch (Exception e) {
            System.err.println("[GitLab] Fatal error during scan: " + e.getMessage());
            e.printStackTrace();
            DiscoveryReport errorReport = new DiscoveryReport();
            errorReport.setRepoName("GitLab Scan Error");
            errorReport.setRepoPath(groupPath);
            errorReport.setClassification("Scan Failed");
            errorReport.setConfidenceScore(0);
            errorReport.addEvidence("Error: " + e.getMessage());
            allReports.add(errorReport);
        }
        return allReports;
    }
    private List<GitLabProject> fetchProjects(String groupPath, String token) throws Exception {
        String encodedPath = groupPath.replace("/", "%2F");
        String urlString = GITLAB_API_BASE + "/groups/" + encodedPath + "/projects?include_subgroups=true&per_page=100";
        
        try {
            return invokeGitLabApi(urlString, token);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("404")) {
                // Fallback: Try identifying as a User Namespace
                System.out.println("[GitLab] Group not found. Checking if '" + groupPath + "' is a user...");
                try {
                    Integer userId = fetchUserId(groupPath, token);
                    if (userId != null) {
                         System.out.println("[GitLab] Found User ID: " + userId + ". Scanning user projects...");
                         String userProjectsUrl = GITLAB_API_BASE + "/users/" + userId + "/projects?per_page=100";
                         return invokeGitLabApi(userProjectsUrl, token);
                    }
                } catch (Exception ex) {
                    System.err.println("[GitLab] User lookup failed: " + ex.getMessage());
                }
            }
            throw e; // Rethrow original if fallback fails
        }
    }

    private Integer fetchUserId(String username, String token) throws Exception {
        String urlString = GITLAB_API_BASE + "/users?username=" + username;
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("PRIVATE-TOKEN", token);
        conn.setConnectTimeout(HTTP_TIMEOUT_MS);
        conn.setReadTimeout(HTTP_TIMEOUT_MS);
        
        if (conn.getResponseCode() == 200) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                Gson gson = new Gson();
                List<GitLabUser> users = gson.fromJson(br, new TypeToken<List<GitLabUser>>(){}.getType());
                if (users != null && !users.isEmpty()) {
                    return users.get(0).id;
                }
            }
        }
        return null;
    }

    private List<GitLabProject> invokeGitLabApi(String urlString, String token) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("PRIVATE-TOKEN", token);
        conn.setConnectTimeout(HTTP_TIMEOUT_MS);
        conn.setReadTimeout(HTTP_TIMEOUT_MS);
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            String errorMessage = "GitLab API Failed: " + responseCode + " " + conn.getResponseMessage();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                StringBuilder errorBody = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    errorBody.append(line);
                }
                errorMessage += " - " + errorBody.toString();
            } catch (Exception e) {
            }
            throw new RuntimeException(errorMessage);
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            Gson gson = new Gson();
            List<GitLabProject> projects = gson.fromJson(br, new TypeToken<List<GitLabProject>>(){}.getType());
            return projects != null ? projects : new ArrayList<>();
        }
    }

    private static class GitLabUser {
        int id;
        String username;
    }
    private void deleteDirectory(File file) {
        if (!file.exists()) return;
        if (!file.canWrite()) {
            file.setWritable(true);
        }
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteDirectory(f);
                }
            }
        }
        if (!file.delete()) {
            System.gc();
            try { Thread.sleep(100); } catch (Exception e) {}
            if (!file.delete()) {
            }
        }
    }
    private static class GitLabProject {
        int id;
        String name;
        String web_url;
        String http_url_to_repo;
        String visibility;
    }
}
