package com.raks.gitanalyzer.provider;

import java.util.List;
import java.io.File;

public interface GitProvider {
    // Clones repository (optionally specifying branch)
    void cloneRepository(String repoName, File destination, String branch) throws Exception;
    
    // Returns JSON string of the diff comparison
    String compareBranches(String repoName, String sourceBranch, String targetBranch) throws Exception;

    // Returns JSON string of search results
    String searchRepository(String repoName, String query) throws Exception;
    
    // Lists repositories in a group/org
    List<String> listRepositories(String groupName) throws Exception;

    // Lists branches for a repository
    List<String> listBranches(String repoName) throws Exception;
}
