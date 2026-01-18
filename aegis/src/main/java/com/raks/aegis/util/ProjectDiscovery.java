package com.raks.aegis.util;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class ProjectDiscovery {
    private static final Logger logger = LoggerFactory.getLogger(ProjectDiscovery.class);
    public static List<Path> findProjects(
            Path searchPath,
            int maxDepth,
            List<String> markerFiles,
            String matchMode,
            String configFolderPattern,
            List<String> exactIgnoredNames,
            List<String> ignoredPrefixes) {
        List<Path> projects = new ArrayList<>();
        logger.debug("Searching for projects in: {}", searchPath.toAbsolutePath());
        logger.debug("Maximum search depth: {}", maxDepth);
        searchForProjects(searchPath, projects, 0, maxDepth,
                markerFiles, matchMode, configFolderPattern,
                exactIgnoredNames, ignoredPrefixes);
        if (projects.isEmpty()) {
            logger.info("No projects found in: {}", searchPath.toAbsolutePath());
        } else {
            logger.info("Found {} project(s)", projects.size());
            for (int i = 0; i < projects.size(); i++) {
                logger.debug("  {}. {}", (i + 1), projects.get(i).toAbsolutePath());
            }
        }
        return projects;
    }
    private static void searchForProjects(
            Path directory,
            List<Path> results,
            int currentDepth,
            int maxDepth,
            List<String> markerFiles,
            String matchMode,
            String configFolderPattern,
            List<String> exactIgnoredNames,
            List<String> ignoredPrefixes) {
        if (currentDepth > maxDepth) {
            return;
        }
        
        // Check if this directory should be ignored BEFORE checking if it's a project
        if (isIgnoredDirectory(directory, exactIgnoredNames, ignoredPrefixes)) {
            return;
        }
        
        try {
            if (isProject(directory, markerFiles, matchMode, configFolderPattern)) {
                results.add(directory);
                return;
            }
            if (Files.isDirectory(directory)) {
                try (Stream<Path> paths = Files.list(directory)) {
                    paths.filter(Files::isDirectory)
                            .filter(p -> !isIgnoredDirectory(p, exactIgnoredNames, ignoredPrefixes))
                            .forEach(subDir -> searchForProjects(subDir, results, currentDepth + 1,
                                    maxDepth, markerFiles, matchMode, configFolderPattern,
                                    exactIgnoredNames, ignoredPrefixes));
                }
            }
        } catch (IOException e) {
            logger.error("Error searching directory: {} - {}", directory, e.getMessage());
        }
    }
    private static boolean isProject(
            Path dir,
            List<String> markerFiles,
            String matchMode,
            String configFolderPattern) {
        String name = dir.getFileName().toString();
        boolean isCodeProject;
        if ("ALL".equalsIgnoreCase(matchMode)) {
            isCodeProject = markerFiles.stream()
                    .allMatch(markerFile -> Files.exists(dir.resolve(markerFile)));
        } else {
            isCodeProject = markerFiles.stream()
                    .anyMatch(markerFile -> Files.exists(dir.resolve(markerFile)));
        }
        boolean isConfigProject = name.matches(configFolderPattern);
        return isCodeProject || isConfigProject;
    }
    private static boolean isIgnoredDirectory(
            Path path,
            List<String> exactNames,
            List<String> prefixes) {
        String name = path.getFileName().toString();
        String nameLower = name.toLowerCase();
        
        // Case-insensitive exact name matching
        if (exactNames.stream().anyMatch(exactName -> exactName.equalsIgnoreCase(name))) {
            return true;
        }
        
        // Case-insensitive prefix matching
        for (String prefix : prefixes) {
            if (nameLower.startsWith(prefix.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
