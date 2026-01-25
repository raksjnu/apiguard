package com.raks.gitanalyzer.core;

import com.raks.gitanalyzer.provider.GitProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchService {
    private final GitProvider gitProvider;

    public SearchService(GitProvider gitProvider) {
        this.gitProvider = gitProvider;
    }

    public static class SearchParams {
        public String token;
        public boolean caseSensitive;
        public String searchType; // "substring", "exact", "regex"
        public boolean ignoreComments;
        public int contextLines = 10;
    }

    public List<SearchResult> searchLocal(SearchParams params, File directory) {
        List<SearchResult> results = new ArrayList<>();
        if (!directory.exists() || !directory.isDirectory()) return results;

        Pattern searchPattern = compilePattern(params);

        try {
            Files.walkFileTree(directory.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!Files.isRegularFile(file)) return FileVisitResult.CONTINUE;
                    
                    try {
                        List<String> lines = Files.readAllLines(file);
                        for (int i = 0; i < lines.size(); i++) {
                            String line = lines.get(i);
                            
                            if (params.ignoreComments && isComment(line, file.toString())) {
                                continue;
                            }

                            if (searchPattern.matcher(line).find()) {
                                results.add(new SearchResult(
                                    directory.toPath().relativize(file).toString(), 
                                    i + 1, 
                                    line.trim(),
                                    extractContext(lines, i, params.contextLines),
                                    file.getAbsolutePath()
                                ));
                            }
                        }
                    } catch (Exception ignored) {}
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return results;
    }

    private Pattern compilePattern(SearchParams params) {
        String regex = params.token;
        if ("exact".equalsIgnoreCase(params.searchType)) {
            regex = "\\b" + Pattern.quote(params.token) + "\\b";
        } else if ("substring".equalsIgnoreCase(params.searchType)) {
            regex = Pattern.quote(params.token);
        }
        
        int flags = params.caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
        return Pattern.compile(regex, flags);
    }

    private boolean isComment(String line, String fileName) {
        String trimmed = line.trim();
        if (fileName.endsWith(".java") || fileName.endsWith(".js") || fileName.endsWith(".ts")) {
            return trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*");
        }
        if (fileName.endsWith(".properties") || fileName.endsWith(".yaml") || fileName.endsWith(".yml") || fileName.endsWith(".py") || fileName.endsWith(".sh")) {
            return trimmed.startsWith("#");
        }
        if (fileName.endsWith(".xml") || fileName.endsWith(".html")) {
            return trimmed.startsWith("<!--");
        }
        return false;
    }

    private List<String> extractContext(List<String> lines, int index, int contextCount) {
        int start = Math.max(0, index - contextCount);
        int end = Math.min(lines.size(), index + contextCount + 1);
        return new ArrayList<>(lines.subList(start, end));
    }

    public static class SearchResult {
        public String filePath;
        public int lineNumber;
        public String content;
        public List<String> context;
        public String absolutePath;

        public SearchResult(String filePath, int lineNumber, String content, List<String> context, String absolutePath) {
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.content = content;
            this.context = context;
            this.absolutePath = absolutePath;
        }
    }

    // Simplified for now, will enhance in next step
    public List<SearchResult> searchRemote(String token, List<String> repos) {
        List<SearchResult> results = new ArrayList<>();
        // Implementation for remote API search to be enhanced later
        return results;
    }
}
