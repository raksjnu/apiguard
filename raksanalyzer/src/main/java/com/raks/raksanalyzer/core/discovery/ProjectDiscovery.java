package com.raks.raksanalyzer.core.discovery;

import com.raks.raksanalyzer.core.config.ConfigurationManager;
import com.raks.raksanalyzer.core.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Discovers Mule projects within a given directory structure.
 * Recursively searches for directories containing both pom.xml and mule-artifact.json.
 */
public class ProjectDiscovery {
    private static final Logger logger = LoggerFactory.getLogger(ProjectDiscovery.class);
    private static final ConfigurationManager config = ConfigurationManager.getInstance();
    
    /**
     * Find all Mule projects within the given search path.
     * 
     * @param searchPath Root path to search for projects
     * @return List of discovered Mule projects
     */
    public static List<DiscoveredProject> findMuleProjects(Path searchPath) {
        List<DiscoveredProject> projects = new ArrayList<>();
        int maxDepth = Integer.parseInt(config.getProperty("analyzer.multi.project.max.depth", "5"));
        
        logger.info("Searching for Mule projects in: {}", searchPath.toAbsolutePath());
        logger.info("Maximum search depth: {}", maxDepth);
        
        searchForProjects(searchPath, projects, 0, maxDepth);
        
        // Log all discovered projects
        logger.info("Input path: {}", searchPath.toAbsolutePath());
        if (projects.isEmpty()) {
            logger.warn("No Mule projects found in: {}", searchPath.toAbsolutePath());
        } else {
            for (int i = 0; i < projects.size(); i++) {
                logger.info("Mule project {} path: {}", i + 1, projects.get(i).getProjectPath().toAbsolutePath());
            }
        }
        
        return projects;
    }
    
    /**
     * Recursively search for Mule projects.
     */
    private static void searchForProjects(Path directory, List<DiscoveredProject> results, int currentDepth, int maxDepth) {
        if (currentDepth > maxDepth) {
            return;
        }
        
        try {
            // Check if current directory is a Mule project
            if (FileUtils.isMuleProject(directory)) {
                String projectName = directory.getFileName().toString();
                DiscoveredProject project = new DiscoveredProject(directory, projectName);
                results.add(project);
                logger.debug("Found Mule project: {} at {}", projectName, directory);
                // Don't search subdirectories of a Mule project
                return;
            }
            
            // Search subdirectories
            if (Files.isDirectory(directory)) {
                try (Stream<Path> paths = Files.list(directory)) {
                    paths.filter(Files::isDirectory)
                         .filter(p -> !isExcludedDirectory(p))
                         .forEach(subDir -> searchForProjects(subDir, results, currentDepth + 1, maxDepth));
                }
            }
        } catch (IOException e) {
            logger.warn("Error searching directory: {}", directory, e);
        }
    }
    
    /**
     * Check if directory should be excluded from search.
     */
    private static boolean isExcludedDirectory(Path directory) {
        String name = directory.getFileName().toString();
        // Exclude common build/IDE directories
        return name.equals("target") || 
               name.equals(".git") || 
               name.equals(".mule") || 
               name.equals(".settings") || 
               name.equals("node_modules") ||
               name.startsWith(".");
    }
}
