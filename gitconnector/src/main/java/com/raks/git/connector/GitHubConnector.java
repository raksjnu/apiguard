package com.raks.git.connector;

import java.io.File;
import java.util.List;
import java.util.Map;

public class GitHubConnector implements GitConnector {
    @Override
    public boolean validateToken(String baseUrl, String token) { return false; }
    @Override
    public List<Map<String, String>> listGroups(String baseUrl, String token) { return null; }
    @Override
    public List<Map<String, Object>> listRepositories(String baseUrl, String token, String identity, String filter) { return null; }
    @Override
    public List<Map<String, String>> listSubGroups(String baseUrl, String token, String groupId) { return null; }
    @Override
    public void cloneRepository(String repoUrl, String branch, String token, File destination) {}
    @Override
    public String createRepository(String baseUrl, String token, String name, String org, boolean isPrivate) { return null; }
    @Override
    public void pushChanges(File repoDir, String token, String branch) {}
    @Override
    public void createBranch(File repoDir, String branchName) {}
    @Override
    public String mapErrorMessage(int statusCode, String errorBody) { return ""; }
}
