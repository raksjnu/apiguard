package com.raks.muleguard.wrapper;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZipExtractor {
    
    private static final Logger logger = LoggerFactory.getLogger(ZipExtractor.class);
    
    private static final Set<String> IGNORE_FOLDERS = new HashSet<>(Arrays.asList(
            "target", "bin", "build", ".git", ".idea", ".vscode", "node_modules", "muleguard-reports"));

    public static String extractZip(InputStream zipStream, String targetDirectory) throws IOException {
        Path targetPath = Paths.get(targetDirectory);
        Files.createDirectories(targetPath);
        
        Path tempZipPath = Files.createTempFile("muleguard-start-", ".zip");
        try {
            Files.copy(zipStream, tempZipPath, StandardCopyOption.REPLACE_EXISTING);
            
            int totalExtracted = 0;
            int totalSkipped = 0;

            try (ZipFile zipFile = new ZipFile(tempZipPath.toFile())) {
                java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    
                    if (shouldIgnore(entryName)) {
                        totalSkipped++;
                        continue;
                    }

                    Path filePath = targetPath.resolve(entryName).normalize();
                    if (!filePath.startsWith(targetPath)) {
                        throw new IOException("Entry is outside of the target dir: " + entryName);
                    }

                    if (entry.isDirectory()) {
                        Files.createDirectories(filePath);
                    } else {
                        Files.createDirectories(filePath.getParent());
                        try (InputStream is = zipFile.getInputStream(entry);
                                OutputStream fos = Files.newOutputStream(filePath)) {
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = is.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                            totalExtracted++;
                        }
                    }
                }
            }
            logger.info("ZIP extraction complete: {} files extracted, {} skipped", totalExtracted, totalSkipped);
            return targetPath.toString();
        } finally {
            try {
                Files.deleteIfExists(tempZipPath);
            } catch (IOException ignored) {
                // check if we should log verify delete
                logger.trace("Failed to delete temp zip file", ignored);
            }
        }
    }

    public static String zipDirectory(String sourceDirectory, String zipFilePath) throws IOException {
        Path sourcePath = Paths.get(sourceDirectory);
        Path zipPath = Paths.get(zipFilePath);

        if (!Files.exists(sourcePath)) {
            throw new FileNotFoundException("Source directory not found: " + sourceDirectory);
        }

        if (zipPath.getParent() != null) {
            Files.createDirectories(zipPath.getParent());
        }

        try (FileOutputStream fos = new FileOutputStream(zipPath.toFile());
                ZipOutputStream zos = new ZipOutputStream(fos)) {

            Files.walk(sourcePath)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(sourcePath.relativize(path).toString().replace("\\", "/"));
                        try {
                            zos.putNextEntry(zipEntry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                             logger.error("Failed to zip file: {}", path, e);
                        }
                    });
        }
        logger.info("Directory zipped successfully to: {}", zipFilePath);
        return zipFilePath;
    }

    private static boolean shouldIgnore(String path) {
        for (String folder : IGNORE_FOLDERS) {
            if (path.contains("/" + folder + "/") || path.startsWith(folder + "/")) {
                return true;
            }
        }
        return false;
    }
}
