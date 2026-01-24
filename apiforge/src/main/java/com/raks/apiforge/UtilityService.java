package com.raks.apiforge;

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

    public List<String> importBaselines(String storageDir, InputStream zipStream) throws IOException {
        List<String> importedFiles = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path targetPath = Paths.get(storageDir).resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    importedFiles.add(entry.getName());
                }
                zis.closeEntry();
            }
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
