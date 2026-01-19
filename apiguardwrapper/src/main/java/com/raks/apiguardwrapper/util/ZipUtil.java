package com.raks.apiguardwrapper.util;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZipUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZipUtil.class);

    public static void zipDirectory(String sourceDirPath, String outputZipPath) throws IOException {
        Path sourceDir = Paths.get(sourceDirPath);
        Path outputZip = Paths.get(outputZipPath);
        
        LOGGER.info("Zipping directory: {} to {}", sourceDir.toAbsolutePath(), outputZip.toAbsolutePath());
        final int[] count = {0};

        // Ensure parent dir exists
        if (outputZip.getParent() != null) {
            Files.createDirectories(outputZip.getParent());
        }
        
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputZip))) {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!file.equals(outputZip)) {
                        // Get relative path
                        String relativePath = sourceDir.relativize(file).toString();
                        // Use forward slashes for zip spec
                        relativePath = relativePath.replace("\\", "/");
                        
                        LOGGER.debug("Adding entry: {}", relativePath);
                        
                        ZipEntry zipEntry = new ZipEntry(relativePath);
                        zos.putNextEntry(zipEntry);
                        Files.copy(file, zos);
                        zos.closeEntry();
                        count[0]++;
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!sourceDir.equals(dir)) {
                        String relativePath = sourceDir.relativize(dir).toString();
                        relativePath = relativePath.replace("\\", "/") + "/";
                        LOGGER.debug("Adding directory entry: {}", relativePath);
                        zos.putNextEntry(new ZipEntry(relativePath));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        LOGGER.info("Zip created successfully. Total entries: {}", count[0]);
    }

    public static String unzip(String zipFilePath, String outputDir) throws IOException {
        Path zipFile = Paths.get(zipFilePath);
        Path outputPath = Paths.get(outputDir);
        
        LOGGER.info("Unzipping archive: {} to {}", zipFile.toAbsolutePath(), outputPath.toAbsolutePath());

        // Ensure output dir exists
        Files.createDirectories(outputPath);
        int count = 0;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // Normalize path separators to forward slashes to handle Windows-created zips on Linux
                String entryName = entry.getName().replace("\\", "/");
                Path targetPath = outputPath.resolve(entryName).normalize();

                // Prevent Zip Slip
                if (!targetPath.startsWith(outputPath)) {
                    throw new IOException("Invalid ZIP entry (path traversal detected): " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    if (targetPath.getParent() != null) {
                        Files.createDirectories(targetPath.getParent());
                    }
                    Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    count++;
                }
                zis.closeEntry();
            }
        }
        
        LOGGER.info("Unzip completed. Total files extracted: {}", count);
        
        if (count == 0) {
            throw new IOException("Extraction failed: No files were extracted from the archive. Check path separators or archive validity.");
        }
        
        return outputPath.toString();
    }
    
    // Alias for JARs as they are Zips
    public static String extractJar(String jarFilePath, String outputDir) throws IOException {
         return unzip(jarFilePath, outputDir);
    }

    public static String getProjectRootName(String extractPath, String fallbackName) {
        try {
            Path dir = Paths.get(extractPath);
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                return fallbackName;
            }

            // Check if the root itself is a project (Flat Zip)
            if (Files.exists(dir.resolve("pom.xml")) || 
                Files.exists(dir.resolve("mule-artifact.json")) ||
                Files.exists(dir.resolve(".project"))) {
                return fallbackName;
            }
            
            // Filter out system files like __MACOSX, .DS_Store, etc.
            java.util.List<Path> contents = Files.list(dir)
                .filter(p -> {
                    String name = p.getFileName().toString();
                    return !name.equals("__MACOSX") && !name.equals(".DS_Store") && !name.startsWith(".");
                })
                .collect(java.util.stream.Collectors.toList());
            
            // Single subdirectory -> Use its name
            if (contents.size() == 1 && Files.isDirectory(contents.get(0))) {
                return contents.get(0).getFileName().toString();
            }

            // Multiple items and root is not a project -> Return null to let Aegis discover names
            // This handles the case of a zip containing multiple project folders
            return null;

        } catch (IOException e) {
            LOGGER.warn("Failed to detect project root name from {}: {}", extractPath, e.getMessage());
        }
        return fallbackName;
    }
}
