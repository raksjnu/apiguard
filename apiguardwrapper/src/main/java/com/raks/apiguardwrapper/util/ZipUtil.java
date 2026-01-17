package com.raks.apiguardwrapper.util;

import java.io.IOException;
import java.nio.file.*;
import java.util.zip.*;
import java.nio.file.attribute.BasicFileAttributes;

public class ZipUtil {

    public static void zipDirectory(String sourceDirPath, String outputZipPath) throws IOException {
        Path sourceDir = Paths.get(sourceDirPath);
        Path outputZip = Paths.get(outputZipPath);
        
        // Ensure parent dir exists
        Files.createDirectories(outputZip.getParent());
        
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputZip))) {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!file.equals(outputZip)) {
                         // Get relative path
                        String relativePath = sourceDir.relativize(file).toString();
                        // Use forward slashes for zip spec
                        relativePath = relativePath.replace("\\", "/");
                        
                        ZipEntry zipEntry = new ZipEntry(relativePath);
                        zos.putNextEntry(zipEntry);
                        Files.copy(file, zos);
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
