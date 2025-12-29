package com.raks.apidiscovery.connector;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.raks.apidiscovery.ScannerEngine;
import com.raks.apidiscovery.ApiDiscoveryTool;
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

public class GitLabConnector {

    private static final String GITLAB_API_BASE = "https://gitlab.com/api/v4";
    private static final int HTTP_TIMEOUT_MS = 30000; // 30 seconds
    private static final int GIT_CLONE_TIMEOUT_SEC = 120; // 2 minutes per repo

    public List<DiscoveryReport> scanGroup(String groupPath, String token) {
        List<DiscoveryReport> allReports = new ArrayList<>();
        ScannerEngine engine = new ScannerEngine();
        
        System.out.println("[GitLab] Connecting to GitLab Group: " + groupPath);

        try {
            // 1. Fetch Projects from GitLab API
            System.out.println("[GitLab] Fetching project list from API...");
            List<GitLabProject> projects = fetchProjects(groupPath, token);
            System.out.println("[GitLab] Found " + projects.size() + " projects in group.");

            if (projects.isEmpty()) {
                System.out.println("[GitLab] No projects found in group: " + groupPath);
                DiscoveryReport emptyReport = new DiscoveryReport();
                emptyReport.setRepoName("No Projects Found");
                emptyReport.setRepoPath(groupPath);
                emptyReport.setClassification("Empty Group");
                emptyReport.setConfidenceScore(100);
                emptyReport.addEvidence("Group exists but contains no projects");
                allReports.add(emptyReport);
                return allReports;
            }

            // 2. Process each project
            int totalProjects = projects.size();
            int currentProject = 0;
            
            // Create temp directory for cloning (Use Scan_Timestamp format for History Persistence)
            String scanFolderName = "Scan_" + System.currentTimeMillis();
            File tempDir = new File("temp");
            if (!tempDir.exists()) tempDir.mkdirs();
            
            File tempCloneDir = new File(tempDir, scanFolderName);
            tempCloneDir.mkdirs();
            
            // Set the scan folder name in the Tool context so it can be returned
            ApiDiscoveryTool.setScanFolder(scanFolderName);
            
            // FIX: Set JGit User Home to temp directory to avoid permission errors on CloudHub
            // CloudHub /usr/src/app/.config is often read-only or non-existent for the user
            try {
                FS.DETECTED.setUserHome(tempCloneDir);
                System.out.println("[GitLab] Set JGit User Home to: " + tempCloneDir.getAbsolutePath());
            } catch (Exception e) {
                System.err.println("[GitLab] Failed to set JGit User Home: " + e.getMessage());
            }
            
            try {
                for (GitLabProject p : projects) {
                    currentProject++;
                    int percent = 10 + (int)((currentProject / (float)totalProjects) * 80);
                    ApiDiscoveryTool.updateProgress("Scanning " + p.name + " (" + currentProject + "/" + totalProjects + ")", percent);
                    System.out.println("[GitLab] Processing " + currentProject + "/" + totalProjects + ": " + p.name);
                    
                    try {
                        // Clone the repository
                        File repoDir = new File(tempCloneDir, p.name);
                        System.out.println("[GitLab] Cloning " + p.name + " to " + repoDir.getAbsolutePath());
                        
                        Git git = Git.cloneRepository()
                            .setURI(p.http_url_to_repo)
                            .setDirectory(repoDir)
                            .setCredentialsProvider(new UsernamePasswordCredentialsProvider("oauth2", token))
                            .setDepth(1) // Shallow clone for performance
                            .setTimeout(GIT_CLONE_TIMEOUT_SEC)
                            .call();
                        
                        git.close();
                        
                        // Scan the cloned repository
                        System.out.println("[GitLab] Scanning " + p.name);
                        DiscoveryReport report = engine.scanRepository(repoDir);
                        report.setRepoName(p.name);
                        report.setRepoPath(p.web_url);
                        report.addMetadata("gitlab_id", String.valueOf(p.id));
                        report.addMetadata("visibility", p.visibility != null ? p.visibility : "unknown");
                        
                        allReports.add(report);
                        System.out.println("[GitLab] Completed scan for: " + p.name + " - " + report.getTechnology());
                        
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
                // Cleanup cloned repositories
                // System.out.println("[GitLab] Cleaning up temp clone directory: " + tempCloneDir.getAbsolutePath());
                // deleteDirectory(tempCloneDir); // REMOVED FOR HISTORY PERSISTENCE
            }
            
            ApiDiscoveryTool.updateProgress("Finalizing results...", 95);
            System.out.println("[GitLab] Scan complete. Processed " + allReports.size() + " projects.");
            
        } catch (Exception e) {
            System.err.println("[GitLab] Fatal error during scan: " + e.getMessage());
            e.printStackTrace();
            
            // Return error report
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
        // URL encode the path (e.g. raks-group/subgroup -> raks-group%2Fsubgroup)
        String encodedPath = groupPath.replace("/", "%2F");
        String urlString = GITLAB_API_BASE + "/groups/" + encodedPath + "/projects?include_subgroups=true&per_page=100";
        
        System.out.println("[GitLab] API Request: " + urlString);
        
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("PRIVATE-TOKEN", token);
        conn.setConnectTimeout(HTTP_TIMEOUT_MS);
        conn.setReadTimeout(HTTP_TIMEOUT_MS);
        
        int responseCode = conn.getResponseCode();
        System.out.println("[GitLab] API Response Code: " + responseCode);
        
        if (responseCode != 200) {
            String errorMessage = "GitLab API Failed: " + responseCode + " " + conn.getResponseMessage();
            
            // Try to read error response
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                StringBuilder errorBody = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    errorBody.append(line);
                }
                errorMessage += " - " + errorBody.toString();
            } catch (Exception e) {
                // Ignore error reading error stream
            }
            
            throw new RuntimeException(errorMessage);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            Gson gson = new Gson();
            List<GitLabProject> projects = gson.fromJson(br, new TypeToken<List<GitLabProject>>(){}.getType());
            System.out.println("[GitLab] Successfully parsed " + (projects != null ? projects.size() : 0) + " projects");
            return projects != null ? projects : new ArrayList<>();
        }
    }

    // Inner DTO
    private static class GitLabProject {
        int id;
        String name;
        String web_url;
        String http_url_to_repo;
        String visibility;
    }
}
