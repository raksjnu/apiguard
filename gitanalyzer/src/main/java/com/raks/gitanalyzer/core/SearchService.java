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
        public List<String> ignoreFolders = new ArrayList<>();
        public List<String> ignoreFiles = new ArrayList<>();
        public List<String> includeFolders = new ArrayList<>();
        public List<String> includeFiles = new ArrayList<>();
    }

    public List<SearchResult> searchLocal(SearchParams params, File directory) {
        List<SearchResult> results = new ArrayList<>();
        if (!directory.exists() || !directory.isDirectory()) return results;

        Pattern searchPattern = compilePattern(params);
        List<Pattern> folderIgnorePatterns = compileAntPatterns(params.ignoreFolders);
        List<Pattern> fileIgnorePatterns = compileAntPatterns(params.ignoreFiles);
        List<Pattern> folderIncludePatterns = compileAntPatterns(params.includeFolders);
        List<Pattern> fileIncludePatterns = compileAntPatterns(params.includeFiles);

        try {
            Files.walkFileTree(directory.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    String relDir = directory.toPath().relativize(dir).toString().replace("\\", "/");
                    if (relDir.isEmpty()) return FileVisitResult.CONTINUE;

                    // Skip if specifically ignored
                    if (matchesAny(relDir, folderIgnorePatterns)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    // If includes are specified, and this dir doesn't match an include (or isn't a parent of one), 
                    // we could skip. But simple logic: filter at file level is safer unless we do complex path matching.
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!Files.isRegularFile(file)) return FileVisitResult.CONTINUE;
                    
                    String relFile = directory.toPath().relativize(file).toString().replace("\\", "/");
                    String fileName = file.getFileName().toString();

                    // Exclusion Logic
                    if (matchesAny(fileName, fileIgnorePatterns) || matchesAny(relFile, fileIgnorePatterns)) {
                        return FileVisitResult.CONTINUE;
                    }

                    // Inclusion Logic (if filters are set)
                    if (!params.includeFolders.isEmpty()) {
                        String parentRelPath = directory.toPath().relativize(file.getParent()).toString();
                        if (!matchesAny(parentRelPath, folderIncludePatterns)) {
                            return FileVisitResult.CONTINUE;
                        }
                    }
                    if (!params.includeFiles.isEmpty()) {
                        if (!matchesAny(fileName, fileIncludePatterns) && !matchesAny(relFile, fileIncludePatterns)) {
                            return FileVisitResult.CONTINUE;
                        }
                    }

                    try {
                        List<String> lines = Files.readAllLines(file);
                        for (int i = 0; i < lines.size(); i++) {
                            String line = lines.get(i);
                            
                            if (params.ignoreComments && isComment(line, file.toString())) {
                                continue;
                            }

                            if (searchPattern.matcher(line).find()) {
                                results.add(new SearchResult(
                                    relFile, 
                                    i + 1, 
                                    line.trim(),
                                    extractContext(lines, i, params.contextLines),
                                    file.toAbsolutePath().toString()
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

    private List<Pattern> compileAntPatterns(List<String> patterns) {
        List<Pattern> compiled = new ArrayList<>();
        if (patterns == null) return compiled;
        for (String p : patterns) {
            if (p == null || p.isBlank()) continue;
            // Simple glob-to-regex conversion: * -> .*, ? -> .
            String regex = p.trim()
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
            compiled.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
        }
        return compiled;
    }

    private boolean matchesAny(String text, List<Pattern> patterns) {
        if (text == null || patterns == null || patterns.isEmpty()) return false;
        String normalized = text.replace("\\", "/");
        for (Pattern p : patterns) {
            // Check if the pattern matches a full path segment
            // e.g. "test" should match "src/test" or "test/foo" but maybe not "atest" (depending on intent)
            // For now, let's stick to simple find() but print debug if it matches
            if (p.matcher(normalized).find()) {
                // System.out.println("DEBUG: Ignored '" + normalized + "' due to pattern '" + p.pattern() + "'");
                return true;
            }
        }
        return false;
    }

    private List<String> extractContext(List<String> lines, int matchIndex, int contextSize) {
        int start = Math.max(0, matchIndex - contextSize);
        int end = Math.min(lines.size(), matchIndex + contextSize + 1);
        return new ArrayList<>(lines.subList(start, end));
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
    public List<SearchResult> searchRemote(SearchParams params, List<String> repos) {
        List<SearchResult> results = new ArrayList<>();
        File remoteTemp = new File(ConfigManager.getTempDir(), "remote_search_" + System.currentTimeMillis());
        if (!remoteTemp.exists()) remoteTemp.mkdirs();

        try {
            for (String repo : repos) {
                if (repo == null || repo.trim().isEmpty()) {
                    System.err.println("[SearchService] Skipping empty or null repository URL.");
                    continue;
                }
                
                String cleanRepo = repo.trim();
                File repoDir = new File(remoteTemp, cleanRepo.replace("/", "_").replace(":", "_")); // Sanitize for file system
                
                try {
                    System.out.println("[SearchService] Cloning remote repo: " + cleanRepo + " to " + repoDir.getAbsolutePath());
                    gitProvider.cloneRepository(cleanRepo, repoDir, null); // Clone default branch
                    
                    if (repoDir.exists() && repoDir.list() != null && repoDir.list().length > 0) {
                        System.out.println("[SearchService] Successfully cloned. Searching in server clone of: " + repoDir.getName());
                        List<SearchResult> repoResults = searchLocal(params, repoDir);
                        for (SearchResult r : repoResults) {
                            r.filePath = cleanRepo + "/" + r.filePath;
                        }
                        results.addAll(repoResults);
                    } else {
                        System.err.println("[SearchService] Clone target directory is empty or missing: " + cleanRepo);
                    }
                } catch (Exception e) {
                    System.err.println("[SearchService] Failed to clone/search repo: " + cleanRepo + " - " + e.getMessage());
                    // Continue with next repo
                }
            }
        } finally {
            // Cleanup: Optional, maybe keep for a few minutes? 
            // For now, let's delete to save space
            deleteDirectory(remoteTemp);
        }
        return results;
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectory(f);
                else f.delete();
            }
        }
        dir.delete();
    }
}
