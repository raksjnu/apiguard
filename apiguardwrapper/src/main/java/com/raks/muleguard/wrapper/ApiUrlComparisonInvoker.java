package com.raks.muleguard.wrapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raks.apiurlcomparison.ComparisonResult;
import com.raks.apiurlcomparison.ComparisonService;
import com.raks.apiurlcomparison.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.File;

public class ApiUrlComparisonInvoker {
    private static final Logger logger = LoggerFactory.getLogger(ApiUrlComparisonInvoker.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static List<ComparisonResult> compare(Map<String, Object> configMap, String workDir) {
        try {
            logger.info("Invoking ApiUrlComparison calculation with workDir: {}", workDir);

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

            logger.info("ApiUrlComparison completed. Results count: {}", results.size());
            return results;

        } catch (Exception e) {
            logger.error("Error during ApiUrlComparison invocation", e);
            // Return empty list or handle error appropriately for Mule to catch
            ComparisonResult errorResult = new ComparisonResult();
            errorResult.setStatus(ComparisonResult.Status.ERROR);
            errorResult.setErrorMessage("Wrapper Invocation Failed: " + e.getMessage());
            return Collections.singletonList(errorResult);
        }
    }

    // --- MOCK SERVER WRAPPERS ---
    public static Map<String, Object> startMockServer() {
        try {
            com.raks.apiurlcomparison.MockApiServer.start();
            return Collections.singletonMap("status", "started");
        } catch (Exception e) {
            logger.error("Failed to start mock server", e);
            return Collections.singletonMap("error", e.getMessage());
        }
    }

    public static Map<String, Object> stopMockServer() {
        try {
            com.raks.apiurlcomparison.MockApiServer.stop();
            return Collections.singletonMap("status", "stopped");
        } catch (Exception e) {
            logger.error("Failed to stop mock server", e);
            return Collections.singletonMap("error", e.getMessage());
        }
    }

    public static Map<String, Object> getMockServerStatus() {
        return Collections.singletonMap("running", com.raks.apiurlcomparison.MockApiServer.isRunning());
    }

    // --- BASELINE WRAPPERS ---
    public static List<String> listBaselineServices(String workDir) {
        try {
            if (workDir == null) return Collections.emptyList();
            com.raks.apiurlcomparison.BaselineStorageService storage = new com.raks.apiurlcomparison.BaselineStorageService(workDir);
            return storage.listServices();
        } catch (Exception e) {
            logger.error("Error listing services", e);
            return Collections.emptyList();
        }
    }

    public static List<String> listBaselineDates(String workDir, String serviceName) {
         try {
            if (workDir == null || serviceName == null) return Collections.emptyList();
            com.raks.apiurlcomparison.BaselineStorageService storage = new com.raks.apiurlcomparison.BaselineStorageService(workDir);
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
            com.raks.apiurlcomparison.BaselineStorageService storage = new com.raks.apiurlcomparison.BaselineStorageService(workDir);
            List<com.raks.apiurlcomparison.BaselineStorageService.RunInfo> runs = storage.listRuns(serviceName, date);
            return objectMapper.writeValueAsString(runs);
        } catch (Exception e) {
            logger.error("Error listing runs", e);
            return "[]";
        }
    }

    public static String getRunEndpoint(String workDir, String serviceName, String date, String runId) {
        try {
             if (workDir == null) return "{\"endpoint\": null}";
             com.raks.apiurlcomparison.BaselineStorageService storage = new com.raks.apiurlcomparison.BaselineStorageService(workDir);
             String endpoint = storage.getRunEndpoint(serviceName, date, runId);
             String payload = storage.getRunRequestPayload(serviceName, date, runId);
             
             Map<String, String> result = new HashMap<>();
             result.put("endpoint", endpoint);
             result.put("payload", payload);
             return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            logger.error("Error fetching run endpoint", e);
            return "{\"endpoint\": null}";
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
}
