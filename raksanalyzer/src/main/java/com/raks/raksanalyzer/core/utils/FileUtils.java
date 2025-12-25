package com.raks.raksanalyzer.core.utils;

import com.raks.raksanalyzer.core.config.ConfigurationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * File utility methods for RaksAnalyzer.
 * 
 * Provides:
 * - ZIP extraction with edge case handling
 * - File searching and filtering
 * - Directory operations
 * - Temporary file management
 */
public class FileUtils {
    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);
    
    /**
     * Extract ZIP file to target directory.
     * Handles nested directories, special characters, and large files.
     */
    public static Path extractZip(Path zipFile, Path targetDir) throws IOException {
        logger.info("Extracting ZIP: {} to {}", zipFile, targetDir);
        
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }
        
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            int fileCount = 0;
            
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName());
                
                // Security: Prevent zip slip vulnerability
                if (!entryPath.normalize().startsWith(targetDir.normalize())) {
                    logger.warn("Skipping entry outside target directory: {}", entry.getName());
                    continue;
                }
                
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    // Create parent directories if needed
                    Files.createDirectories(entryPath.getParent());
                    
                    // Extract file
                    try (OutputStream out = new FileOutputStream(entryPath.toFile())) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            out.write(buffer, 0, len);
                        }
                    }
                    fileCount++;
                }
                
                zis.closeEntry();
            }
            
            logger.info("Extracted {} files from ZIP", fileCount);
        }
        
        return targetDir;
    }
    
    /**
     * Find files matching a pattern in a directory tree.
     */
    public static List<Path> findFiles(Path directory, String pattern) throws IOException {
        List<Path> matchingFiles = new ArrayList<>();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (matcher.matches(file.getFileName())) {
                    matchingFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                logger.warn("Failed to visit file: {}", file, exc);
                return FileVisitResult.CONTINUE;
            }
        });
        
        return matchingFiles;
    }
    
    /**
     * Find files with specific extension.
     */
    public static List<Path> findFilesByExtension(Path directory, String extension) throws IOException {
        List<Path> matchingFiles = new ArrayList<>();
        
        if (!Files.exists(directory)) {
            logger.warn("Directory does not exist: {}", directory);
            return matchingFiles;
        }
        
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().endsWith("." + extension)) {
                    matchingFiles.add(file);
                    logger.debug("Found file: {}", file);
                }
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                logger.warn("Failed to visit file: {}", file, exc);
                return FileVisitResult.CONTINUE;
            }
        });
        
        logger.info("Found {} files with extension .{} in {}", matchingFiles.size(), extension, directory);
        return matchingFiles;
    }
    
    /**
     * Delete directory recursively.
     */
    public static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
        
        logger.debug("Deleted directory: {}", directory);
    }
    
    /**
     * Create temporary directory.
     */
    public static Path createTempDirectory(String prefix) throws IOException {
        Path tempDir = Files.createTempDirectory(prefix);
        logger.debug("Created temp directory: {}", tempDir);
        return tempDir;
    }
    
    /**
     * Copy file to target location.
     */
    public static void copyFile(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        logger.debug("Copied file: {} -> {}", source, target);
    }
    
    /**
     * Read file content as string.
     */
    public static String readFileAsString(Path file) throws IOException {
        return new String(Files.readAllBytes(file));
    }
    
    /**
     * Check if path is a Mule project.
     * MUST have both pom.xml AND mule-artifact.json (AND logic).
     */
    public static boolean isMuleProject(Path directory) {
        try {
            // MUST have both files (AND logic)
            Path pomXml = directory.resolve("pom.xml");
            Path muleArtifact = directory.resolve("mule-artifact.json");
            
            if (!Files.exists(pomXml) || !Files.exists(muleArtifact)) {
                return false;
            }
            
            // Verify pom.xml contains Mule dependencies
            String content = readFileAsString(pomXml);
            return content.contains("mule-maven-plugin") || content.contains("org.mule");
            
        } catch (IOException e) {
            logger.warn("Error checking if directory is Mule project: {}", directory, e);
        }
        
        return false;
    }
    
    /**
     * Check if path is a Tibco BW5 project.
     * Checks for markers defined in framework.properties (analyzer.tibco.project.markers).
     * Default markers: AESchemas folder, vcrepo.dat file, .folder file
     */
    public static boolean isTibcoBW5Project(Path directory) {
        try {
            ConfigurationManager config = ConfigurationManager.getInstance();
            String markersProperty = config.getProperty("analyzer.tibco.project.markers", "AESchemas,vcrepo.dat,.folder");
            String[] markers = markersProperty.split(",");
            
            // Check for TIBCO.xml (EAR project marker)
        // But also require at least one other marker to avoid false positives from test folders
        boolean hasTibcoXml = Files.exists(directory.resolve("TIBCO.xml"));
        if (hasTibcoXml) {
            logger.debug("TIBCO.xml found in: {}, checking for additional markers", directory);
            
            // For EAR projects, check for .par or .sar files in current OR parent directory
            // (EAR extraction puts .par/.sar in parent, contents in subdirectory)
            try {
                // Check current directory
                try (java.util.stream.Stream<Path> files = Files.list(directory)) {
                    boolean hasArchives = files.anyMatch(f -> {
                        String name = f.getFileName().toString().toLowerCase();
                        return name.endsWith(".par") || name.endsWith(".sar");
                    });
                    
                    if (hasArchives) {
                        logger.info("✓ Detected EAR-extracted TIBCO project (TIBCO.xml + .par/.sar): {}", directory);
                        return true;
                    }
                }
                
                // Check parent directory (for EAR extraction structure)
                Path parent = directory.getParent();
                if (parent != null && Files.exists(parent)) {
                    try (java.util.stream.Stream<Path> files = Files.list(parent)) {
                        boolean hasArchives = files.anyMatch(f -> {
                            String name = f.getFileName().toString().toLowerCase();
                            return name.endsWith(".par") || name.endsWith(".sar");
                        });
                        
                        if (hasArchives) {
                            logger.info("✓ Detected EAR-extracted TIBCO project (TIBCO.xml + parent .par/.sar): {}", directory);
                            return true;
                        }
                    }
                }
            } catch (IOException e) {
                logger.warn("Error checking for .par/.sar files in: {}", directory, e);
            }
            
            // For EAR projects, require at least one additional marker (.folder or vcrepo.dat)
            for (String marker : markers) {
                marker = marker.trim();
                if (marker.equals("AESchemas")) continue; // Skip AESchemas for EAR check
                Path markerPath = directory.resolve(marker);
                if (Files.exists(markerPath)) {
                    logger.debug("TIBCO.xml + {} found (EAR project) in: {}", marker, directory);
                    return true;
                }
            }
            logger.debug("TIBCO.xml found but no additional markers - not a valid TIBCO project: {}", directory);
            return false;
        }

            // Check each marker (for non-EAR projects)
            for (String marker : markers) {
                marker = marker.trim();
                Path markerPath = directory.resolve(marker);
                
                // Check if marker exists (can be file or directory)
                if (!Files.exists(markerPath)) {
                    logger.debug("Tibco marker not found: {} in {}", marker, directory);
                    return false;
                }
            }
            
            // All markers found
            logger.debug("All Tibco BW5 markers found in: {}", directory);
            return true;
            
        } catch (Exception e) {
            logger.warn("Error checking if directory is Tibco BW5 project: {}", directory, e);
        }
        
        return false;
    }
}
