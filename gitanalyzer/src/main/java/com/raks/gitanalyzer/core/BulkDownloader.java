package com.raks.gitanalyzer.core;

import com.raks.gitanalyzer.provider.GitProvider;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BulkDownloader {
    private final GitProvider gitProvider;
    private final String outputDir;

    public BulkDownloader(GitProvider gitProvider) {
        this.gitProvider = gitProvider;
        // Default to configured output dir, or fall back to standard temp location
        this.outputDir = ConfigManager.resolveOutputDir(null, "app.output.dir").getAbsolutePath();
    }

    public void downloadRepos(String repoList) {
        if (repoList == null || repoList.trim().isEmpty()) {
            System.out.println("No repositories specified for download.");
            return;
        }

        List<String> repos = Arrays.asList(repoList.split(","));
        File baseDir = new File(outputDir, "downloads");
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }

        ExecutorService executor = Executors.newFixedThreadPool(5); // Parallel downloads

        for (String repo : repos) {
            String repoName = repo.trim();
            if (repoName.isEmpty()) continue;

            executor.submit(() -> {
                try {
                    System.out.println("Starting download: " + repoName);
                    File repoDir = new File(baseDir, repoName);
                    gitProvider.cloneRepository(repoName, repoDir);
                    System.out.println("Completed download: " + repoName);
                } catch (Exception e) {
                    System.err.println("Failed to download " + repoName + ": " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
        
        executor.shutdown();
    }
}
