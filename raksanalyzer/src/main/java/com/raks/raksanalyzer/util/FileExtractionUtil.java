package com.raks.raksanalyzer.util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
public class FileExtractionUtil {
    private static final Logger logger = LoggerFactory.getLogger(FileExtractionUtil.class);
    public static Path extractZip(Path zipPath, String uploadId) throws IOException {
        return extractZip(zipPath, uploadId, System.getProperty("user.dir"));
    }
    public static Path extractZip(Path zipPath, String uploadId, String baseTempDir) throws IOException {
        logger.info("Extracting ZIP file: {}", zipPath);
        Path tempDir = createTempDirectory(uploadId, baseTempDir);
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path targetPath = tempDir.resolve(entry.getName());
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
    public static Path extractJar(Path jarPath, String uploadId) throws IOException {
        return extractJar(jarPath, uploadId, System.getProperty("user.dir"));
    }
    public static Path extractJar(Path jarPath, String uploadId, String baseTempDir) throws IOException {
        logger.info("Extracting JAR file: {}", jarPath);
        Path tempDir = createTempDirectory(uploadId, baseTempDir);
        boolean foundMuleSrc = false;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jarPath.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("META-INF/mule-src/")) {
                    foundMuleSrc = true;
                    String relativePath = entry.getName().substring("META-INF/mule-src/".length());
                    if (relativePath.isEmpty()) {
                        continue;
                    }
                    Path targetPath = tempDir.resolve(relativePath);
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
    public static Path extractEar(Path earPath, String uploadId) throws IOException {
        return extractEar(earPath, uploadId, System.getProperty("user.dir"));
    }
    public static Path extractEar(Path earPath, String uploadId, String baseTempDir) throws IOException {
        logger.info("Extracting TIBCO EAR file: {}", earPath);
        Path tempDir = createTempDirectory(uploadId, baseTempDir);
        logger.info("Step 1: Extracting EAR archive");
        extractZipArchive(earPath, tempDir);
        logger.info("Step 2: Extracting nested .par and .sar archives");
        String earFileName = earPath.getFileName().toString();
        String projectName = earFileName.substring(0, earFileName.lastIndexOf('.'));
        Path commonFolder = tempDir.resolve(projectName);
        Files.createDirectories(commonFolder);
        extractNestedArchives(tempDir, commonFolder);
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
    private static void extractZipArchive(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path targetPath = targetDir.resolve(entry.getName());
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
    private static void extractNestedArchives(Path earRoot, Path commonFolder) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(earRoot)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    String fileName = file.getFileName().toString().toLowerCase();
                    if (fileName.endsWith(".par") || fileName.endsWith(".sar")) {
                        logger.info("Extracting nested archive: {}", fileName);
                        try {
                            extractZipArchive(file, commonFolder);
                            logger.info("Successfully extracted: {}", fileName);
                        } catch (IOException e) {
                            logger.warn("Failed to extract {}: {}", fileName, e.getMessage());
                        }
                    }
                }
            }
        }
    }
    private static Path findProjectRoot(Path extractedDir) throws IOException {
        if (Files.exists(extractedDir.resolve("pom.xml"))) {
            return extractedDir;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(extractedDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry) && Files.exists(entry.resolve("pom.xml"))) {
                    return entry;
                }
            }
        }
        logger.warn("pom.xml not found in extracted directory. Returning base path: {}", extractedDir);
        return extractedDir;
    }
    private static Path createTempDirectory(String uploadId, String baseTempDir) throws IOException {
        Path tempUploadsDir = Paths.get(baseTempDir, "temp", "uploads");
        Files.createDirectories(tempUploadsDir);
        Path uploadDir = tempUploadsDir.resolve(uploadId);
        Files.createDirectories(uploadDir);
        logger.debug("Created temp directory: {}", uploadDir);
        return uploadDir;
    }
    public static void cleanupTempDirectory(String uploadId) {
        cleanupTempDirectory(uploadId, System.getProperty("user.dir"));
    }
    public static void cleanupTempDirectory(String uploadId, String baseTempDir) {
        try {
            Path uploadDir = Paths.get(baseTempDir, "temp", "uploads", uploadId);
            if (Files.exists(uploadDir)) {
                deleteRecursively(uploadDir);
                logger.info("Cleaned up temporary directory: {}", uploadId);
            }
        } catch (IOException e) {
            logger.warn("Failed to cleanup temp directory: {}", uploadId, e);
        }
    }
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
    public static boolean isValidArchive(Path filePath) {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(filePath.toFile()))) {
            return zis.getNextEntry() != null;
        } catch (IOException e) {
            return false;
        }
    }
}
