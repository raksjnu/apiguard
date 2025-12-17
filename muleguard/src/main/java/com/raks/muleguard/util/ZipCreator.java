package com.raks.muleguard.util;

import java.io.IOException;
import java.nio.file.*;
import java.util.zip.*;

/**
 * Utility class for creating ZIP archives of directories.
 */
public class ZipCreator {

    /**
     * Creates a ZIP file from a source directory.
     * 
     * @param sourceDirPath Path to the directory to compress
     * @param outputZipPath Path where the ZIP file should be created
     * @throws IOException if an I/O error occurs
     */
    public static void createZip(String sourceDirPath, String outputZipPath) throws IOException {
        Path sourceDir = Paths.get(sourceDirPath);
        Path outputZip = Paths.get(outputZipPath);

        // Ensure parent directory exists
        Files.createDirectories(outputZip.getParent());

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputZip))) {
            Files.walk(sourceDir)
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> !path.equals(outputZip)) // Don't include the ZIP file itself
                    .forEach(path -> {
                        try {
                            String relativePath = sourceDir.relativize(path).toString();
                            // Use forward slashes for ZIP entries (cross-platform compatibility)
                            ZipEntry zipEntry = new ZipEntry(relativePath.replace("\\", "/"));
                            zos.putNextEntry(zipEntry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to add file to ZIP: " + path, e);
                        }
                    });
        }
    }

    /**
     * Gets the size of a file in bytes.
     * 
     * @param filePath Path to the file
     * @return Size in bytes
     * @throws IOException if an I/O error occurs
     */
    public static long getFileSize(String filePath) throws IOException {
        return Files.size(Paths.get(filePath));
    }
}
