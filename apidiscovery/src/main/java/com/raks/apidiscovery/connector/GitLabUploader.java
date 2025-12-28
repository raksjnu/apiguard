package com.raks.apidiscovery.connector;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;

/**
 * One-time utility to verify credentials and upload test data to GitLab.
 */
public class GitLabUploader {

    private static final String GITLAB_API = "https://gitlab.com/api/v4";
    // Using the token provided by user
    private static final String TOKEN = "glpat-8xWKilsBixw-i6hMcR1FvW86MQp1OmplYXcwCw.01.120yhiotd"; 
    private static final String NAMESPACE_PATH = "raks-group"; // Target Group

    public static void main(String[] args) {
        System.out.println("Starting GitLab Upload to group: " + NAMESPACE_PATH);
        
        File testDataDir = new File("test-data/gitlab-simulated-group");
        if (!testDataDir.exists()) {
             // Fallback for running from target dir
             testDataDir = new File("../test-data/gitlab-simulated-group");
             if (!testDataDir.exists()) {
                 testDataDir = new File("C:\\raks\\apiguard\\apidiscovery\\test-data\\gitlab-simulated-group");
             }
        }
        
        if (!testDataDir.exists()) {
            System.err.println("Test data directory NOT found at " + testDataDir.getAbsolutePath());
            return;
        }

        try {
            // 1. Get Namespace ID (to ensure we create projects in the right place)
            int namespaceId = getNamespaceId(NAMESPACE_PATH);
            System.out.println("Found Namespace ID: " + namespaceId);

            for (File repoDir : testDataDir.listFiles(File::isDirectory)) {
                String repoName = repoDir.getName();
                System.out.println("\nProcessing: " + repoName);

                // 2. Create Project in GitLab
                String gitUrl = createProjectIfNotExists(repoName, namespaceId);
                
                if (gitUrl != null) {
                    // 3. Init and Push
                    pushToGitLab(repoDir, gitUrl);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int getNamespaceId(String path) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        // Search for the group
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITLAB_API + "/groups/" + path))
                .header("PRIVATE-TOKEN", TOKEN)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            // Quick and dirty regex extraction to avoid importing full JSON lib structure for one field
            String body = response.body();
            String idStr = body.replaceAll(".*\"id\":(\\d+).*", "$1");
            return Integer.parseInt(idStr);
        } else {
             // Fallback: If path is 'user/project', we might need user namespace. 
             // But let's assume valid group first.
             throw new RuntimeException("Failed to find group '" + path + "'. Status: " + response.statusCode() + " Body: " + response.body());
        }
    }

    private static String createProjectIfNotExists(String name, int namespaceId) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        
        // Check if exists first? Or just try create and catch 400.
        // Let's try create.
        String json = "{\"name\": \"" + name + "\", \"namespace_id\": " + namespaceId + ", \"visibility\": \"private\"}";
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITLAB_API + "/projects"))
                .header("PRIVATE-TOKEN", TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();

        if (response.statusCode() == 201) {
            System.out.println("Build Created project: " + name);
            // Extract http_url_to_repo
            int start = body.indexOf("\"http_url_to_repo\":\"") + 22;
            int end = body.indexOf("\"", start);
            return body.substring(start, end);
        } else if (response.statusCode() == 400 && body.contains("has already been taken")) {
             System.out.println("Project " + name + " already exists.");
             // Construct URL manually since we know the pattern
             return "https://gitlab.com/" + NAMESPACE_PATH + "/" + name + ".git";
        } else {
            System.err.println("Failed to create project: " + response.statusCode() + " " + body);
            return null;
        }
    }

    private static void pushToGitLab(File dir, String remoteUrl) {
        try {
            // Clean up existing .git if any (from previous local tests)
            deleteFolder(new File(dir, ".git"));
            
            Git git;
            if (new File(dir, ".git").exists()) {
                 git = Git.open(dir);
            } else {
                 git = Git.init().setDirectory(dir).call();
            }
            
            git.add().addFilepattern(".").call();
            git.commit().setMessage("apidiscovery demo").call();
            
            // Add Remote
            // We need to inject token into URL for JGit or use CredentialsProvider?
            // JGit with CredentialsProvider is cleaner.
            
            git.push()
                .setRemote(remoteUrl)
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider("oauth2", TOKEN))
                .setPushAll()
                .setForce(true) // Force in case of history mismatch
                .call();
                
            System.out.println("-> Pushed to " + remoteUrl);
            
        } catch (Exception e) {
            System.err.println("-> Push failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void deleteFolder(File folder) {
        if (folder.exists()) {
            File[] files = folder.listFiles();
            if(files!=null) {
                for(File f: files) {
                    if(f.isDirectory()) deleteFolder(f);
                    else f.delete();
                }
            }
            folder.delete();
        }
    }
}
