package com.raks.muleguard.util;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
public class ProjectDiscovery {
    public static List<Path> findMuleProjects(
            Path searchPath,
            int maxDepth,
            List<String> markerFiles,
            String matchMode,
            String configFolderPattern,
            List<String> exactIgnoredNames,
            List<String> ignoredPrefixes) {
        List<Path> projects = new ArrayList<>();
        System.out.println("Searching for Mule projects in: " + searchPath.toAbsolutePath());
        System.out.println("Maximum search depth: " + maxDepth);
        searchForProjects(searchPath, projects, 0, maxDepth,
                markerFiles, matchMode, configFolderPattern,
                exactIgnoredNames, ignoredPrefixes);
        if (projects.isEmpty()) {
            System.out.println("No Mule projects found in: " + searchPath.toAbsolutePath());
        } else {
            System.out.println("Found " + projects.size() + " Mule project(s):");
            for (int i = 0; i < projects.size(); i++) {
                System.out.println("  " + (i + 1) + ". " + projects.get(i).toAbsolutePath());
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
        try {
            if (isMuleProject(directory, markerFiles, matchMode, configFolderPattern)) {
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
            System.err.println("Error searching directory: " + directory + " - " + e.getMessage());
        }
    }
    private static boolean isMuleProject(
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
        if (exactNames.contains(name)) {
            return true;
        }
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
