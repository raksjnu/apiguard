package com.raks.raksanalyzer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/**
 * Utility class for extracting ZIP and JAR files.
 * Supports both regular ZIP files and Mule Studio exported JAR files.
 */
public class FileExtractionUtil {
    private static final Logger logger = LoggerFactory.getLogger(FileExtractionUtil.class);
    
    /**
     * Extract ZIP file to temporary directory.
     * Uses user.dir as base temp directory (for standalone usage).
     * 
     * @param zipPath Path to ZIP file
     * @param uploadId Unique upload identifier
     * @return Path to extracted project root
     * @throws IOException if extraction fails
     */
    public static Path extractZip(Path zipPath, String uploadId) throws IOException {
        return extractZip(zipPath, uploadId, System.getProperty("user.dir"));
    }
    
    /**
     * Extract ZIP file to temporary directory.
     * 
     * @param zipPath Path to ZIP file
     * @param uploadId Unique upload identifier
     * @param baseTempDir Base temporary directory (from Mule flow)
     * @return Path to extracted project root
     * @throws IOException if extraction fails
     */
    public static Path extractZip(Path zipPath, String uploadId, String baseTempDir) throws IOException {
        logger.info("Extracting ZIP file: {}", zipPath);
        Path tempDir = createTempDirectory(uploadId, baseTempDir);
        
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path targetPath = tempDir.resolve(entry.getName());
                
                // Security: Prevent path traversal attacks
                if (!targetPath.normalize().startsWith(tempDir)) {
                    throw new IOException("Invalid ZIP entry (path traversal detected): " + entry.getName());
                }
                
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
        
        Path projectRoot = findProjectRoot(tempDir);
        logger.info("ZIP extracted successfully. Project root: {}", projectRoot);
        return projectRoot;
    }
    
    /**
     * Extract JAR file and locate source code in META-INF/mule-src/.
     * Uses user.dir as base temp directory (for standalone usage).
     * 
     * @param jarPath Path to JAR file
     * @param uploadId Unique upload identifier
     * @return Path to extracted project root
     * @throws IOException if extraction fails
     */
    public static Path extractJar(Path jarPath, String uploadId) throws IOException {
        return extractJar(jarPath, uploadId, System.getProperty("user.dir"));
    }
    
    /**
     * Extract JAR file and locate source code in META-INF/mule-src/.
     * This handles Mule Studio exported JAR files which contain source code
     * nested inside META-INF/mule-src/{projectName}/.
     * 
     * @param jarPath Path to JAR file
     * @param uploadId Unique upload identifier
     * @param baseTempDir Base temporary directory (from Mule flow)
     * @return Path to extracted project root
     * @throws IOException if extraction fails
     */
    public static Path extractJar(Path jarPath, String uploadId, String baseTempDir) throws IOException {
        logger.info("Extracting JAR file: {}", jarPath);
        Path tempDir = createTempDirectory(uploadId, baseTempDir);
        
        boolean foundMuleSrc = false;
        
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jarPath.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // Only extract META-INF/mule-src/ contents (source code)
                if (entry.getName().startsWith("META-INF/mule-src/")) {
                    foundMuleSrc = true;
                    
                    // Remove META-INF/mule-src/ prefix to get relative path
                    String relativePath = entry.getName().substring("META-INF/mule-src/".length());
                    
                    if (relativePath.isEmpty()) {
                        continue;
                    }
                    
                    Path targetPath = tempDir.resolve(relativePath);
                    
                    // Security: Prevent path traversal attacks
                    if (!targetPath.normalize().startsWith(tempDir)) {
                        throw new IOException("Invalid JAR entry (path traversal detected): " + entry.getName());
                    }
                    
                    if (entry.isDirectory()) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                zis.closeEntry();
            }
        }
        
        if (!foundMuleSrc) {
            throw new IOException("Invalid Mule JAR file: No source code found in META-INF/mule-src/");
        }
        
        Path projectRoot = findProjectRoot(tempDir);
        logger.info("JAR extracted successfully. Project root: {}", projectRoot);
        return projectRoot;
    }
    
    /**
     * Extract TIBCO EAR file and nested .par and .sar archives.
     * Uses user.dir as base temp directory (for standalone usage).
     * 
     * @param earPath Path to EAR file
     * @param uploadId Unique upload identifier
     * @return Path to extracted project root (common folder with merged contents)
     * @throws IOException if extraction fails
     */
    public static Path extractEar(Path earPath, String uploadId) throws IOException {
        return extractEar(earPath, uploadId, System.getProperty("user.dir"));
    }
    
    /**
     * Extract TIBCO EAR file and nested .par and .sar archives.
     * EAR files contain Process Archives (.par) and Shared Archives (.sar)
     * which are also ZIP files that need to be extracted.
     * 
     * @param earPath Path to EAR file
     * @param uploadId Unique upload identifier
     * @param baseTempDir Base temporary directory (from Mule flow)
     * @return Path to extracted project root (common folder with merged contents)
     * @throws IOException if extraction fails
     */
    public static Path extractEar(Path earPath, String uploadId, String baseTempDir) throws IOException {
        logger.info("Extracting TIBCO EAR file: {}", earPath);
        Path tempDir = createTempDirectory(uploadId, baseTempDir);
        
        // Step 1: Extract EAR file (standard ZIP extraction)
        logger.info("Step 1: Extracting EAR archive");
        extractZipArchive(earPath, tempDir);
        
        // Step 2: Extract nested .par and .sar files to common folder
        logger.info("Step 2: Extracting nested .par and .sar archives");
        // Use original EAR filename (without extension) instead of generic "extracted"
        String earFileName = earPath.getFileName().toString();
        String projectName = earFileName.substring(0, earFileName.lastIndexOf('.'));
        Path commonFolder = tempDir.resolve(projectName);
        Files.createDirectories(commonFolder);
        
        extractNestedArchives(tempDir, commonFolder);
        
        // Step 3: Copy root TIBCO.xml to common folder (for global variables)
        Path rootTibcoXml = tempDir.resolve("TIBCO.xml");
        if (Files.exists(rootTibcoXml)) {
            Files.copy(rootTibcoXml, commonFolder.resolve("TIBCO.xml"), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Copied root TIBCO.xml to common folder");
        } else {
            logger.warn("Root TIBCO.xml not found in EAR");
        }
        
        logger.info("EAR extracted successfully. Project root: {}", commonFolder);
        return commonFolder;
    }
    
    /**
     * Extract a ZIP archive to target directory (generic helper).
     * 
     * @param zipFile Path to ZIP file
     * @param targetDir Target directory for extraction
     * @throws IOException if extraction fails
     */
    private static void extractZipArchive(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path targetPath = targetDir.resolve(entry.getName());
                
                // Security: Prevent path traversal attacks
                if (!targetPath.normalize().startsWith(targetDir)) {
                    throw new IOException("Invalid ZIP entry (path traversal detected): " + entry.getName());
                }
                
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }
    
    /**
     * Extract nested .par and .sar archives to common folder.
     * Merges contents from all archives into a single directory structure.
     * 
     * @param earRoot Root directory of extracted EAR
     * @param commonFolder Common folder to extract all archives into
     * @throws IOException if extraction fails
     */
    private static void extractNestedArchives(Path earRoot, Path commonFolder) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(earRoot)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    String fileName = file.getFileName().toString().toLowerCase();
                    
                    // Extract .par and .sar files
                    if (fileName.endsWith(".par") || fileName.endsWith(".sar")) {
                        logger.info("Extracting nested archive: {}", fileName);
                        
                        try {
                            extractZipArchive(file, commonFolder);
                            logger.info("Successfully extracted: {}", fileName);
                        } catch (IOException e) {
                            logger.warn("Failed to extract {}: {}", fileName, e.getMessage());
                            // Continue with other archives even if one fails
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Find the actual project root directory.
     * Handles cases where the project might be nested one level deep.
     * 
     * @param extractedDir Directory where files were extracted
     * @return Path to project root (directory containing pom.xml)
     * @throws IOException if project root cannot be found
     */
    private static Path findProjectRoot(Path extractedDir) throws IOException {
        // Check if current directory has pom.xml
        if (Files.exists(extractedDir.resolve("pom.xml"))) {
            return extractedDir;
        }
        
        // Check one level deep for single project folder
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(extractedDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry) && Files.exists(entry.resolve("pom.xml"))) {
                    return entry;
                }
            }
        }
        
        // If still not found, return extracted dir and let ProjectDiscovery handle it
        logger.warn("pom.xml not found in extracted directory. Returning base path: {}", extractedDir);
        return extractedDir;
    }
    
    /**
     * Create unique temporary directory for upload.
     * Uses the base temp directory provided by the caller (Mule flow).
     * 
     * @param uploadId Unique upload identifier
     * @param baseTempDir Base temporary directory path
     * @return Path to created temp directory
     * @throws IOException if directory creation fails
     */
    private static Path createTempDirectory(String uploadId, String baseTempDir) throws IOException {
        // Use provided base temp directory from Mule flow
        Path tempUploadsDir = Paths.get(baseTempDir, "temp", "uploads");
        Files.createDirectories(tempUploadsDir);
        
        Path uploadDir = tempUploadsDir.resolve(uploadId);
        Files.createDirectories(uploadDir);
        
        logger.debug("Created temp directory: {}", uploadDir);
        return uploadDir;
    }
    
    /**
     * Cleanup temporary directory and all its contents.
     * Uses user.dir as base temp directory (for standalone usage).
     * 
     * @param uploadId Unique upload identifier
     */
    public static void cleanupTempDirectory(String uploadId) {
        cleanupTempDirectory(uploadId, System.getProperty("user.dir"));
    }
    
    /**
     * Cleanup temporary directory and all its contents.
     * 
     * @param uploadId Unique upload identifier
     * @param baseTempDir Base temporary directory path
     */
    public static void cleanupTempDirectory(String uploadId, String baseTempDir) {
        try {
            Path uploadDir = Paths.get(baseTempDir, "temp", "uploads", uploadId);
            
            if (Files.exists(uploadDir)) {
                deleteRecursively(uploadDir);
                logger.info("Cleaned up temporary directory: {}", uploadId);
            }
        } catch (IOException e) {
            // Log but don't fail - cleanup is best effort
            logger.warn("Failed to cleanup temp directory: {}", uploadId, e);
        }
    }
    
    /**
     * Recursively delete a directory and all its contents.
     * 
     * @param path Directory to delete
     * @throws IOException if deletion fails
     */
    public static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path entry : stream) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.delete(path);
    }
    
    /**
     * Validate that a file is a valid archive (ZIP or JAR).
     * 
     * @param filePath Path to file
     * @return true if file is a valid ZIP/JAR archive
     */
    public static boolean isValidArchive(Path filePath) {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(filePath.toFile()))) {
            return zis.getNextEntry() != null;
        } catch (IOException e) {
            return false;
        }
    }
}
