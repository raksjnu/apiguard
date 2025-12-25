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
 * Discovers projects within a given directory structure.
 * Supports Mule and Tibco project types.
 * Recursively searches for directories containing project-specific markers.
 */
public class ProjectDiscovery {
    private static final Logger logger = LoggerFactory.getLogger(ProjectDiscovery.class);
    private static final ConfigurationManager config = ConfigurationManager.getInstance();
    
    /**
     * Find all projects of specified type within the given search path.
     * 
     * @param searchPath Root path to search for projects
     * @param projectType Type of project to search for (MULE, TIBCO_BW5, etc.)
     * @return List of discovered projects
     */
    public static List<DiscoveredProject> findProjects(Path searchPath, com.raks.raksanalyzer.domain.enums.ProjectType projectType) {
        List<DiscoveredProject> projects = new ArrayList<>();
        int maxDepth = Integer.parseInt(config.getProperty("analyzer.multi.project.max.depth", "5"));
        
        String projectTypeName = projectType != null ? projectType.name() : "MULE";
        logger.info("Searching for {} projects in: {}", projectTypeName, searchPath.toAbsolutePath());
        logger.info("Maximum search depth: {}", maxDepth);
        
        searchForProjects(searchPath, projects, 0, maxDepth, projectType);
        
        // Log all discovered projects
        logger.info("Input path: {}", searchPath.toAbsolutePath());
        if (projects.isEmpty()) {
            logger.warn("No {} projects found in: {}", projectTypeName, searchPath.toAbsolutePath());
        } else {
            for (int i = 0; i < projects.size(); i++) {
                logger.info("{} project {} path: {}", projectTypeName, i + 1, projects.get(i).getProjectPath().toAbsolutePath());
            }
        }
        
        return projects;
    }
    
    /**
     * Find all Mule projects (backward compatibility).
     */
    public static List<DiscoveredProject> findMuleProjects(Path searchPath) {
        return findProjects(searchPath, com.raks.raksanalyzer.domain.enums.ProjectType.MULE);
    }
    
    /**
     * Recursively search for projects of specified type.
     */
    private static void searchForProjects(Path directory, List<DiscoveredProject> results, int currentDepth, int maxDepth, com.raks.raksanalyzer.domain.enums.ProjectType projectType) {
        logger.info("Searching directory: {} (depth: {}/{})", directory, currentDepth, maxDepth);
        
        if (currentDepth > maxDepth) {
            logger.info("Max depth exceeded at: {}", directory);
            return;
        }
        
        try {
            // Check if current directory is a project of the specified type
            boolean isProject = false;
            if (projectType == com.raks.raksanalyzer.domain.enums.ProjectType.TIBCO_BW5 || projectType == com.raks.raksanalyzer.domain.enums.ProjectType.TIBCO_BW6) {
                isProject = FileUtils.isTibcoBW5Project(directory);
            } else {
                isProject = FileUtils.isMuleProject(directory);
            }
            
            if (isProject) {
                String projectName = directory.getFileName().toString();
                DiscoveredProject project = new DiscoveredProject(directory, projectName);
                results.add(project);
                logger.info("✓ Found {} project: {} at {}", projectType, projectName, directory);
                // Don't search subdirectories of a project
                return;
            }
            
            // Search subdirectories
            if (Files.isDirectory(directory)) {
                try (Stream<Path> paths = Files.list(directory)) {
                    List<Path> subdirs = paths.filter(Files::isDirectory).collect(java.util.stream.Collectors.toList());
                    logger.info("Found {} subdirectories in: {}", subdirs.size(), directory);
                    
                    for (Path subDir : subdirs) {
                        if (isExcludedDirectory(subDir)) {
                            logger.info("✗ Excluded directory: {}", subDir);
                        } else {
                            searchForProjects(subDir, results, currentDepth + 1, maxDepth, projectType);
                        }
                    }
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
