package com.raks.apidiscovery;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.raks.apidiscovery.connector.GitLabConnector;
import com.raks.apidiscovery.model.DiscoveryReport;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
public class ApiDiscoveryService {
    private static final Gson gson = new Gson();
    private static final ScannerEngine engine = new ScannerEngine();
    private static final Map<String, ScanProgress> activeScans = new ConcurrentHashMap<>();
    private static final ExecutorService scanExecutor = Executors.newFixedThreadPool(3);
    public static String startScan(String source, String token, String group, String tempDir) throws Exception {
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing 'source' parameter");
        }
        String scanId = UUID.randomUUID().toString();
        String scanFolder = generateScanFolderName();
        ScanProgress progress = new ScanProgress(scanId, scanFolder);
        activeScans.put(scanId, progress);
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
    public static ScanProgress getProgress(String scanId) {
        return activeScans.get(scanId);
    }
    
    // --- New Static Wrappers for Mule ---
    public static String fetchGroups(String baseUrl, String token) throws Exception {
        return GitService.fetchGroups(baseUrl, token);
    }

    public static String fetchProjects(String baseUrl, String token, String groupId) throws Exception {
        return GitService.fetchProjects(baseUrl, token, groupId);
    }

    public static List<Map<String, String>> correlateTraffic(String trafficData, String tempDir) {
        return TrafficCorrelator.correlate(trafficData, tempDir);
    }
    // ------------------------------------

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
        scanFolders.sort((a, b) -> Long.compare((Long)b.get("lastModified"), (Long)a.get("lastModified")));
        return scanFolders;
    }
    public static boolean deleteScan(String scanName, String tempDir) {
        if (scanName == null || scanName.trim().isEmpty()) {
            return false;
        }
        File scanFolder = new File(tempDir, scanName);
        if (scanFolder.exists() && scanFolder.isDirectory()) {
            return deleteDirectory(scanFolder, false);
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
    // --- Ignore List Management ---
    public static void ignoreItem(String item) { IgnoredRepoManager.add(item); }
    public static void removeIgnoredItem(String item) { IgnoredRepoManager.remove(item); }
    public static java.util.List<String> getIgnoredItems() { return IgnoredRepoManager.getList(); }

    // --- State Management ---
    public static String exportState(String tempDir) {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("ignored", IgnoredRepoManager.getList());
        state.put("history", listScans(tempDir));
        return gson.toJson(state);
    }
    
    public static void importState(String jsonState, String tempDir) {
        try {
            java.util.Map<String, Object> state = gson.fromJson(jsonState, new TypeToken<java.util.Map<String, Object>>(){}.getType());
            if (state.containsKey("ignored")) {
                java.util.List<String> ignored = (java.util.List<String>) state.get("ignored");
                for (String s : ignored) IgnoredRepoManager.add(s);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static List<DiscoveryReport> viewScan(String scanName, String tempDir) throws IOException {
        if (scanName == null || scanName.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing scan name");
        }
        File scanFolder = new File(tempDir, scanName);
        if (!scanFolder.exists() || !scanFolder.isDirectory()) {
            throw new IOException("Scan folder not found: " + scanName);
        }
        File resultsFile = new File(scanFolder, "scan_results.json");
        if (resultsFile.exists()) {
             try {
                String content = new String(java.nio.file.Files.readAllBytes(resultsFile.toPath()));
                return gson.fromJson(content, new TypeToken<List<DiscoveryReport>>(){}.getType());
             } catch (Exception e) {
                 System.err.println("Failed to parse consolidated results: " + e.getMessage());
             }
        }
        List<DiscoveryReport> reports = new ArrayList<>();
        File[] jsonFiles = scanFolder.listFiles((dir, name) -> name.endsWith(".json") && !name.equals("scan_results.json"));
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
    private static void performScan(String scanId, String source, String token, String scanFolder, ScanProgress progress, String tempDir) throws Exception {
        List<DiscoveryReport> reports = new ArrayList<>();
        File localFile = new File(source);
        progress.setMessage("Initializing scan...");
        progress.setPercent(5);
        if (localFile.exists() && localFile.isDirectory()) {
            // System.out.println("Detected Local Directory: " + source);
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
        else if (token != null && !token.isEmpty()) {
            // System.out.println("Detected GitLab Source: " + source);
            progress.setMessage("Connecting to GitLab...");
            progress.setPercent(10);
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
            reports = connector.scanGroup(cleanGroup, token, scanFolder, new File(tempDir), (msg, pct) -> {
                progress.setMessage(msg);
                progress.setPercent(pct);
            });
            
            // Check for ignored repositories in the result and adjust status
            for (DiscoveryReport r : reports) {
                if (IgnoredRepoManager.isIgnored(r.getRepoPath()) || IgnoredRepoManager.isIgnored(r.getRepoName())) {
                    r.setClassification("IGNORED");
                    r.setConfidenceScore(0);
                    r.setTechnology("Skipped by User");
                }
            }
        } else {
            throw new IllegalArgumentException("Source not found locally and no GitLab Token provided");
        }
        progress.setMessage("Saving results...");
        progress.setPercent(90);
        File outputDir = new File(tempDir, scanFolder);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        try {
            File resultsFile = new File(outputDir, "scan_results.json");
            String json = gson.toJson(reports);
            java.nio.file.Files.write(resultsFile.toPath(), json.getBytes());
            java.nio.file.Files.write(resultsFile.toPath(), json.getBytes());
            // System.out.println("Saved consolidated report: " + resultsFile.getAbsolutePath());
        } catch (IOException e) {
            // System.err.println("Failed to save report: " + e.getMessage());
        }
        File[] everything = outputDir.listFiles();
        if (everything != null) {
            for (File file : everything) {
                if (file.isDirectory()) {
                    boolean deleted = deleteDirectory(file, true);
                    if (!deleted) {
                        System.gc();
                        try { Thread.sleep(2000); } catch (InterruptedException e) {} 
                        if (!deleteDirectory(file, false)) {
                            try {
                                java.nio.file.Files.walk(file.toPath())
                                    .forEach(p -> System.err.println("  - Remaining: " + p));
                            } catch (IOException ex) {}
                        }
                    }
                }
            }
        }
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
    private static boolean deleteDirectory(File directory, boolean quiet) {
        if (!directory.exists()) return true;
        try {
            java.nio.file.Path path = directory.toPath();
            java.nio.file.Files.walkFileTree(path, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(java.nio.file.Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    try {
                        java.nio.file.Files.delete(file);
                    } catch (IOException e) {
                        if (!quiet) System.err.println("FAILED to delete file: " + file + " (" + e.getMessage() + ")");
                        throw e;
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
                @Override
                public java.nio.file.FileVisitResult postVisitDirectory(java.nio.file.Path dir, IOException exc) throws IOException {
                    try {
                        java.nio.file.Files.delete(dir);
                    } catch (IOException e) {
                        if (!quiet) System.err.println("FAILED to delete dir: " + dir + " (" + e.getMessage() + ")");
                        throw e;
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException e) {
            if (!quiet) System.err.println("Error deleting directory struct: " + e.getMessage());
            return false;
        }
    }
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
            // System.out.println("[PROGRESS] " + percent + "% - " + message);
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
        }
        public String getScanId() { return scanId; }
        public String getScanFolder() { return scanFolder; }
        public String getMessage() { return message; }
        public int getPercent() { return percent; }
        public boolean isComplete() { return complete; }
        public String getError() { return error; }
        public List<DiscoveryReport> getReports() { return reports; }
    }
}
