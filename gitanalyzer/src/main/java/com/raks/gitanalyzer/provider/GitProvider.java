package com.raks.gitanalyzer.provider;

import java.util.List;
import java.io.File;

public interface GitProvider {
    void cloneRepository(String repoName, File destination) throws Exception;
    
    // Returns JSON string of the diff comparison
    String compareBranches(String repoName, String sourceBranch, String targetBranch) throws Exception;

    // Returns JSON string of search results
    String searchRepository(String repoName, String query) throws Exception;
}
