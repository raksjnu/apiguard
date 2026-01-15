package com.raks.muleguard.wrapper;

import com.raks.gitanalyzer.provider.GitProvider;
import com.raks.gitanalyzer.provider.GitLabProvider;
import com.raks.gitanalyzer.provider.GitHubProvider;
import com.raks.gitanalyzer.core.ConfigManager;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class GitDiscoveryHelper {
    
    /**
     * Lists repositories for a given provider and search query (group/org/user).
     */
    public static List<String> listRepos(String provider, String token, String query, Map<String, String> config) throws Exception {
        // Initialize ConfigManager with current app settings if provided
        if (config != null) {
            ConfigManager.setAll(config);
        }
        
        GitProvider gp;
        if ("github".equalsIgnoreCase(provider)) {
            gp = new GitHubProvider(token);
        } else {
            gp = new GitLabProvider(token);
        }
        return gp.listRepositories(query);
    }

    /**
     * Lists branches for a given repository.
     */
    public static List<String> listBranches(String provider, String token, String repo, Map<String, String> config) throws Exception {
        if (config != null) {
            ConfigManager.setAll(config);
        }
        
        GitProvider gp;
        if ("github".equalsIgnoreCase(provider)) {
            gp = new GitHubProvider(token);
        } else {
            gp = new GitLabProvider(token);
        }
        return gp.listBranches(repo);
    }
}
