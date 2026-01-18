package com.raks.aegis.util;

import com.raks.aegis.model.DetectionCriteria;
import com.raks.aegis.model.ProjectTypeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Classifies projects into types (CODE, CONFIG, API, etc.) based on detection criteria.
 * Classification happens once per project and results are cached for efficiency.
 */
public class ProjectTypeClassifier {
    private static final Logger logger = LoggerFactory.getLogger(ProjectTypeClassifier.class);
    
    private final Map<String, ProjectTypeDefinition> projectTypes;
    private final Map<Path, Set<String>> classificationCache = new HashMap<>();

    public ProjectTypeClassifier(Map<String, ProjectTypeDefinition> projectTypes) {
        this.projectTypes = projectTypes != null ? projectTypes : new HashMap<>();
    }

    /**
     * Classifies a project and returns all matching project types.
     * Results are cached for efficiency.
     * 
     * @param projectPath Path to the project directory
     * @return Set of project type names (e.g., ["CODE", "API"])
     */
    public Set<String> classifyProject(Path projectPath) {
        // Check cache first
        if (classificationCache.containsKey(projectPath)) {
            return classificationCache.get(projectPath);
        }

        Set<String> matchedTypes = new HashSet<>();
        
        // If no project types defined, return empty set (all rules apply)
        if (projectTypes.isEmpty()) {
            classificationCache.put(projectPath, matchedTypes);
            return matchedTypes;
        }

        // Check each project type definition
        for (Map.Entry<String, ProjectTypeDefinition> entry : projectTypes.entrySet()) {
            String typeName = entry.getKey();
            ProjectTypeDefinition typeDef = entry.getValue();
            
            if (matchesCriteria(projectPath, typeDef.getDetectionCriteria())) {
                matchedTypes.add(typeName);
                logger.debug("Project {} matched type: {}", projectPath.getFileName(), typeName);
            }
        }

        // Cache the result
        classificationCache.put(projectPath, matchedTypes);
        
        if (matchedTypes.isEmpty()) {
            logger.debug("Project {} did not match any defined types", projectPath.getFileName());
        }
        
        return matchedTypes;
    }

    /**
     * Checks if a project matches the given detection criteria.
     */
    private boolean matchesCriteria(Path projectPath, DetectionCriteria criteria) {
        if (criteria == null) {
            return false;
        }

        String projectName = projectPath.getFileName().toString();
        String logic = criteria.getLogic(); // "AND" or "OR" (default: OR)
        boolean isAndLogic = "AND".equalsIgnoreCase(logic);
        
        List<Boolean> results = new ArrayList<>();

        // Check 1: namePattern (regex match on folder name)
        if (criteria.getNamePattern() != null && !criteria.getNamePattern().isEmpty()) {
            boolean matches = projectName.matches(criteria.getNamePattern());
            results.add(matches);
            logger.trace("namePattern '{}' match for {}: {}", criteria.getNamePattern(), projectName, matches);
        }

        // Check 2: nameContains (substring match on folder name)
        if (criteria.getNameContains() != null && !criteria.getNameContains().isEmpty()) {
            boolean matches = criteria.getNameContains().stream()
                .anyMatch(substring -> projectName.toLowerCase().contains(substring.toLowerCase()));
            results.add(matches);
            logger.trace("nameContains match for {}: {}", projectName, matches);
        }

        // Check 3: markerFiles (file existence check)
        if (criteria.getMarkerFiles() != null && !criteria.getMarkerFiles().isEmpty()) {
            boolean matches = hasAnyMarkerFile(projectPath, criteria.getMarkerFiles());
            results.add(matches);
            logger.trace("markerFiles match for {}: {}", projectName, matches);
        }

        // Check 4: excludePatterns (exclusion check)
        if (criteria.getExcludePatterns() != null && !criteria.getExcludePatterns().isEmpty()) {
            boolean excluded = criteria.getExcludePatterns().stream()
                .anyMatch(projectName::matches);
            if (excluded) {
                logger.trace("Project {} excluded by pattern", projectName);
                return false; // Immediate exclusion
            }
        }

        // Apply logic (AND or OR)
        if (results.isEmpty()) {
            return false; // No criteria specified
        }

        if (isAndLogic) {
            return results.stream().allMatch(Boolean::booleanValue);
        } else {
            return results.stream().anyMatch(Boolean::booleanValue);
        }
    }

    /**
     * Checks if any of the marker files exist in the project.
     * Supports glob patterns (e.g., "**\/*.properties")
     */
    private boolean hasAnyMarkerFile(Path projectPath, List<String> markerFiles) {
        for (String markerFile : markerFiles) {
            if (markerFile.contains("*")) {
                // Glob pattern - search recursively
                if (hasMatchingFile(projectPath, markerFile)) {
                    return true;
                }
            } else {
                // Simple file name - direct check
                if (Files.exists(projectPath.resolve(markerFile))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if any file matching the glob pattern exists.
     */
    private boolean hasMatchingFile(Path projectPath, String globPattern) {
        try (Stream<Path> paths = Files.walk(projectPath, 5)) { // Max depth 5 for performance
            return paths.anyMatch(path -> {
                String relativePath = projectPath.relativize(path).toString().replace("\\", "/");
                return matchesGlob(relativePath, globPattern);
            });
        } catch (IOException e) {
            logger.warn("Error searching for pattern {} in {}: {}", globPattern, projectPath, e.getMessage());
            return false;
        }
    }

    /**
     * Simple glob pattern matching.
     * Supports: **\/*.ext, *.ext, path/file.ext
     */
    private boolean matchesGlob(String path, String pattern) {
        // Convert glob to regex
        String regex = pattern
            .replace(".", "\\.")
            .replace("**", ".*")
            .replace("*", "[^/]*");
        return path.matches(regex);
    }

    /**
     * Clears the classification cache.
     * Useful for testing or when project structure changes.
     */
    public void clearCache() {
        classificationCache.clear();
    }
}
