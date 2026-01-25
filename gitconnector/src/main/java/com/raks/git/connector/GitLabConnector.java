package com.raks.git.connector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class GitLabConnector implements GitConnector {
    private static final Gson gson = new Gson();

    @Override
    public boolean validateToken(String baseUrl, String token) throws Exception {
        String url = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "api/v4/user";
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.setHeader("PRIVATE-TOKEN", token);
            try (CloseableHttpResponse response = client.execute(request)) {
                return response.getStatusLine().getStatusCode() == 200;
            }
        }
    }

    @Override
    public List<Map<String, String>> listGroups(String baseUrl, String token) throws Exception {
        String url = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "api/v4/groups?min_access_level=30";
        String response = makeGetRequest(url, token);
        List<Map<String, Object>> groups = gson.fromJson(response, new TypeToken<List<Map<String, Object>>>(){}.getType());
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, Object> g : groups) {
            Map<String, String> map = new HashMap<>();
            map.put("name", (String) g.get("name"));
            map.put("full_path", (String) g.get("full_path"));
            map.put("id", String.valueOf(g.get("id")).replace(".0", ""));
            result.add(map);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> listRepositories(String baseUrl, String token, String identity, String filter) throws Exception {
         String url = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "api/v4/groups/" + identity + "/projects?include_subgroups=true";
         String response = makeGetRequest(url, token);
         return gson.fromJson(response, new TypeToken<List<Map<String, Object>>>(){}.getType());
    }

    @Override
    public List<Map<String, String>> listSubGroups(String baseUrl, String token, String groupId) throws Exception {
        String url = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "api/v4/groups/" + groupId + "/subgroups";
        String response = makeGetRequest(url, token);
        List<Map<String, Object>> groups = gson.fromJson(response, new TypeToken<List<Map<String, Object>>>(){}.getType());
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, Object> g : groups) {
            Map<String, String> map = new HashMap<>();
            map.put("name", (String) g.get("name"));
            map.put("full_path", (String) g.get("full_path"));
            map.put("id", String.valueOf(g.get("id")).replace(".0", ""));
            result.add(map);
        }
        return result;
    }

    @Override
    public void cloneRepository(String repoUrl, String branch, String token, File destination) throws Exception {
        CredentialsProvider cp = new UsernamePasswordCredentialsProvider("oauth2", token);
        Git.cloneRepository()
            .setURI(repoUrl)
            .setDirectory(destination)
            .setCredentialsProvider(cp)
            .setBranch(branch != null ? branch : "HEAD")
            .call()
            .close();
    }
    
    // Unused methods for now
    public List<String> listBranches(String baseUrl, String token, String repo) { return null; }
    public String createRepository(String baseUrl, String token, String name, String org, boolean isPrivate) { return null; }
    public void pushChanges(File repoDir, String token, String branch) {}
    public void createBranch(File repoDir, String branchName) {}
    public String mapErrorMessage(int statusCode, String errorBody) { return "Error " + statusCode; }

    private String makeGetRequest(String url, String token) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.setHeader("PRIVATE-TOKEN", token);
            try (CloseableHttpResponse response = client.execute(request)) {
                return EntityUtils.toString(response.getEntity());
            }
        }
    }
}
