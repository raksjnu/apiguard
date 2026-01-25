package com.raks.apidiscovery;
import com.google.gson.Gson;
import com.raks.apidiscovery.model.DiscoveryReport;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.google.gson.reflect.TypeToken;
import com.raks.apidiscovery.connector.GitLabConnector;
public class ApiDiscoveryTool {
    private static int PORT = 8085;
    private static ScannerEngine engine;
    private static volatile String currentProgress = "";
    private static volatile int progressPercent = 0;
    private static volatile String currentScanFolder = "";
    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            runCliScan(args);
            return;
        }
        System.out.println("==========================================");
        System.out.println("   Raks ApiDiscovery Tool v1.0");
        System.out.println("==========================================");
        String portStr = loadConfig("server.port");
        if (portStr != null && !portStr.isEmpty()) {
            try {
                PORT = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                System.err.println("[WARN] Invalid server.port in config, using default: 8085");
            }
        }
        engine = new ScannerEngine();
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/scan", new ScanApiHandler());
        server.createContext("/api/scans/list", new ScanListHandler());
        server.createContext("/api/scans/delete", new ScanDeleteHandler());
        server.createContext("/api/scans/view", new ScanViewHandler());
        server.createContext("/api/progress", new ProgressHandler());
        server.createContext("/api/config", new ConfigHandler());
        // New Endpoints for Hierarchical Scanning & Correlation
        server.createContext("/api/gitlab/groups", new GitLabGroupsHandler());
        server.createContext("/api/gitlab/subgroups", new GitLabSubGroupsHandler());
        server.createContext("/api/gitlab/projects", new GitLabProjectsHandler());
        // Ignore List & State
        server.createContext("/api/ignore", new IgnoreListHandler());
        server.createContext("/api/state", new StateHandler());
        server.createContext("/api/correlate", new TrafficCorrelationHandler());
        
        server.setExecutor(null); 
        server.start();
        disableJGitMmap();
        System.out.println("Server started at http://localhost:" + PORT);
        System.out.println("Open your browser to access the dashboard.");
    }
    private static void disableJGitMmap() {
        try {
            org.eclipse.jgit.storage.file.WindowCacheConfig config = new org.eclipse.jgit.storage.file.WindowCacheConfig();
            config.setPackedGitMMAP(false);
            config.install();
        } catch (Exception e) {
            System.err.println("[INIT] Failed to configure JGit: " + e.getMessage());
        }
    }
    private static void runCliScan(String[] args) {
        String source = null;
        String token = null;
        String output = "scan_results.json";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss");
        String scanId = "Scan_" + sdf.format(new java.util.Date());
        for (int i = 0; i < args.length; i++) {
            if ("-source".equals(args[i]) && i + 1 < args.length) {
                source = args[i + 1];
                i++;
            } else if ("-token".equals(args[i]) && i + 1 < args.length) {
                token = args[i + 1];
                i++;
            } else if ("-output".equals(args[i]) && i + 1 < args.length) {
                output = args[i + 1];
                i++;
            } else if ("-help".equals(args[i])) {
                printHelp();
                return;
            }
        }
        if (source == null) {
            System.err.println("Error: -source argument is required.");
            printHelp();
            System.exit(1);
        }
        System.out.println("==========================================");
        System.out.println("   Raks ApiDiscovery CLI");
        System.out.println("==========================================");
        System.out.println("Source: " + source);
        System.out.println("Output: " + output);
        System.out.println("------------------------------------------");
        disableJGitMmap();
        engine = new ScannerEngine();
        try {
            List<DiscoveryReport> reports = new ArrayList<>();
            File localFile = new File(source);
            if (localFile.exists() && localFile.isDirectory()) {
                System.out.println("[INFO] Scanning local directory...");
                reports.add(engine.scanRepository(localFile));
                File[] subs = localFile.listFiles(File::isDirectory);
                if (subs != null) {
                    int count = 0;
                    for (File sub : subs) {
                         if (!sub.getName().startsWith(".")) {
                             count++;
                             System.out.print("\r[INFO] Scanning sub-folder " + count + "/" + subs.length + ": " + sub.getName() + "          ");
                             reports.add(engine.scanRepository(sub));
                         }
                    }
                    System.out.println(); 
                }
            } 
            else if (source.startsWith("http")) {
                 if (token == null || token.isEmpty()) {
                     token = loadConfig("gitlab.token");
                 }
                 if (token == null || token.isEmpty()) {
                     System.err.println("[ERROR] Token required for GitLab scan. Use -token <token>.");
                     System.exit(1);
                 }
                 System.out.println("[INFO] Connecting to GitLab...");
                 GitLabConnector connector = new GitLabConnector();
                 java.util.function.BiConsumer<String, Integer> cliProgress = (msg, pct) -> {
                     System.out.print("\r[PROGRESS] " + pct + "% - " + msg + "                         ");
                 };
                 reports = connector.scanGroup(source, token, scanId, getTempDir(), cliProgress);
                 System.out.println(); 
            } else {
                System.err.println("[ERROR] Invalid source. Must be a directory path or URL.");
                System.exit(1);
            }
            Gson gson = new Gson();
            String jsonResults = gson.toJson(reports);
            File outputFile = new File(output);
            if (outputFile.exists() && outputFile.isDirectory()) {
                File subDir = new File(outputFile, scanId);
                subDir.mkdirs();
                outputFile = new File(subDir, "scan_results.json");
            } else if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }
            java.nio.file.Files.write(outputFile.toPath(), jsonResults.getBytes(StandardCharsets.UTF_8));
            System.out.println("[SUCCESS] Results saved to: " + outputFile.getAbsolutePath());
            printSummary(reports);
        } catch (Exception e) {
            System.err.println("[ERROR] Scan failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    private static void printHelp() {
        System.out.println("Usage: java -jar apidiscovery.jar -source <path_or_url> [-token <token>] [-output <file_path>]");
        System.out.println("Options:");
        System.out.println("  -source  : Local directory path or GitLab Group URL");
        System.out.println("  -token   : GitLab API Token (required for URL)");
        System.out.println("  -output  : Path to save JSON results (default: scan_results.json)");
    }
    private static void printSummary(List<DiscoveryReport> reports) {
        System.out.println("\n-------------------------------------------------------------");
        System.out.printf("%-40s | %-30s%n", "Project Name", "Technology");
        System.out.println("-------------------------------------------------------------");
        for (DiscoveryReport r : reports) {
            String name = r.getRepoName();
            if (name == null) name = "Unknown";
            if (name.length() > 37) name = name.substring(0, 37) + "..";
            String tech = r.getTechnology();
            if (tech == null) tech = "N/A";
            System.out.printf("%-40s | %-30s%n", name, tech);
        }
        System.out.println("-------------------------------------------------------------");
        System.out.println("Total Projects: " + reports.size());
    }
    public static void updateProgress(String message, int percent) {
        currentProgress = message;
        progressPercent = percent;
        System.out.println("[PROGRESS] " + percent + "% - " + message);
    }
    public static void setScanFolder(String folderName) {
        currentScanFolder = folderName;
    }
    public static String getScanFolderName() {
        return currentScanFolder;
    }
    public static void clearProgress() {
        currentProgress = "";
        progressPercent = 0;
    }
    private static boolean deleteDirectory(File directory) {
        if (!directory.exists()) return true;
        
        // Hint to GC to release file handles
        System.gc();
        
        for (int i = 0; i < 3; i++) {
            try {
                java.nio.file.Path path = directory.toPath();
                java.nio.file.Files.walkFileTree(path, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                    @Override
                    public java.nio.file.FileVisitResult visitFile(java.nio.file.Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                        java.nio.file.Files.delete(file);
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                    @Override
                    public java.nio.file.FileVisitResult postVisitDirectory(java.nio.file.Path dir, IOException exc) throws IOException {
                        java.nio.file.Files.delete(dir);
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                });
                return true;
            } catch (IOException e) {
                if (i == 2) {
                    System.err.println("[CLEANUP] Final Failure deleting " + directory.getName() + ": " + e.getMessage());
                    return false;
                }
                try { Thread.sleep(200); } catch (InterruptedException ie) {}
                System.gc();
            }
        }
        return false;
    }
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String path = t.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            InputStream is = getClass().getResourceAsStream("/web" + path);
            if (is == null) {
                String response = "404 Not Found";
                t.sendResponseHeaders(404, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                byte[] bytes = is.readAllBytes();
                t.getResponseHeaders().add("Cache-Control", "no-cache, no-store, must-revalidate");
                t.getResponseHeaders().add("Pragma", "no-cache");
                t.getResponseHeaders().add("Expires", "0");
                if (path.endsWith(".html")) t.getResponseHeaders().set("Content-Type", "text/html");
                else if (path.endsWith(".css")) t.getResponseHeaders().set("Content-Type", "text/css");
                else if (path.endsWith(".js")) t.getResponseHeaders().set("Content-Type", "application/javascript");
                else if (path.endsWith(".json")) t.getResponseHeaders().set("Content-Type", "application/json");
                else if (path.endsWith(".svg")) t.getResponseHeaders().set("Content-Type", "image/svg+xml");
                else if (path.endsWith(".png")) t.getResponseHeaders().set("Content-Type", "image/png");
                else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) t.getResponseHeaders().set("Content-Type", "image/jpeg");
                t.sendResponseHeaders(200, bytes.length);
                OutputStream os = t.getResponseBody();
                os.write(bytes);
                os.close();
            }
        }
    }
    static class ScanApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(t.getRequestMethod())) {
                t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                t.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
                t.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                t.sendResponseHeaders(204, -1);
                return;
            }
            if ("POST".equalsIgnoreCase(t.getRequestMethod())) {
                try {
                    InputStreamReader isr = new InputStreamReader(t.getRequestBody(), StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr);
                    StringBuilder jsonBuilder = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        jsonBuilder.append(line);
                    }
                    Gson gson = new Gson();
                    Map<String, String> request = gson.fromJson(jsonBuilder.toString(), new TypeToken<Map<String, String>>(){}.getType());
                    String source = request.getOrDefault("source", request.get("path")); 
                    String token = request.get("token");
                    if ((token == null || token.trim().isEmpty())) {
                        token = loadConfig("gitlab.token");
                    }
                    if (source == null || source.trim().isEmpty()) {
                        sendError(t, 400, "{\"error\": \"Missing 'source' parameter\"}");
                        return;
                    }
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss");
                    String scanId = "Scan_" + sdf.format(new java.util.Date());
                    String finalToken = token;
                    new Thread(() -> {
                        runAsyncScan(source, finalToken, scanId);
                    }).start();
                    Map<String, Object> response = new java.util.HashMap<>();
                    response.put("status", "started");
                    response.put("scanId", scanId);
                    response.put("message", "Scan started in background");
                    String jsonResponse = gson.toJson(response);
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                    t.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = t.getResponseBody()) {
                        os.write(bytes);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    sendError(t, 500, "{\"error\": \"Internal Server Error: " + e.getMessage() + "\"}");
                }
            } else {
                sendError(t, 405, "{\"error\": \"Method Not Allowed\"}");
            }
        }
        private void sendError(HttpExchange t, int code, String jsonMessage) throws IOException {
             t.getResponseHeaders().set("Content-Type", "application/json");
             byte[] bytes = jsonMessage.getBytes(StandardCharsets.UTF_8);
             t.sendResponseHeaders(code, bytes.length);
             OutputStream os = t.getResponseBody();
             os.write(bytes);
             os.close();
        }
    }
    private static void runAsyncScan(String source, String token, String scanId) {
        System.out.println("Starting Async Scan: " + scanId);

        // --- Register Scan Progress for UI Polling ---
        // Ensure the folder name matches scanId if not set otherwise
        setScanFolder(scanId);
        ApiDiscoveryService.registerScan(scanId, scanId);
        ApiDiscoveryService.ScanProgress progress = ApiDiscoveryService.getProgress(scanId);
        // ----------------------------------------------

        updateProgress("Initializing scan...", 0);
        if (progress != null) { progress.setMessage("Initializing scan..."); progress.setPercent(0); }

        try {
            List<DiscoveryReport> reports = new ArrayList<>();
            Gson gson = new Gson();
            List<String> sources = new ArrayList<>();
            if (source.trim().startsWith("[")) {
                try {
                    sources = gson.fromJson(source, new TypeToken<List<String>>(){}.getType());
                } catch (Exception e) {
                    sources.add(source);
                }
            } else {
                sources.add(source);
            }

            for (int i = 0; i < sources.size(); i++) {
                String s = sources.get(i);
                int basePct = (i * 90 / sources.size());
                int nextBasePct = ((i + 1) * 90 / sources.size());
                
                final int currentIdx = i;
                final int totalSources = sources.size();
                
                File localFile = new File(s);
                if (localFile.exists() && localFile.isDirectory()) {
                    System.out.println("Detected Local Directory: " + s);
                    if (progress != null) {
                        progress.setMessage("[" + (i+1) + "/" + totalSources + "] Scanning local directory...");
                        progress.setPercent(basePct + 5);
                    }
                    reports.add(engine.scanRepository(localFile));
                    File[] subs = localFile.listFiles(File::isDirectory);
                    if (subs != null) {
                        for (File sub : subs) {
                            if (!sub.getName().startsWith(".")) {
                                reports.add(engine.scanRepository(sub));
                            }
                        }
                    }
                } else if (token != null && !token.isEmpty()) {
                    System.out.println("Detected GitLab Source: " + s);
                    String cleanGroup = s.trim();
                    if (cleanGroup.startsWith("https://gitlab.com/")) cleanGroup = cleanGroup.substring("https://gitlab.com/".length());
                    else if (cleanGroup.startsWith("http://gitlab.com/")) cleanGroup = cleanGroup.substring("http://gitlab.com/".length());
                    
                    if (cleanGroup.endsWith("/")) cleanGroup = cleanGroup.substring(0, cleanGroup.length() - 1);
                    if (cleanGroup.contains("#")) cleanGroup = cleanGroup.substring(0, cleanGroup.indexOf("#"));
                    
                    GitLabConnector connector = new GitLabConnector();
                    
                    List<DiscoveryReport> groupResults = connector.scanGroup(cleanGroup, token, scanId, getTempDir(), (msg, pct) -> {
                        int scaledPct = basePct + (pct * (nextBasePct - basePct) / 100);
                        if (progress != null) {
                            progress.setMessage("[" + (currentIdx+1) + "/" + totalSources + "] " + msg);
                            progress.setPercent(scaledPct);
                        }
                    });
                    reports.addAll(groupResults);
                }
            }
            try {
                String folderName = scanId; // Use scanId as folder name
                File tempDir = getTempDir();
                if (!tempDir.exists()) tempDir.mkdirs();
                File scanDir = new File(tempDir, folderName);
                if (!scanDir.exists()) scanDir.mkdirs();
                String jsonResults = gson.toJson(reports);
                java.nio.file.Files.write(
                    new File(scanDir, "scan_results.json").toPath(), 
                    jsonResults.getBytes(StandardCharsets.UTF_8)
                );
                
                // Save Metadata
                Map<String, Object> meta = new java.util.HashMap<>();
                meta.put("source", source);
                meta.put("timestamp", System.currentTimeMillis());
                meta.put("count", reports.size());
                meta.put("type", (reports.size() > 1) ? "API Discovery" : "Quick Scan");
                java.nio.file.Files.write(
                    new File(scanDir, "scan_metadata.json").toPath(), 
                    gson.toJson(meta).getBytes(StandardCharsets.UTF_8)
                );
                
                System.out.println("Saved scan results and metadata to: " + scanDir.getAbsolutePath());
            } catch (Exception e) {
                System.err.println("Failed to save scan results: " + e.getMessage());
                e.printStackTrace();
            }
            
            updateProgress("Finalizing results...", 95);
            if (progress != null) { progress.setMessage("Finalizing results..."); progress.setPercent(95); }
            
            try {
                org.eclipse.jgit.lib.RepositoryCache.clear();
            } catch (Exception e) {
                System.err.println("[CLEANUP] Failed to clear JGit cache: " + e.getMessage());
            }
            try {
                 File tempDir = getTempDir();
                 File scanDir = new File(tempDir, scanId);
                     // Preserving repositories for Traffic Correlation feature
            } catch (Exception e) {
                System.err.println("Cleanup failed: " + e.getMessage());
            }
            updateProgress("Scan complete!", 100);
            if (progress != null) { 
                progress.setMessage("Scan complete!"); 
                progress.setPercent(100); 
                progress.setComplete(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
            updateProgress("Error: " + e.getMessage(), 0);
            if (progress != null) { progress.setError(e.getMessage()); }
        }
    }
    private static String loadConfig(String key) {
        try (InputStream input = ApiDiscoveryTool.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) return null;
            java.util.Properties prop = new java.util.Properties();
            prop.load(input);
            return prop.getProperty(key);
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }
    static class ScanListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equalsIgnoreCase(t.getRequestMethod())) {
                try {
                    File tempDir = getTempDir();
                    List<Map<String, Object>> scanFolders = new ArrayList<>();
                    if (tempDir.exists() && tempDir.isDirectory()) {
                        File[] folders = tempDir.listFiles(File::isDirectory);
                        if (folders != null) {
                            for (File folder : folders) {
                                if (folder.getName().toLowerCase().startsWith("scan")) {
                                    Map<String, Object> info = new java.util.HashMap<>();
                                    info.put("name", folder.getName());
                                    info.put("path", folder.getAbsolutePath());
                                    info.put("lastModified", folder.lastModified());
                                    scanFolders.add(info);
                                }
                            }
                        }
                    }
                    scanFolders.sort((a, b) -> Long.compare((Long)b.get("lastModified"), (Long)a.get("lastModified")));
                    Gson gson = new Gson();
                    String jsonResponse = gson.toJson(scanFolders);
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.sendResponseHeaders(200, jsonResponse.getBytes().length);
                    try (OutputStream os = t.getResponseBody()) {
                        os.write(jsonResponse.getBytes());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    sendError(t, 500, "{\"error\": \"Failed to list scans\"}");
                }
            } else {
                sendError(t, 405, "{\"error\": \"Method Not Allowed\"}");
            }
        }
        private void sendError(HttpExchange t, int code, String jsonMessage) throws IOException {
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(code, jsonMessage.length());
            OutputStream os = t.getResponseBody();
            os.write(jsonMessage.getBytes());
            os.close();
        }
    }
    static class ScanDeleteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equalsIgnoreCase(t.getRequestMethod())) {
                try {
                    InputStreamReader isr = new InputStreamReader(t.getRequestBody(), StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr);
                    StringBuilder jsonBuilder = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        jsonBuilder.append(line);
                    }
                    Gson gson = new Gson();
                    Map<String, Object> request = gson.fromJson(jsonBuilder.toString(), new TypeToken<Map<String, Object>>(){}.getType());
                    List<String> folderNames = (List<String>) request.get("folders");
                    Map<String, Object> result = new java.util.HashMap<>();
                    List<String> deleted = new ArrayList<>();
                    List<String> failed = new ArrayList<>();
                    File tempDir = getTempDir();
                    for (String folderName : folderNames) {
                        File folder = new File(tempDir, folderName);
                        if (folder.exists() && folder.isDirectory() && folderName.toLowerCase().startsWith("scan")) {
                            if (deleteDirectory(folder)) {
                                deleted.add(folderName);
                            } else {
                                failed.add(folderName);
                            }
                        } else {
                            failed.add(folderName);
                        }
                    }
                    result.put("deleted", deleted);
                    result.put("failed", failed);
                    result.put("status", "success");
                    String jsonResponse = gson.toJson(result);
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.sendResponseHeaders(200, jsonResponse.getBytes().length);
                    try (OutputStream os = t.getResponseBody()) {
                        os.write(jsonResponse.getBytes());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    sendError(t, 500, "{\"error\": \"Failed to delete scans\"}");
                }
            } else {
                sendError(t, 405, "{\"error\": \"Method Not Allowed\"}");
            }
        }
        private void sendError(HttpExchange t, int code, String jsonMessage) throws IOException {
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(code, jsonMessage.length());
            OutputStream os = t.getResponseBody();
            os.write(jsonMessage.getBytes());
            os.close();
        }
    }
    static class ScanViewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equalsIgnoreCase(t.getRequestMethod())) {
                try {
                    String query = t.getRequestURI().getQuery();
                    String scanName = null;
                    String fileName = "scan_results.json";
                    if (query != null) {
                        for (String param : query.split("&")) {
                            String[] pair = param.split("=");
                            if (pair.length == 2) {
                                if ("scan".equals(pair[0]) || "scanName".equals(pair[0])) {
                                    scanName = pair[1];
                                } else if ("file".equals(pair[0]) || "fileName".equals(pair[0])) {
                                    fileName = pair[1];
                                }
                            }
                        }
                    }
                    if (scanName == null || scanName.isEmpty()) {
                        sendError(t, 400, "{\"error\": \"Missing scan parameter\"}");
                        return;
                    }
                    File tempDir = getTempDir();
                    File scanFolder = new File(tempDir, scanName);
                    if (!scanFolder.exists() || !scanFolder.isDirectory() || !scanName.toLowerCase().startsWith("scan")) {
                        sendError(t, 404, "{\"error\": \"Scan folder not found\"}");
                        return;
                    }
                    File resultsFile = new File(scanFolder, fileName);
                    if (resultsFile.exists() && !fileName.contains("..")) {
                         byte[] bytes = java.nio.file.Files.readAllBytes(resultsFile.toPath());
                         t.getResponseHeaders().set("Content-Type", "application/json");
                         t.sendResponseHeaders(200, bytes.length);
                         try (OutputStream os = t.getResponseBody()) {
                             os.write(bytes);
                         }
                         return;
                    }
                    List<DiscoveryReport> reports = new ArrayList<>();
                    File[] repos = scanFolder.listFiles(File::isDirectory);
                    if (repos != null) {
                        for (File repo : repos) {
                            if (!repo.getName().startsWith(".")) {
                                DiscoveryReport report = engine.scanRepository(repo);
                                report.setRepoName(repo.getName());
                                report.setRepoPath(repo.getAbsolutePath());
                                reports.add(report);
                            }
                        }
                    }
                    Gson gson = new Gson();
                    String jsonResponse = gson.toJson(reports);
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.sendResponseHeaders(200, jsonResponse.getBytes().length);
                    try (OutputStream os = t.getResponseBody()) {
                        os.write(jsonResponse.getBytes());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    sendError(t, 500, "{\"error\": \"Failed to load scan results\"}");
                }
            } else {
                sendError(t, 405, "{\"error\": \"Method Not Allowed\"}");
            }
        }
        private void sendError(HttpExchange t, int code, String jsonMessage) throws IOException {
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(code, jsonMessage.length());
            OutputStream os = t.getResponseBody();
            os.write(jsonMessage.getBytes());
            os.close();
        }
    }
    static class ProgressHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equalsIgnoreCase(t.getRequestMethod())) {
                String query = t.getRequestURI().getQuery();
                String scanId = null;
                if (query != null && query.contains("scanId=")) {
                    for (String param : query.split("&")) {
                        if (param.startsWith("scanId=")) {
                            scanId = param.substring(7);
                            break;
                        }
                    }
                }
                
                Map<String, Object> progress = new java.util.HashMap<>();
                if (scanId != null) {
                    ApiDiscoveryService.ScanProgress p = ApiDiscoveryService.getProgress(scanId);
                    if (p != null) {
                        progress.put("message", p.getMessage());
                        progress.put("percent", p.getPercent());
                        progress.put("complete", p.isComplete());
                        progress.put("scanFolder", p.getScanFolder());
                    } else {
                        progress.put("message", "Scan not found");
                        progress.put("percent", 0);
                        progress.put("complete", false);
                    }
                } else {
                    // Fallback to global for legacy or CLI
                    progress.put("message", currentProgress);
                    progress.put("percent", progressPercent);
                    progress.put("complete", progressPercent >= 100);
                }
                
                Gson gson = new Gson();
                String jsonResponse = gson.toJson(progress);
                t.getResponseHeaders().set("Content-Type", "application/json");
                byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                t.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = t.getResponseBody()) {
                    os.write(bytes);
                }
            }
        }
    }
    static class ConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equalsIgnoreCase(t.getRequestMethod())) {
                Map<String, String> config = new java.util.HashMap<>();
                config.put("defaultGroup", loadConfig("gitlab.default.group"));
                config.put("defaultToken", loadConfig("gitlab.token"));
                Gson gson = new Gson();
                String jsonResponse = gson.toJson(config);
                t.getResponseHeaders().set("Content-Type", "application/json");
                byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                t.sendResponseHeaders(200, bytes.length);
                OutputStream os = t.getResponseBody();
                os.write(bytes);
                os.close();
            } else {
                 String jsonMessage = "{\"error\": \"Method Not Allowed\"}";
                 t.getResponseHeaders().set("Content-Type", "application/json");
                 byte[] bytes = jsonMessage.getBytes(StandardCharsets.UTF_8);
                 t.sendResponseHeaders(405, bytes.length);
                 OutputStream os = t.getResponseBody();
                 os.write(bytes);
                 os.close();
            }
        }
    }
    public static File getTempDir() {
        String customHome = System.getProperty("apidiscovery.home");
        File baseDir = (customHome != null && !customHome.isEmpty()) ? new File(customHome) : new File(".");
        return new File(baseDir, "temp");
    }

    // --- New Handlers ---

    static class GitLabGroupsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equalsIgnoreCase(t.getRequestMethod())) {
                try {
                    String query = t.getRequestURI().getQuery();
                    String token = null;
                    String baseUrl = "https://gitlab.com"; 

                    if (query != null) {
                        for (String param : query.split("&")) {
                            String[] pair = param.split("=");
                            if (pair.length == 2) {
                                if ("token".equals(pair[0])) token = pair[1];
                                else if ("baseUrl".equals(pair[0])) baseUrl = java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                            }
                        }
                    }
                    if (token == null) token = loadConfig("gitlab.token");
                    
                    String json = GitService.fetchGroups(baseUrl, token);
                    sendResponse(t, 200, json);
                } catch (Exception e) {
                    e.printStackTrace();
                    sendError(t, 500, "{\"error\": \"" + e.getMessage() + "\"}");
                }
            } else {
                sendError(t, 405, "{\"error\": \"Method Not Allowed\"}");
            }
        }
        private void sendResponse(HttpExchange t, int code, String json) throws IOException {
             t.getResponseHeaders().set("Content-Type", "application/json");
             byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
             t.sendResponseHeaders(code, bytes.length);
             OutputStream os = t.getResponseBody();
             os.write(bytes);
             os.close();
        }
        private void sendError(HttpExchange t, int code, String json) throws IOException {
             sendResponse(t, code, json);
        }
    }

    static class GitLabSubGroupsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equalsIgnoreCase(t.getRequestMethod())) {
                try {
                    String query = t.getRequestURI().getQuery();
                    String token = null;
                    String groupId = null;
                    String baseUrl = "https://gitlab.com";

                    if (query != null) {
                        for (String param : query.split("&")) {
                            String[] pair = param.split("=");
                            if (pair.length == 2) {
                                if ("token".equals(pair[0])) token = pair[1];
                                else if ("groupId".equals(pair[0])) groupId = pair[1];
                                else if ("baseUrl".equals(pair[0])) baseUrl = java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                            }
                        }
                    }
                    if (token == null) token = loadConfig("gitlab.token");
                    if (groupId == null) {
                        sendError(t, 400, "{\"error\": \"Missing groupId parameter\"}");
                        return;
                    }

                    String json = GitService.fetchSubGroups(baseUrl, token, groupId);
                    sendResponse(t, 200, json);
                } catch (Exception e) {
                    e.printStackTrace();
                    sendError(t, 500, "{\"error\": \"" + e.getMessage() + "\"}");
                }
            } else {
                sendError(t, 405, "{\"error\": \"Method Not Allowed\"}");
            }
        }
        private void sendResponse(HttpExchange t, int code, String json) throws IOException {
             t.getResponseHeaders().set("Content-Type", "application/json");
             byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
             t.sendResponseHeaders(code, bytes.length);
             OutputStream os = t.getResponseBody();
             os.write(bytes);
             os.close();
        }
        private void sendError(HttpExchange t, int code, String json) throws IOException {
             sendResponse(t, code, json);
        }
    }

    static class GitLabProjectsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equalsIgnoreCase(t.getRequestMethod())) {
                try {
                    String query = t.getRequestURI().getQuery();
                    String token = null;
                    String groupId = null;
                    String baseUrl = "https://gitlab.com";

                    if (query != null) {
                        for (String param : query.split("&")) {
                            String[] pair = param.split("=");
                            if (pair.length == 2) {
                                if ("token".equals(pair[0])) token = pair[1];
                                else if ("groupId".equals(pair[0])) groupId = pair[1];
                                else if ("baseUrl".equals(pair[0])) baseUrl = java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                            }
                        }
                    }
                    if (token == null) token = loadConfig("gitlab.token");
                    if (groupId == null) {
                        sendError(t, 400, "{\"error\": \"Missing groupId parameter\"}");
                        return;
                    }

                    String json = GitService.fetchProjects(baseUrl, token, groupId);
                    sendResponse(t, 200, json);
                } catch (Exception e) {
                    e.printStackTrace();
                    sendError(t, 500, "{\"error\": \"" + e.getMessage() + "\"}");
                }
            } else {
                sendError(t, 405, "{\"error\": \"Method Not Allowed\"}");
            }
        }
        private void sendResponse(HttpExchange t, int code, String json) throws IOException {
             t.getResponseHeaders().set("Content-Type", "application/json");
             byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
             t.sendResponseHeaders(code, bytes.length);
             OutputStream os = t.getResponseBody();
             os.write(bytes);
             os.close();
        }
        private void sendError(HttpExchange t, int code, String json) throws IOException {
             sendResponse(t, code, json);
        }
    }

    static class TrafficCorrelationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equalsIgnoreCase(t.getRequestMethod())) {
                try {
                    InputStreamReader isr = new InputStreamReader(t.getRequestBody(), StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr);
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line).append("\n");
                    
                    String trafficData = sb.toString();
                    // If JSON input, extract a specific field? For now assume raw text or JSON body is the traffic list
                    // Actually, let's assume raw text payload for simplicity as per plan "Paste IPs"
                    
                    List<Map<String, Object>> results = TrafficCorrelator.correlate(trafficData, getTempDir().getAbsolutePath());
                    
                    Gson gson = new Gson();
                    String jsonResponse = gson.toJson(results);
                    
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                    t.sendResponseHeaders(200, bytes.length);
                    OutputStream os = t.getResponseBody();
                    os.write(bytes);
                    os.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    t.sendResponseHeaders(500, 0);
                    t.getResponseBody().close();
                }
            } else {
                t.sendResponseHeaders(405, 0);
                t.getResponseBody().close();
            }
        }
    }

    static class IgnoreListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                if ("GET".equalsIgnoreCase(t.getRequestMethod())) {
                    List<String> list = ApiDiscoveryService.getIgnoredItems();
                    sendResponse(t, 200, new Gson().toJson(list));
                } else if ("POST".equalsIgnoreCase(t.getRequestMethod())) {
                    String item = readBody(t);
                    ApiDiscoveryService.ignoreItem(item);
                    sendResponse(t, 200, "{\"status\": \"ok\", \"message\": \"Item ignored\"}");
                } else if ("DELETE".equalsIgnoreCase(t.getRequestMethod())) {
                    String item = readBody(t);
                    ApiDiscoveryService.removeIgnoredItem(item);
                    sendResponse(t, 200, "{\"status\": \"ok\", \"message\": \"Item removed from ignore list\"}");
                } else {
                    sendError(t, 405, "Method Not Allowed");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendError(t, 500, e.getMessage());
            }
        }
        private String readBody(HttpExchange t) throws IOException {
             InputStreamReader isr = new InputStreamReader(t.getRequestBody(), StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr);
             StringBuilder sb = new StringBuilder();
             String line;
             while ((line = br.readLine()) != null) sb.append(line);
             return sb.toString();
        }
        private void sendResponse(HttpExchange t, int code, String json) throws IOException {
             t.getResponseHeaders().set("Content-Type", "application/json");
             byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
             t.sendResponseHeaders(code, bytes.length);
             OutputStream os = t.getResponseBody();
             os.write(bytes);
             os.close();
        }
        private void sendError(HttpExchange t, int code, String msg) throws IOException {
             sendResponse(t, code, "{\"error\": \"" + msg + "\"}");
        }
    }

    static class StateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                if ("GET".equalsIgnoreCase(t.getRequestMethod())) { // EXPORT
                    String json = ApiDiscoveryService.exportState(ApiDiscoveryTool.getTempDir().getAbsolutePath());
                    sendResponse(t, 200, json);
                } else if ("POST".equalsIgnoreCase(t.getRequestMethod())) { // IMPORT
                    String json = readBody(t);
                    ApiDiscoveryService.importState(json, ApiDiscoveryTool.getTempDir().getAbsolutePath());
                    sendResponse(t, 200, "{\"status\": \"import_success\"}");
                } else {
                    sendError(t, 405, "Method Not Allowed");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendError(t, 500, e.getMessage());
            }
        }
        private String readBody(HttpExchange t) throws IOException {
             InputStreamReader isr = new InputStreamReader(t.getRequestBody(), StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr);
             StringBuilder sb = new StringBuilder();
             String line;
             while ((line = br.readLine()) != null) sb.append(line);
             return sb.toString();
        }
        private void sendResponse(HttpExchange t, int code, String json) throws IOException {
             t.getResponseHeaders().set("Content-Type", "application/json");
             byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
             t.sendResponseHeaders(code, bytes.length);
             OutputStream os = t.getResponseBody();
             os.write(bytes);
             os.close();
        }
        private void sendError(HttpExchange t, int code, String msg) throws IOException {
             sendResponse(t, code, "{\"error\": \"" + msg + "\"}");
        }
    }
}
