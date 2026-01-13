package com.raks.apidiscovery;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class GitService {

    private static final String API_V4_GROUPS = "/api/v4/groups";
    private static final String API_V4_PROJECTS = "/api/v4/groups/%s/projects";
    private static final String API_V4_FILE = "/api/v4/projects/%s/repository/files/%s/raw?ref=%s";

    public static String fetchGroups(String baseUrl, String token) throws Exception {
        String url = baseUrl + API_V4_GROUPS + "?simple=true&per_page=100";
        return makeRequest(url, token);
    }

    public static String fetchSubGroups(String baseUrl, String token, String groupId) throws Exception {
        String url = baseUrl + String.format(API_V4_GROUPS + "/%s/subgroups", groupId) + "?simple=true&per_page=100";
        return makeRequest(url, token);
    }

    public static String fetchProjects(String baseUrl, String token, String groupId) throws Exception {
        String url = baseUrl + String.format(API_V4_PROJECTS, groupId) + "?simple=true&per_page=100&include_subgroups=true";
        return makeRequest(url, token);
    }

    // Fetch raw content of a specific file for Metadata Scanning
    public static String fetchFileContent(String baseUrl, String token, String projectId, String filePath, String ref) {
        try {
            String encodedPath = URLEncoder.encode(filePath, StandardCharsets.UTF_8.toString());
            String url = baseUrl + String.format(API_V4_FILE, projectId, encodedPath, ref);
            return makeRequest(url, token);
        } catch (Exception e) {
            // File not found or error, return null to indicate missing metadata
            return null;
        }
    }

    private static String makeRequest(String urlStr, String token) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        if (token != null && !token.isEmpty()) {
            conn.setRequestProperty("PRIVATE-TOKEN", token);
        }
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 404) {
            throw new Exception("Resource not found: " + urlStr);
        } else if (responseCode >= 400) {
            throw new Exception("GitLab API Error: " + responseCode);
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();
        conn.disconnect();
        return content.toString();
    }
}
