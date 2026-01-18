package com.raks.aegis.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

public class ArchiveExtractor {
    private static final Logger logger = LoggerFactory.getLogger(ArchiveExtractor.class);

    public static String extractZip(Path zipPath, String sessionId, String baseTempDir) throws IOException {
        logger.info("Extracting ZIP file: {}", zipPath);
        Path tempDir = createTempDirectory(sessionId, baseTempDir);

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
                    if (targetPath.getParent() != null) {
                        Files.createDirectories(targetPath.getParent());
                    }
                    Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }

        // Return the extraction directory - let ProjectDiscovery find all projects within it
        logger.info("ZIP extracted successfully to: {}", tempDir);
        logger.info("ProjectDiscovery will scan for all projects in this directory");
        return tempDir.toString();
    }

    public static String extractZip(String zipPath, String sessionId, String baseTempDir) throws IOException {
        return extractZip(Paths.get(zipPath), sessionId, baseTempDir);
    }

    public static String extractZip(InputStream zipStream, String sessionId, String baseTempDir) throws IOException {
        logger.info("Extracting ZIP from stream for session: {}", sessionId);

        Path tempZipFile = Files.createTempFile("Aegis-upload-", ".zip");
        try {
            Files.copy(zipStream, tempZipFile, StandardCopyOption.REPLACE_EXISTING);
            return extractZip(tempZipFile, sessionId, baseTempDir);
        } finally {

            try {
                Files.deleteIfExists(tempZipFile);
            } catch (IOException e) {
                logger.warn("Failed to delete temporary ZIP file: {}", tempZipFile, e);
            }
        }
    }

    public static String extractJar(Path jarPath, String sessionId, String baseTempDir) throws IOException {
        logger.info("Extracting JAR file: {}", jarPath);
        Path tempDir = createTempDirectory(sessionId, baseTempDir);
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
                        if (targetPath.getParent() != null) {
                            Files.createDirectories(targetPath.getParent());
                        }
                        Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                zis.closeEntry();
            }
        }

        if (!foundMuleSrc) {
            throw new IOException("Invalid Mule JAR file: No source code found in META-INF/mule-src/");
        }

        // Return the extraction directory - let ProjectDiscovery find all projects within it
        logger.info("JAR extracted successfully to: {}", tempDir);
        logger.info("ProjectDiscovery will scan for all projects in this directory");
        return tempDir.toString();
    }

    public static String extractJar(String jarPath, String sessionId, String baseTempDir) throws IOException {
        return extractJar(Paths.get(jarPath), sessionId, baseTempDir);
    }

    public static String extractJar(InputStream jarStream, String sessionId, String baseTempDir) throws IOException {
        logger.info("Extracting JAR from stream for session: {}", sessionId);

        Path tempJarFile = Files.createTempFile("Aegis-upload-", ".jar");
        try {
            Files.copy(jarStream, tempJarFile, StandardCopyOption.REPLACE_EXISTING);
            return extractJar(tempJarFile, sessionId, baseTempDir);
        } finally {

            try {
                Files.deleteIfExists(tempJarFile);
            } catch (IOException e) {
                logger.warn("Failed to delete temporary JAR file: {}", tempJarFile, e);
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

    private static Path createTempDirectory(String sessionId, String baseTempDir) throws IOException {
        Path sessionDir = Paths.get(baseTempDir, sessionId);
        if (!Files.exists(sessionDir)) {
             try {
                 Files.createDirectories(sessionDir);
             } catch (FileAlreadyExistsException e) {

             }
        }

        logger.debug("Created temp directory: {}", sessionDir);
        return sessionDir;
    }

    public static void cleanupSession(String sessionId, String baseTempDir) {
        try {
            Path sessionDir = Paths.get(baseTempDir, sessionId);
            if (Files.exists(sessionDir)) {
                deleteRecursively(sessionDir);
                logger.info("Cleaned up session directory: {}", sessionId);
            }
        } catch (IOException e) {
            logger.warn("Failed to cleanup session directory: {}", sessionId, e);
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

    public static Path zipDirectory(Path sourceDir, Path targetZip) throws IOException {
        logger.info("Compressing directory: {} to {}", sourceDir, targetZip);

        if (!Files.exists(sourceDir)) {
            throw new FileNotFoundException("Source directory not found: " + sourceDir);
        }

        if (targetZip.getParent() != null) {
            Files.createDirectories(targetZip.getParent());
        }

        try (FileOutputStream fos = new FileOutputStream(targetZip.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            Files.walk(sourceDir)
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {

                    String zipEntryName = sourceDir.relativize(path).toString().replace("\\", "/");
                    ZipEntry zipEntry = new ZipEntry(zipEntryName);
                    try {
                        zos.putNextEntry(zipEntry);
                        Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        logger.warn("Failed to zip file: {} - {}", path, e.getMessage());
                    }
                });
        }

        logger.info("Directory compressed successfully to: {}", targetZip);
        return targetZip;
    }

    public static Path zipDirectory(String sourceDir, String targetZip) throws IOException {
        return zipDirectory(Paths.get(sourceDir), Paths.get(targetZip));
    }
}
