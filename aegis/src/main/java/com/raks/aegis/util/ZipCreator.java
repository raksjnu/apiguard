package com.raks.aegis.util;
import java.io.IOException;
import java.nio.file.*;
import java.util.zip.*;
public class ZipCreator {
    public static void createZip(String sourceDirPath, String outputZipPath) throws IOException {
        Path sourceDir = Paths.get(sourceDirPath);
        Path outputZip = Paths.get(outputZipPath);
        Files.createDirectories(outputZip.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputZip))) {
            Files.walk(sourceDir)
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> !path.equals(outputZip)) 
                    .forEach(path -> {
                        try {
                            String relativePath = sourceDir.relativize(path).toString();
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
    public static long getFileSize(String filePath) throws IOException {
        return Files.size(Paths.get(filePath));
    }
}
