package com.raks.apidiscovery;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Collectors;

public class TrafficCorrelator {

    private static final long MAX_FILE_SIZE = 1024 * 1024; // 1MB limit for safety

    public static List<Map<String, Object>> correlate(String trafficData, String tempDirPath) {
        List<Map<String, Object>> allResults = new ArrayList<>();
        File tempDir = new File(tempDirPath);
        if (!tempDir.exists() || !tempDir.isDirectory()) return allResults;

        // Parse tokens (one per line)
        List<String> tokens = Arrays.stream(trafficData.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        if (tokens.isEmpty()) return allResults;

        // Find all scan folders
        File[] scanFolders = tempDir.listFiles(f -> f.isDirectory() && f.getName().toLowerCase().startsWith("scan"));
        if (scanFolders == null) return allResults;

        for (File scanFolder : scanFolders) {
            String scanId = scanFolder.getName();
            
            // Look for subdirectories (cloned repositories)
            File[] repoDirs = scanFolder.listFiles(File::isDirectory);
            if (repoDirs == null) continue;

            for (File repoDir : repoDirs) {
                String repoName = repoDir.getName();
                
                // For each token, search in this repo
                for (String token : tokens) {
                    List<String> matchingFiles = new ArrayList<>();
                    searchInDirectory(repoDir, repoDir, token, matchingFiles);
                    
                    if (!matchingFiles.isEmpty()) {
                        Map<String, Object> match = new HashMap<>();
                        match.put("traffic_item", token);
                        match.put("scan_id", scanId);
                        match.put("matched_repo", repoName);
                        match.put("confidence", "High (Code Match)");
                        match.put("files", matchingFiles);
                        allResults.add(match);
                    }
                }
            }
        }

        return allResults;
    }

    private static void searchInDirectory(File root, File current, String token, List<String> matchingFiles) {
        File[] files = current.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                if (!file.getName().startsWith(".")) {
                    searchInDirectory(root, file, token, matchingFiles);
                }
            } else {
                if (file.length() < MAX_FILE_SIZE) {
                    if (fileContainsToken(file, token)) {
                        String relativePath = root.toPath().relativize(file.toPath()).toString();
                        matchingFiles.add(relativePath); 
                    }
                }
            }
        }
    }

    private static boolean fileContainsToken(File file, String token) {
        try {
            // Simple string check is usually enough for tokens like IPs/URLs
            String content = new String(Files.readAllBytes(file.toPath()));
            return content.contains(token);
        } catch (Exception e) {
            return false;
        }
    }
}
