package com.raks.gitanalyzer.core;

import com.raks.gitanalyzer.provider.GitProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class SearchService {
    private final GitProvider gitProvider;

    public SearchService(GitProvider gitProvider) {
        this.gitProvider = gitProvider;
    }

    public List<SearchResult> searchLocal(String token, File directory) {
        List<SearchResult> results = new ArrayList<>();
        if (!directory.exists() || !directory.isDirectory()) return results;

        try {
            Files.walkFileTree(directory.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!Files.isRegularFile(file)) return FileVisitResult.CONTINUE;
                    
                    // Skip binary or large files if needed, naive text check
                    // For now, simple read all lines (careful with memory, maybe stream)
                    try {
                        List<String> lines = Files.readAllLines(file);
                        int lineNum = 0;
                        for (String line : lines) {
                            lineNum++;
                            if (line.contains(token)) {
                                results.add(new SearchResult(
                                    directory.toPath().relativize(file).toString(), 
                                    lineNum, 
                                    line.trim()
                                ));
                            }
                        }
                    } catch (Exception ignored) {
                        // Ignore read errors (binary files etc)
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return results;
    }

    // VO for results
    public static class SearchResult {
        public String filePath;
        public int lineNumber;
        public String content;

        public SearchResult(String filePath, int lineNumber, String content) {
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.content = content;
        }
    }

    public List<SearchResult> searchRemote(String token, List<String> repos) {
        List<SearchResult> results = new ArrayList<>();
        for (String repo : repos) {
            try {
                // Returns JSON list of blobs
                String jsonResponse = gitProvider.searchRepository(repo, token);
                // Parse JSON array and add to results (Simplified for now)
                // In real impl, parse JSON using Jackson similar to AnalyzerService
                System.out.println("Remote Search Result for " + repo + ": " + jsonResponse);
            } catch (Exception e) {
                System.err.println("Remote search failed for " + repo + ": " + e.getMessage());
            }
        }
        return results;
    }
}
