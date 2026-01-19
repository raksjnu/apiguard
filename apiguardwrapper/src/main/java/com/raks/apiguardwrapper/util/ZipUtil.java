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
        LOGGER.info("Zip created successfully.");
    }

    public static String unzip(String zipFilePath, String outputDir) throws IOException {
        Path zipFile = Paths.get(zipFilePath);
        Path outputPath = Paths.get(outputDir);
        
        LOGGER.info("Unzipping archive: {} to {}", zipFile.toAbsolutePath(), outputPath.toAbsolutePath());

        // Ensure output dir exists
        Files.createDirectories(outputPath);

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
                }
                zis.closeEntry();
            }
        }
        
        LOGGER.info("Unzip completed.");
        return outputPath.toString();
    }
    
    // Alias for JARs as they are Zips
    public static String extractJar(String jarFilePath, String outputDir) throws IOException {
         return unzip(jarFilePath, outputDir);
    }
}
