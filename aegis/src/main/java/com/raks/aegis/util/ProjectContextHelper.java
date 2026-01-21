package com.raks.aegis.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Additive helper class to manage cross-project validation context.
 * This class handles pairing projects with their config siblings and
 * provides context-aware search roots and property resolution.
 */
public class ProjectContextHelper {
    private static final Logger logger = LoggerFactory.getLogger(ProjectContextHelper.class);

    /**
     * Discovers a linked config folder for a given project root.
     * Heuristic: ProjectName -> ProjectName_config or ProjectName-config
     */
    public static Optional<Path> findLinkedConfig(Path projectRoot, List<Path> discoveredProjects) {
        if (projectRoot == null) return Optional.empty();
        
        String projectName = projectRoot.getFileName().toString();
        // Skip if this is already a config folder to avoid circular or weird linking
        if (projectName.toLowerCase().contains("config")) {
            return Optional.empty();
        }

        String[] suffixes = {"_config", "-config", ".config"};
        
        for (String suffix : suffixes) {
            String targetName = projectName + suffix;
            Optional<Path> found = discoveredProjects.stream()
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(targetName))
                    .findFirst();
            
            if (found.isPresent()) {
                logger.info("🔗 Linked project discovery: {} <-> {}", projectName, targetName);
                return found;
            }
        }
        
        return Optional.empty();
    }

    /**
     * Returns the effective search roots based on the rule configuration.
     */
    public static List<Path> getEffectiveSearchRoots(Path projectRoot, Path linkedConfig, boolean includeLinked) {
        List<Path> roots = new ArrayList<>();
        if (projectRoot != null) {
            roots.add(projectRoot);
        }
        if (includeLinked && linkedConfig != null && Files.exists(linkedConfig)) {
            roots.add(linkedConfig);
        }
        return roots;
    }

    /**
     * Resolves a property value, optionally searching in the linked config if not found in primary.
     */
    public static String resolveWithFallback(String val, Path projectRoot, Path linkedConfig, boolean resolveLinked) {
        // First try primary project root
        String resolved = PropertyResolver.resolve(val, projectRoot);
        
        // If still contains placeholders and resolveLinked is enabled, try config root
        if (resolveLinked && isUnresolved(resolved) && linkedConfig != null && Files.exists(linkedConfig)) {
            logger.debug("Attempting cross-project resolution for: {} in {}", val, linkedConfig);
            resolved = PropertyResolver.resolve(val, linkedConfig);
        }
        
        return resolved;
    }

    private static boolean isUnresolved(String val) {
        return val != null && val.contains("${");
    }
}
