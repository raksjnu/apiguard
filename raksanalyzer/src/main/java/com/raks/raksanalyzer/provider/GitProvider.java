package com.raks.raksanalyzer.provider;

import java.util.List;
import java.io.File;

public interface GitProvider {
    // Clones repository (optionally specifying branch)
    void cloneRepository(String repoName, File destination, String branch) throws Exception;
    
    // Lists repositories in a group/org (returns list of maps with 'name' and 'cloneUrl')
    java.util.List<java.util.Map<String, String>> listRepositories(String groupName) throws Exception;

    // Lists branches for a repository
    List<String> listBranches(String repoName) throws Exception;

    // Validates credentials
    void validateCredentials() throws Exception;
}
