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
public class GitLabUploader {
    private static final String GITLAB_API = "https://gitlab.com/api/v4";
    private static final String TOKEN = "glpat-8xWKilsBixw-i6hMcR1FvW86MQp1OmplYXcwCw.01.120yhiotd"; 
    private static final String NAMESPACE_PATH = "raks-group"; 
    public static void main(String[] args) {
        System.out.println("Starting GitLab Upload to group: " + NAMESPACE_PATH);
        File testDataDir = new File("test-data/gitlab-simulated-group");
        if (!testDataDir.exists()) {
             testDataDir = new File("../test-data/gitlab-simulated-group");
             if (!testDataDir.exists()) {
                 testDataDir = new File(System.getProperty("java.io.tmpdir"), "gitlab-simulated-group");
             }
        }
        if (!testDataDir.exists()) {
            System.err.println("Test data directory NOT found at " + testDataDir.getAbsolutePath());
            return;
        }
        try {
            int namespaceId = getNamespaceId(NAMESPACE_PATH);
            System.out.println("Found Namespace ID: " + namespaceId);
            for (File repoDir : testDataDir.listFiles(File::isDirectory)) {
                String repoName = repoDir.getName();
                System.out.println("\nProcessing: " + repoName);
                String gitUrl = createProjectIfNotExists(repoName, namespaceId);
                if (gitUrl != null) {
                    pushToGitLab(repoDir, gitUrl);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static int getNamespaceId(String path) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITLAB_API + "/groups/" + path))
                .header("PRIVATE-TOKEN", TOKEN)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            String body = response.body();
            String idStr = body.replaceAll(".*\"id\":(\\d+).*", "$1");
            return Integer.parseInt(idStr);
        } else {
             throw new RuntimeException("Failed to find group '" + path + "'. Status: " + response.statusCode() + " Body: " + response.body());
        }
    }
    private static String createProjectIfNotExists(String name, int namespaceId) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
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
            int start = body.indexOf("\"http_url_to_repo\":\"") + 22;
            int end = body.indexOf("\"", start);
            return body.substring(start, end);
        } else if (response.statusCode() == 400 && body.contains("has already been taken")) {
             System.out.println("Project " + name + " already exists.");
             return "https://gitlab.com/" + NAMESPACE_PATH + "/" + name + ".git";
        } else {
            System.err.println("Failed to create project: " + response.statusCode() + " " + body);
            return null;
        }
    }
    private static void pushToGitLab(File dir, String remoteUrl) {
        try {
            deleteFolder(new File(dir, ".git"));
            Git git;
            if (new File(dir, ".git").exists()) {
                 git = Git.open(dir);
            } else {
                 git = Git.init().setDirectory(dir).call();
            }
            git.add().addFilepattern(".").call();
            git.commit().setMessage("apidiscovery demo").call();
            git.push()
                .setRemote(remoteUrl)
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider("oauth2", TOKEN))
                .setPushAll()
                .setForce(true) 
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
