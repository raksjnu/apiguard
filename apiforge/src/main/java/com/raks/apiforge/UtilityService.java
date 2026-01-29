package com.raks.apiforge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class UtilityService {
    private static final Logger logger = LoggerFactory.getLogger(UtilityService.class);

    public File exportBaselines(String storageDir, List<String> relativePaths) throws IOException {
        File zipFile = File.createTempFile("export_", ".zip");
        Path rootPath = Paths.get(storageDir);
        
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (String rel : relativePaths) {
                Path sourcePath = rootPath.resolve(rel);
                if (Files.exists(sourcePath)) {
                    if (Files.isDirectory(sourcePath)) {
                        zipDirectory(sourcePath, rootPath, zos);
                    } else {
                        zipFile(sourcePath, rootPath, zos);
                    }
                }
            }
        }
        return zipFile;
    }

    private void zipFile(Path file, Path base, ZipOutputStream zos) throws IOException {
        ZipEntry zipEntry = new ZipEntry(base.relativize(file).toString().replace("\\", "/"));
        zos.putNextEntry(zipEntry);
        Files.copy(file, zos);
        zos.closeEntry();
    }

    private void zipDirectory(Path folder, Path base, ZipOutputStream zos) throws IOException {
        try (java.util.stream.Stream<Path> stream = Files.walk(folder)) {
            stream.filter(path -> !Files.isDirectory(path)).forEach(path -> {
                ZipEntry zipEntry = new ZipEntry(base.relativize(path).toString().replace("\\", "/"));
                try {
                    zos.putNextEntry(zipEntry);
                    Files.copy(path, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    logger.error("Error zipping file: {}", path, e);
                }
            });
        }
    }

    public List<String> detectConflicts(String storageDir, InputStream zipStream) throws IOException {
        List<String> conflicts = new ArrayList<>();
        File tempZip = File.createTempFile("import_detect_", ".zip");
        Files.copy(zipStream, tempZip.toPath(), StandardCopyOption.REPLACE_EXISTING);

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(tempZip))) {
            ZipEntry entry;
            Set<String> conflictPaths = new HashSet<>();
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                
                // Logic:
                // If path starts with rest/, soap/ or jms/, check the NEXT level (the service level).
                // If valid service level found, check if that specific service exists.
                // Otherwise fall back to top level check (legacy support).
                
                String[] parts = name.split("/");
                if (parts.length >= 2 && isProtocolDir(parts[0])) {
                     // Check "protocol/service"
                     String serviceKey = parts[0] + "/" + parts[1];
                     conflictPaths.add(serviceKey);
                } else if (!name.contains("/") && entry.isDirectory()) {
                     // Root folder
                     conflictPaths.add(name.replace("/", ""));
                } else if (name.indexOf('/') > 0) {
                     // Top level dir (legacy e.g. "service1/")
                     conflictPaths.add(name.substring(0, name.indexOf('/')));
                }
                zis.closeEntry();
            }

            for (String relativePath : conflictPaths) {
                if (Files.exists(Paths.get(storageDir).resolve(relativePath))) {
                    conflicts.add(relativePath);
                }
            }
        } finally {
            tempZip.delete();
        }
        return conflicts;
    }

    private boolean isProtocolDir(String name) {
        return Arrays.asList("rest", "jms", "soap").contains(name.toLowerCase());
    }

    public List<String> importBaselines(String storageDir, InputStream zipStream, String conflictAction) throws IOException {
        List<String> importedFiles = new ArrayList<>();
        File tempZip = File.createTempFile("import_", ".zip");
        Files.copy(zipStream, tempZip.toPath(), StandardCopyOption.REPLACE_EXISTING);

        Set<String> pathsToSkip = new HashSet<>();
        if ("SKIP".equalsIgnoreCase(conflictAction)) {
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(tempZip))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    String[] parts = name.split("/");
                    
                    if (parts.length >= 2 && isProtocolDir(parts[0])) {
                         // Check specific service existence
                         String serviceKey = parts[0] + "/" + parts[1];
                         if (Files.exists(Paths.get(storageDir).resolve(serviceKey))) {
                             pathsToSkip.add(serviceKey);
                         }
                    } else {
                         // Fallback legacy
                         int firstSlash = name.indexOf('/');
                         if (firstSlash > 0) {
                            String topDir = name.substring(0, firstSlash);
                            if (Files.exists(Paths.get(storageDir).resolve(topDir))) {
                                pathsToSkip.add(topDir);
                            }
                         }
                    }
                    zis.closeEntry();
                }
            }
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(tempZip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                // SKIP Check
                boolean shouldSkip = false;
                for (String skipPath : pathsToSkip) {
                    if (name.startsWith(skipPath + "/") || name.equals(skipPath) || name.equals(skipPath + "/")) {
                        shouldSkip = true;
                        break;
                    }
                }
                if (shouldSkip) {
                    zis.closeEntry();
                    continue;
                }
                
                Path targetPath = Paths.get(storageDir).resolve(name);
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    importedFiles.add(name);
                }
                zis.closeEntry();
            }
        } finally {
            tempZip.delete();
        }
        return importedFiles;
    }
    public Map<String, Object> ping(String targetUrl) {
        Map<String, Object> pingRes = new HashMap<>();
        if (targetUrl == null || targetUrl.isEmpty()) {
            pingRes.put("error", "URL is required");
            return pingRes;
        }
        
        long start = System.currentTimeMillis();
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(targetUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();
            long end = System.currentTimeMillis();

            pingRes.put("success", code >= 200 && code < 400);
            pingRes.put("statusCode", code);
            pingRes.put("latency", end - start);

        } catch (Exception e) {
            pingRes.put("success", false);
            pingRes.put("statusCode", 0);
            pingRes.put("error", e.getMessage());
            pingRes.put("latency", System.currentTimeMillis() - start);
        }
        return pingRes;
    }

    public String fetchWsdl(String targetUrl) {
        if (targetUrl == null || targetUrl.isEmpty()) return "Error: URL is required";

        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(targetUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            int code = conn.getResponseCode();
            if (code >= 200 && code < 400) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    return sb.toString();
                }
            } else {
                return "Error fetching WSDL: HTTP " + code;
            }
        } catch (Exception e) {
            return "Error fetching WSDL: " + e.getMessage();
        }
    }
}
