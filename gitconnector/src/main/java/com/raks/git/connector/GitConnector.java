package com.raks.git.connector;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Universal interface for Git operations across different providers (GitHub, GitLab, etc.)
 */
public interface GitConnector {

    boolean validateToken(String baseUrl, String token) throws Exception;

    List<Map<String, String>> listGroups(String baseUrl, String token) throws Exception;

    List<Map<String, Object>> listRepositories(String baseUrl, String token, String identity, String filter) throws Exception;

    List<Map<String, String>> listSubGroups(String baseUrl, String token, String groupId) throws Exception;

    List<String> listBranches(String baseUrl, String token, String repo) throws Exception;

    void cloneRepository(String repoUrl, String branch, String token, File destination) throws Exception;

    String createRepository(String baseUrl, String token, String name, String org, boolean isPrivate) throws Exception;

    void pushChanges(File repoDir, String token, String branch) throws Exception;

    void createBranch(File repoDir, String branchName) throws Exception;

    String mapErrorMessage(int statusCode, String errorBody);
}
