package com.raks.muleguard.wrapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.raks.apiforge.ApiConfig;
import com.raks.apiforge.ComparisonResult;
import com.raks.apiforge.ComparisonService;
import com.raks.apiforge.Config;
import com.raks.apiforge.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.File;

public class ApiForgeInvoker {
    private static final Logger logger = LoggerFactory.getLogger(ApiForgeInvoker.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public static String getConfig() {
        try {
            Config config = loadConfigFromFile();
            resolvePayloads(config);
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            logger.error("Error loading config for wrapper", e);
            return "{}";
        }
    }

    private static Config loadConfigFromFile() throws Exception {
        // Try to load from CWD first (often where the standalone JAR runs)
        File cwdConfig = new File("config.yaml");
        if (cwdConfig.exists()) {
            logger.info("Loading config from CWD: {}", cwdConfig.getAbsolutePath());
            return yamlMapper.readValue(cwdConfig, Config.class);
        }

        // Try to load from classpath (where the wrapper resources are)
        logger.info("Loading config from classpath resources: web/apiforge/config.yaml");
        try (InputStream is = ApiForgeInvoker.class.getClassLoader().getResourceAsStream("web/apiforge/config.yaml")) {
            if (is == null) {
                logger.warn("web/apiforge/config.yaml not found in classpath. Returning empty config.");
                return new Config();
            }
            return yamlMapper.readValue(is, Config.class);
        }
    }

    private static void resolvePayloads(Config config) {
        if (config.getRestApis() != null) {
            config.getRestApis().values().forEach(ApiForgeInvoker::resolveApiPayloads);
        }
        if (config.getSoapApis() != null) {
            config.getSoapApis().values().forEach(ApiForgeInvoker::resolveApiPayloads);
        }
    }

    private static void resolveApiPayloads(ApiConfig api) {
        if (api == null || api.getOperations() == null) return;
        for (Operation op : api.getOperations()) {
            String templatePath = op.getPayloadTemplatePath();
            if (templatePath != null && !templatePath.isEmpty() && !templatePath.trim().startsWith("<") && !templatePath.trim().startsWith("{")) {
                try {
                    // Try to read as a file relative to CWD
                    Path path = Paths.get(templatePath);
                    if (Files.exists(path)) {
                        logger.info("Resolving payload from file: {}", path.toAbsolutePath());
                        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                        op.setPayloadTemplatePath(content);
                    } else {
                        // Try classpath fallback
                        String resourcePath = templatePath.replace('\\', '/');
                        if (!resourcePath.startsWith("/")) resourcePath = "/" + resourcePath;
                        try (InputStream is = ApiForgeInvoker.class.getResourceAsStream(resourcePath)) {
                            if (is != null) {
                                logger.info("Resolving payload from classpath: {}", resourcePath);
                                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                                op.setPayloadTemplatePath(content);
                            } else {
                                // Fallback: Try prepending web/apiforge/
                                String fallbackPath = "/web/apiforge" + resourcePath;
                                logger.info("Payload not found at {}, trying fallback: {}", resourcePath, fallbackPath);
                                try (InputStream is2 = ApiForgeInvoker.class.getResourceAsStream(fallbackPath)) {
                                    if (is2 != null) {
                                        logger.info("Resolving payload from fallback classpath: {}", fallbackPath);
                                        String content = new String(is2.readAllBytes(), StandardCharsets.UTF_8);
                                        op.setPayloadTemplatePath(content);
                                    } else {
                                        logger.warn("Payload not found at fallback path: {}", fallbackPath);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to resolve payload path: {} - {}", templatePath, e.getMessage());
                }
            }
        }
    }

    public static List<ComparisonResult> compare(Map<String, Object> configMap, String workDir, String licenseKey) {
        try {
            // 0. Validate License
            com.raks.apiforge.license.LicenseValidator.validate(licenseKey);

            logger.info("Invoking API Forge calculation with workDir: {}", workDir);

            // Convert Map to Config object using Jackson
            Config config = objectMapper.convertValue(configMap, Config.class);

            // Inject workDir if baseline config exists
            if (config.getBaseline() != null && workDir != null && !workDir.isEmpty()) {
                config.getBaseline().setStorageDir(workDir);
                logger.info("Overridden storageDir to: {}", workDir);
            } else if ("BASELINE".equalsIgnoreCase(config.getComparisonMode()) && workDir != null && !workDir.isEmpty()) {
                 // If baseline config is null but mode is BASELINE, initialize it (though UI should send it)
                 Config.BaselineConfig baselineConfig = new Config.BaselineConfig();
                 baselineConfig.setStorageDir(workDir);
                 config.setBaseline(baselineConfig);
                 logger.info("Initialized BaselineConfig with storageDir: {}", workDir);
            }

            // Execute Comparison
            ComparisonService service = new ComparisonService();
            List<ComparisonResult> results = service.execute(config);

            logger.info("API Forge completed. Results count: {}", results.size());
            return results;

        } catch (Exception e) {
            logger.error("Error during API Forge invocation", e);
            // Return empty list or handle error appropriately for Mule to catch
            ComparisonResult errorResult = new ComparisonResult();
            errorResult.setStatus(ComparisonResult.Status.ERROR);
            errorResult.setErrorMessage("Wrapper Invocation Failed: " + e.getMessage());
            return Collections.singletonList(errorResult);
        }
    }

    // Mock server methods removed as mocks are now managed by Mule flows

    // --- BASELINE WRAPPERS ---
    public static List<String> listBaselineServices(String workDir) {
        logger.info("Listing Baseline Services from WorkDir: {}", workDir);
        try {
            if (workDir == null) return Collections.emptyList();
            com.raks.apiforge.BaselineStorageService storage = new com.raks.apiforge.BaselineStorageService(workDir);
            return storage.listServices("ALL");
        } catch (Exception e) {
            logger.error("Error listing services", e);
            return Collections.emptyList();
        }
    }

    public static List<String> listBaselineDates(String workDir, String serviceName) {
         try {
            if (workDir == null || serviceName == null) return Collections.emptyList();
            com.raks.apiforge.BaselineStorageService storage = new com.raks.apiforge.BaselineStorageService(workDir);
            return storage.listDates(serviceName);
        } catch (Exception e) {
            logger.error("Error listing dates", e);
            return Collections.emptyList();
        }
    }

    // Returns JSON String directly to simplify object structure for Mule
    public static String listBaselineRuns(String workDir, String serviceName, String date) {
        try {
            if (workDir == null || serviceName == null || date == null) return "[]";
            com.raks.apiforge.BaselineStorageService storage = new com.raks.apiforge.BaselineStorageService(workDir);
            List<com.raks.apiforge.BaselineStorageService.RunInfo> runs = storage.listRuns(serviceName, date);
            return objectMapper.writeValueAsString(runs);
        } catch (Exception e) {
            logger.error("Error listing runs", e);
            return "[]";
        }
    }

    public static String getRunEndpoint(String workDir, String serviceName, String date, String runId) {
        try {
             if (workDir == null) return "{\"endpoint\": null}";
             com.raks.apiforge.BaselineStorageService storage = new com.raks.apiforge.BaselineStorageService(workDir);
             String endpoint = storage.getRunEndpoint(serviceName, date, runId);
             String payload = storage.getRunRequestPayload(serviceName, date, runId);
             Map<String, String> headers = storage.getRunRequestHeaders(serviceName, date, runId);
             com.raks.apiforge.RunMetadata metadata = storage.getRunMetadata(serviceName, date, runId);
             
             Map<String, Object> result = new HashMap<>();
             result.put("endpoint", endpoint);
             result.put("payload", payload);
             result.put("headers", headers);
             result.put("metadata", metadata);
             return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            logger.error("Error fetching run endpoint", e);
            return "{\"endpoint\": null}";
        }
    }

    public static String getRunDetails(String workDir, String serviceName, String date, String runId) {
        try {
            if (workDir == null) return "[]";
            com.raks.apiforge.BaselineStorageService storage = new com.raks.apiforge.BaselineStorageService(workDir);
            com.raks.apiforge.BaselineComparisonService service = new com.raks.apiforge.BaselineComparisonService(storage);
            
            List<ComparisonResult> results = service.getBaselineAsResults(serviceName, date, runId);
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            logger.error("Error fetching run details", e);
            return "[]";
        }
    }

    // --- CLEANUP WRAPPER ---
    public static Map<String, Object> cleanup(String workDir, int retentionHours) {
        Map<String, Object> result = new HashMap<>();
        if (workDir == null || workDir.isEmpty()) {
            result.put("status", "skipped");
            result.put("message", "Working directory not configured");
            return result;
        }

        // Normalize path for consistent logging
        try {
            workDir = new File(workDir).getCanonicalPath();
        } catch (Exception e) {
             // Fallback to absolute path or original if canonical fails
             workDir = new File(workDir).getAbsolutePath();
        }

        File folder = new File(workDir);
        if (!folder.exists()) {
            if (folder.mkdirs()) {
                 result.put("status", "skipped");
                 result.put("message", "Working directory created: " + workDir);
                 return result;
            } else {
                 result.put("status", "skipped");
                 result.put("message", "Working directory does not exist and could not be created: " + workDir);
                 return result;
            }
        }
        if (!folder.isDirectory()) {
            result.put("status", "skipped");
            result.put("message", "Path is not a directory: " + workDir);
            return result;
        }

        logger.info("Starting cleanup for directory: {} with retention: {} hours", workDir, retentionHours);
        
        try {
            long retentionMillis = retentionHours * 60 * 60 * 1000L;
            long cutoffTime = System.currentTimeMillis() - retentionMillis;
            int deletedCount = cleanupRecursive(folder, cutoffTime);
            
            result.put("status", "success");
            result.put("deletedFiles", deletedCount);
            logger.info("Cleanup completed. Deleted {} files/folders.", deletedCount);
        } catch (Exception e) {
            logger.error("Cleanup failed", e);
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    private static int cleanupRecursive(File file, long cutoffTime) {
        int count = 0;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    count += cleanupRecursive(child, cutoffTime);
                }
            }
            // Try to delete empty directories, or directories that were emptied and are old enough
            // Verify if it's safe to delete the directory itself based on its age? 
            // Logic: If directory is empty after cleaning children, and the directory itself is old, delete it.
            // But we must check if it's empty now.
             if (file.list().length == 0 && file.lastModified() < cutoffTime) {
                if (file.delete()) {
                    count++;
                }
            }
        } else {
            // File
            if (file.lastModified() < cutoffTime) {
                if (file.delete()) {
                    count++;
                }
            }
        }
        return count;
    }

    // --- UTILITY WRAPPERS ---
    public static String ping(String url) {
        try {
             com.raks.apiforge.UtilityService util = new com.raks.apiforge.UtilityService();
             Map<String, Object> result = util.ping(url);
             return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            logger.error("Error invoking ping", e);
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    public static String fetchWsdl(String url) {
        try {
             com.raks.apiforge.UtilityService util = new com.raks.apiforge.UtilityService();
             return util.fetchWsdl(url);
        } catch (Exception e) {
            logger.error("Error invoking fetchWsdl", e);
            return "Error fetching WSDL: " + e.getMessage();
        }
    }

    public static String uploadCertificate(String workDir, String fileName, byte[] content) {
        try {
            com.raks.apiforge.CertificateService service = new com.raks.apiforge.CertificateService();
            Map<String, Object> result = service.uploadCertificate(workDir, fileName, content);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            logger.error("Error uploading certificate", e);
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    public static String validateCertificate(String bodyJson) {
        try {
            Map<String, Object> params = objectMapper.readValue(bodyJson, Map.class);
            com.raks.apiforge.CertificateService service = new com.raks.apiforge.CertificateService();
            Map<String, Object> result = service.validateCertificate(params);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            logger.error("Error validating certificate", e);
            return "{\"valid\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    public static File exportBaselines(String workDir, String serviceName) {
        try {
            if (workDir == null) throw new IllegalArgumentException("Working Directory required");
            com.raks.apiforge.UtilityService util = new com.raks.apiforge.UtilityService();
            return util.exportBaselines(workDir, serviceName);
        } catch (Exception e) {
            logger.error("Error exporting baselines", e);
            return null;
        }
    }

    public static String detectConflicts(String workDir, InputStream zipStream) {
        try {
            if (workDir == null) return "[]";
            com.raks.apiforge.UtilityService util = new com.raks.apiforge.UtilityService();
            // We need to support reading the stream without consuming it permanently if we want to reuse it?
            // Actually, for conflict detection we might consume it. 
            // In Mule, we might need to buffer it or simple let the user re-upload for the actual import if conflict confirmed.
            // But typical flow is: check conflict, if none import.
            // Let's assume the conflict check consumes the stream.
            List<String> conflicts = util.detectConflicts(workDir, zipStream);
            return objectMapper.writeValueAsString(conflicts);
        } catch (Exception e) {
            logger.error("Error detecting conflicts", e);
            return "[]";
        }
    }

    public static String importBaselines(String workDir, InputStream zipStream) {
          try {
            // Resolve canonical path to debug property resolution
            File workDirFile = new File(workDir);
            String canonicalPath = workDirFile.getCanonicalPath();
            
            logger.info("Importing Baselines to WorkDir (Input): {}", workDir);
            logger.info("Importing Baselines to WorkDir (Canonical): {}", canonicalPath);
            
            if (workDir == null) throw new IllegalArgumentException("Working Directory required");
            
            // Ensure directory exists
            if (!workDirFile.exists()) {
                workDirFile.mkdirs();
            }

            com.raks.apiforge.UtilityService util = new com.raks.apiforge.UtilityService();
            // Pass the canonical path to utility service to be sure
            List<String> imported = util.importBaselines(canonicalPath, zipStream);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("imported", imported);
            result.put("targetDirectory", canonicalPath); // Return path to UI
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            logger.error("Error importing baselines", e);
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    // --- File-Based Wrappers (To avoid Stream Exhaustion) ---
    public static String detectConflictsFromFile(String workDir, String zipPath) {
        try (InputStream is = new FileInputStream(zipPath)) {
            return detectConflicts(workDir, is);
        } catch (Exception e) {
            logger.error("Error detecting conflicts from file", e);
            return "[]";
        }
    }

    public static String importBaselinesFromFile(String workDir, String zipPath) {
        try (InputStream is = new FileInputStream(zipPath)) {
            return importBaselines(workDir, is);
        } catch (Exception e) {
            logger.error("Error importing baselines from file", e);
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
