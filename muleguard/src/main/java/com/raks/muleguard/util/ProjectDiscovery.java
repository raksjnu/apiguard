package com.raks.muleguard.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Utility class for discovering Mule projects recursively within a directory tree.
 * Supports nested project structures and configurable search depth.
 * 
 * @author Rakesh Kumar (raksjnu@gmail.com)
 */
public class ProjectDiscovery {

    /**
     * Recursively find all Mule projects within the search path.
     * 
     * @param searchPath         Root path to search for projects
     * @param maxDepth           Maximum depth to search (0 = root only, 1 = immediate children, etc.)
     * @param markerFiles        List of marker files that identify a Mule project (e.g., pom.xml, mule-artifact.json)
     * @param matchMode          "ANY" (at least one marker) or "ALL" (all markers required)
     * @param configFolderPattern Regex pattern to identify config folders by name
     * @param exactIgnoredNames  List of exact folder names to ignore (e.g., target, .git)
     * @param ignoredPrefixes    List of prefixes to ignore (e.g., ".")
     * @return List of discovered Mule project paths
     */
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

    /**
     * Recursively search for Mule projects.
     * Stops searching subdirectories once a Mule project is found.
     */
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

        // Stop if max depth exceeded
        if (currentDepth > maxDepth) {
            return;
        }

        try {
            // Check if current directory is a Mule project
            if (isMuleProject(directory, markerFiles, matchMode, configFolderPattern)) {
                results.add(directory);
                // Don't search subdirectories of a Mule project
                return;
            }

            // Search subdirectories
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

    /**
     * Check if a directory is a Mule project.
     * A directory is considered a Mule project if:
     * - It matches the config folder pattern (config project), OR
     * - It contains the required marker files (code project)
     */
    private static boolean isMuleProject(
            Path dir,
            List<String> markerFiles,
            String matchMode,
            String configFolderPattern) {

        String name = dir.getFileName().toString();

        // Check if it's a code project based on marker files
        boolean isCodeProject;
        if ("ALL".equalsIgnoreCase(matchMode)) {
            // ALL mode: ALL marker files must exist (AND logic)
            isCodeProject = markerFiles.stream()
                    .allMatch(markerFile -> Files.exists(dir.resolve(markerFile)));
        } else {
            // ANY mode (default): At least ONE marker file must exist (OR logic)
            isCodeProject = markerFiles.stream()
                    .anyMatch(markerFile -> Files.exists(dir.resolve(markerFile)));
        }

        // Check if it's a config project (matches naming pattern)
        boolean isConfigProject = name.matches(configFolderPattern);

        return isCodeProject || isConfigProject;
    }

    /**
     * Check if a directory should be ignored during search.
     */
    private static boolean isIgnoredDirectory(
            Path path,
            List<String> exactNames,
            List<String> prefixes) {

        String name = path.getFileName().toString();

        // Check exact ignored folder names
        if (exactNames.contains(name)) {
            return true;
        }

        // Check ignored prefixes
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }
}
