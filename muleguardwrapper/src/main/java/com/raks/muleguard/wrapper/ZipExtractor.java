package com.raks.muleguard.wrapper;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
public class ZipExtractor {
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
            System.out.println(
                    "ZIP extraction complete: " + totalExtracted + " files extracted, " + totalSkipped + " skipped");
            return targetPath.toString();
        } finally {
            try {
                Files.deleteIfExists(tempZipPath);
            } catch (IOException ignored) {
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
                            System.err.println("Failed to zip file: " + path + " - " + e.getMessage());
                        }
                    });
        }
        System.out.println("Directory zipped successfully to: " + zipFilePath);
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
