package com.raks.apiurlcomparison;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class UtilityService {
    private static final Logger logger = LoggerFactory.getLogger(UtilityService.class);

    public File exportBaselines(String storageDir, String serviceName) throws IOException {
        File zipFile = File.createTempFile("export_", ".zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            Path sourcePath = Paths.get(storageDir);
            
            if (serviceName.equalsIgnoreCase("ALL")) {
                zipDirectory(sourcePath, sourcePath, zos);
            } else {
                Path servicePath = sourcePath.resolve(serviceName);
                if (Files.exists(servicePath)) {
                    zipDirectory(servicePath, sourcePath, zos);
                } else {
                    throw new FileNotFoundException("Service not found: " + serviceName);
                }
            }
        }
        return zipFile;
    }

    private void zipDirectory(Path folder, Path base, ZipOutputStream zos) throws IOException {
        Files.walk(folder).filter(path -> !Files.isDirectory(path)).forEach(path -> {
            ZipEntry zipEntry = new ZipEntry(base.relativize(path).toString());
            try {
                zos.putNextEntry(zipEntry);
                Files.copy(path, zos);
                zos.closeEntry();
            } catch (IOException e) {
                logger.error("Error zipping file: {}", path, e);
            }
        });
    }

    public List<String> detectConflicts(String storageDir, InputStream zipStream) throws IOException {
        List<String> conflicts = new ArrayList<>();
        File tempZip = File.createTempFile("import_detect_", ".zip");
        Files.copy(zipStream, tempZip.toPath(), StandardCopyOption.REPLACE_EXISTING);

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(tempZip))) {
            ZipEntry entry;
            Set<String> topLevelDirs = new HashSet<>();
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                int firstSlash = name.indexOf('/');
                if (firstSlash > 0) {
                    topLevelDirs.add(name.substring(0, firstSlash));
                } else if (entry.isDirectory()) {
                    topLevelDirs.add(name.replace("/", ""));
                }
                zis.closeEntry();
            }

            for (String dir : topLevelDirs) {
                if (Files.exists(Paths.get(storageDir).resolve(dir))) {
                    conflicts.add(dir);
                }
            }
        } finally {
            tempZip.delete();
        }
        return conflicts;
    }

    public void importBaselines(String storageDir, InputStream zipStream) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path targetPath = Paths.get(storageDir).resolve(entry.getName());
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
}
