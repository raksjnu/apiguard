package com.raks.apidiscovery;

import com.google.gson.Gson;
import com.raks.apidiscovery.connector.GitLabConnector;
import com.raks.apidiscovery.model.DiscoveryReport;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Service class for API Discovery operations
 * Extracted from HTTP handlers to enable Mule integration
 */
public class ApiDiscoveryService {
    
    private static final Gson gson = new Gson();
    private static final ScannerEngine engine = new ScannerEngine();
    
    // Progress tracking for active scans
    private static final Map<String, ScanProgress> activeScans = new ConcurrentHashMap<>();
    
    // Executor for async scanning
    private static final ExecutorService scanExecutor = Executors.newFixedThreadPool(3);
    
    /**
     * Start a new scan (async)
     * @param source Local path or GitLab URL
     * @param token GitLab access token (optional for local scans)
     * @param group GitLab group (optional)
     * @param tempDir Temporary directory for scan results
     * @return Scan ID
     */
    public static String startScan(String source, String token, String group, String tempDir) throws Exception {
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing 'source' parameter");
        }
        
        // Generate scan ID
        String scanId = UUID.randomUUID().toString();
        String scanFolder = generateScanFolderName();
        
        // Initialize progress
        ScanProgress progress = new ScanProgress(scanId, scanFolder);
        activeScans.put(scanId, progress);
        
        // Start scan in background
        scanExecutor.submit(() -> {
            try {
                performScan(scanId, source, token, scanFolder, progress, tempDir);
            } catch (Exception e) {
                progress.setError(e.getMessage());
                e.printStackTrace();
            }
        });
        
        return scanId;
    }
    
    /**
     * Get progress of an active scan
     * @param scanId Scan ID
     * @return Progress information
     */
    public static ScanProgress getProgress(String scanId) {
        return activeScans.get(scanId);
    }
    
    /**
     * List all completed scans
     * @param tempDir Temporary directory for scans
     * @return List of scan information
     */
    public static List<Map<String, Object>> listScans(String tempDir) {
        List<Map<String, Object>> scanFolders = new ArrayList<>();
        
        File dir = new File(tempDir);
        if (dir.exists() && dir.isDirectory()) {
            File[] folders = dir.listFiles(File::isDirectory);
            if (folders != null) {
                for (File folder : folders) {
                    if (folder.getName().toLowerCase().startsWith("scan")) {
                        Map<String, Object> info = new HashMap<>();
                        info.put("name", folder.getName());
                        info.put("path", folder.getAbsolutePath());
                        info.put("lastModified", folder.lastModified());
                        scanFolders.add(info);
                    }
                }
            }
        }
        
        // Sort by lastModified descending (newest first)
        scanFolders.sort((a, b) -> Long.compare((Long)b.get("lastModified"), (Long)a.get("lastModified")));
        
        return scanFolders;
    }
    
    /**
     * Delete a scan folder
     * @param scanName Scan folder name
     * @param tempDir Temporary directory
     * @return true if deleted successfully
     */
    public static boolean deleteScan(String scanName, String tempDir) {
        if (scanName == null || scanName.trim().isEmpty()) {
            return false;
        }
        
        File scanFolder = new File(tempDir, scanName);
        if (scanFolder.exists() && scanFolder.isDirectory()) {
            return deleteDirectory(scanFolder);
        }
        
        return false;
    }

    public static java.util.Map<String, java.util.List<String>> deleteScans(java.util.List<String> scanNames, String tempDir) {
        java.util.Map<String, java.util.List<String>> result = new java.util.HashMap<>();
        java.util.List<String> deleted = new java.util.ArrayList<>();
        java.util.List<String> failed = new java.util.ArrayList<>();
        
        if (scanNames != null) {
            for (String scanName : scanNames) {
                if (deleteScan(scanName, tempDir)) {
                    deleted.add(scanName);
                } else {
                    failed.add(scanName);
                }
            }
        }
        
        result.put("deleted", deleted);
        result.put("failed", failed);
        return result;
    }
    
    /**
     * View scan results
     * @param scanName Scan folder name
     * @param tempDir Temporary directory
     * @return List of discovery reports
     */
    public static List<DiscoveryReport> viewScan(String scanName, String tempDir) throws IOException {
        if (scanName == null || scanName.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing scan name");
        }
        
        File scanFolder = new File(tempDir, scanName);
        if (!scanFolder.exists() || !scanFolder.isDirectory()) {
            throw new IOException("Scan folder not found: " + scanName);
        }
        
        // Find all JSON report files
        List<DiscoveryReport> reports = new ArrayList<>();
        File[] jsonFiles = scanFolder.listFiles((dir, name) -> name.endsWith(".json"));
        
        if (jsonFiles != null) {
            for (File jsonFile : jsonFiles) {
                try {
                    String content = new String(java.nio.file.Files.readAllBytes(jsonFile.toPath()));
                    DiscoveryReport report = gson.fromJson(content, DiscoveryReport.class);
                    reports.add(report);
                } catch (Exception e) {
                    System.err.println("Failed to parse: " + jsonFile.getName());
                }
            }
        }
        
        return reports;
    }
    
    // ===== Private Helper Methods =====
    
    private static void performScan(String scanId, String source, String token, String scanFolder, ScanProgress progress, String tempDir) throws Exception {
        List<DiscoveryReport> reports = new ArrayList<>();
        
        File localFile = new File(source);
        
        // Update progress
        progress.setMessage("Initializing scan...");
        progress.setPercent(5);
        
        // 1. Check if it's a valid local directory
        if (localFile.exists() && localFile.isDirectory()) {
            System.out.println("Detected Local Directory: " + source);
            progress.setMessage("Scanning local directory: " + source);
            progress.setPercent(20);
            
            reports.add(engine.scanRepository(localFile));
            
            File[] subs = localFile.listFiles(File::isDirectory);
            if (subs != null) {
                int total = subs.length;
                int current = 0;
                for (File sub : subs) {
                    if (!sub.getName().startsWith(".")) {
                        progress.setMessage("Scanning subdirectory: " + sub.getName());
                        progress.setPercent(20 + (current * 60 / total));
                        reports.add(engine.scanRepository(sub));
                        current++;
                    }
                }
            }
        } 
        // 2. Otherwise, treat as GitLab (if token provided)
        else if (token != null && !token.isEmpty()) {
            System.out.println("Detected GitLab Source: " + source);
            progress.setMessage("Connecting to GitLab...");
            progress.setPercent(10);
            
            // Clean URL
            String cleanGroup = source.trim();
            if (cleanGroup.startsWith("https://gitlab.com/")) {
                cleanGroup = cleanGroup.substring("https://gitlab.com/".length());
            } else if (cleanGroup.startsWith("http://gitlab.com/")) {
                cleanGroup = cleanGroup.substring("http://gitlab.com/".length());
            }
            if (cleanGroup.endsWith("/")) cleanGroup = cleanGroup.substring(0, cleanGroup.length() - 1);
            if (cleanGroup.contains("#")) cleanGroup = cleanGroup.substring(0, cleanGroup.indexOf("#"));
            
            progress.setMessage("Scanning GitLab group: " + cleanGroup);
            progress.setPercent(30);
            
            GitLabConnector connector = new GitLabConnector();
            reports = connector.scanGroup(cleanGroup, token, scanFolder);
        } else {
            throw new IllegalArgumentException("Source not found locally and no GitLab Token provided");
        }
        
        // Save reports to disk
        progress.setMessage("Saving results...");
        progress.setPercent(90);
        
        File outputDir = new File(tempDir, scanFolder);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        int reportIndex = 1;
        for (DiscoveryReport report : reports) {
            String filename = String.format("%s_%d.json", 
                report.getRepoName().replaceAll("[^a-zA-Z0-9]", "_"), 
                reportIndex++);
            File reportFile = new File(outputDir, filename);
            
            try {
                String json = gson.toJson(report);
                java.nio.file.Files.write(reportFile.toPath(), json.getBytes());
                System.out.println("Saved report: " + reportFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("Failed to save report: " + e.getMessage());
            }
        }
        
        // CLEANUP: Delete downloaded repository folders to save space
        // Keep only the JSON reports
        File[] everything = outputDir.listFiles();
        if (everything != null) {
            for (File file : everything) {
                if (file.isDirectory()) {
                    System.out.println("Cleaning up repo details: " + file.getName());
                    deleteDirectory(file);
                }
            }
        }
        
        // Mark as complete
        progress.setMessage("Scan completed successfully");
        progress.setPercent(100);
        progress.setComplete(true);
        progress.setReports(reports);
        progress.setScanFolder(scanFolder);
    }
    
    private static String generateScanFolderName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        return "Scan_" + sdf.format(new Date());
    }
    
    private static boolean deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        return directory.delete();
    }
    
    /**
     * Progress tracking class
     */
    public static class ScanProgress {
        private final String scanId;
        private final String scanFolder;
        private String message = "";
        private int percent = 0;
        private boolean complete = false;
        private String error = null;
        private List<DiscoveryReport> reports = null;
        
        public ScanProgress(String scanId, String scanFolder) {
            this.scanId = scanId;
            this.scanFolder = scanFolder;
        }
        
        public synchronized void setMessage(String message) {
            this.message = message;
            System.out.println("[PROGRESS] " + percent + "% - " + message);
        }
        
        public synchronized void setPercent(int percent) {
            this.percent = percent;
        }
        
        public synchronized void setComplete(boolean complete) {
            this.complete = complete;
        }
        
        public synchronized void setError(String error) {
            this.error = error;
            this.complete = true;
        }
        
        public synchronized void setReports(List<DiscoveryReport> reports) {
            this.reports = reports;
        }
        
        public synchronized void setScanFolder(String scanFolder) {
            // Already set in constructor
        }
        
        // Getters
        public String getScanId() { return scanId; }
        public String getScanFolder() { return scanFolder; }
        public String getMessage() { return message; }
        public int getPercent() { return percent; }
        public boolean isComplete() { return complete; }
        public String getError() { return error; }
        public List<DiscoveryReport> getReports() { return reports; }
    }
}
