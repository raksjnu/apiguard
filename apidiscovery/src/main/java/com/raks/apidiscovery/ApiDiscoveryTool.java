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
        System.out.println("==========================================");
        System.out.println("   Raks ApiDiscovery Tool v1.0");
        System.out.println("==========================================");
        
        // Load port from config
        String portStr = loadConfig("server.port");
        if (portStr != null && !portStr.isEmpty()) {
            try {
                PORT = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                System.err.println("[WARN] Invalid server.port in config, using default: 8085");
            }
        }
        
        engine = new ScannerEngine();

        // Start HTTP Server
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        // Contexts
        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/scan", new ScanApiHandler());
        server.createContext("/api/scans/list", new ScanListHandler());
        server.createContext("/api/scans/delete", new ScanDeleteHandler());
        server.createContext("/api/scans/view", new ScanViewHandler());
        server.createContext("/api/progress", new ProgressHandler());
        
        server.setExecutor(null); // default executor
        server.start();
        
        System.out.println("Server started at http://localhost:" + PORT);
        System.out.println("Open your browser to access the dashboard.");
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

    // Handler for Serving UI (index.html)
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String path = t.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            
            // Allow basic resource loading
            InputStream is = getClass().getResourceAsStream("/web" + path);
            

            if (is == null) {
                String response = "404 Not Found";
                t.sendResponseHeaders(404, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                byte[] bytes = is.readAllBytes();
                // Add Cache-Control to prevent browser caching of old UI
                t.getResponseHeaders().add("Cache-Control", "no-cache, no-store, must-revalidate");
                t.getResponseHeaders().add("Pragma", "no-cache");
                t.getResponseHeaders().add("Expires", "0");
                
                t.sendResponseHeaders(200, bytes.length);
                OutputStream os = t.getResponseBody();
                os.write(bytes);
                os.close();
            }
        }
    }

    // Handler for Scanning API
    static class ScanApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            // Handle CORS (if needed) or just POST
            if ("OPTIONS".equalsIgnoreCase(t.getRequestMethod())) {
                t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                t.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
                t.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                t.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equalsIgnoreCase(t.getRequestMethod())) {
                try {
                    // Read Request Body
                    InputStreamReader isr = new InputStreamReader(t.getRequestBody(), StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr);
                    StringBuilder jsonBuilder = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        jsonBuilder.append(line);
                    }
                    
                    Gson gson = new Gson();
                    Map<String, String> request = gson.fromJson(jsonBuilder.toString(), new TypeToken<Map<String, String>>(){}.getType());
                    List<DiscoveryReport> reports = new ArrayList<>();
                    
                    String source = request.getOrDefault("source", request.get("path")); 
                    String token = request.get("token");
                    
                    // Fallback to default token from config
                    if ((token == null || token.trim().isEmpty())) {
                        token = loadConfig("gitlab.token");
                    }
                    
                    if (source == null || source.trim().isEmpty()) {
                        sendError(t, 400, "{\"error\": \"Missing 'source' parameter\"}");
                        return;
                    }
                    
                    File localFile = new File(source);
                    
                    // 1. Check if it's a valid local directory
                    if (localFile.exists() && localFile.isDirectory()) {
                        System.out.println("Detected Local Directory: " + source);
                        reports.add(engine.scanRepository(localFile));
                        
                        File[] subs = localFile.listFiles(File::isDirectory);
                        if (subs != null) {
                            for (File sub : subs) {
                                if (!sub.getName().startsWith(".")) {
                                    reports.add(engine.scanRepository(sub));
                                }
                            }
                        }
                    } 
                    // 2. Otherwise, treat as GitLab (if token provided)
                    else if (token != null && !token.isEmpty()) {
                        System.out.println("Detected GitLab Source: " + source);
                        
                        // Clean URL
                        String cleanGroup = source.trim();
                        if (cleanGroup.startsWith("https://gitlab.com/")) {
                            cleanGroup = cleanGroup.substring("https://gitlab.com/".length());
                        } else if (cleanGroup.startsWith("http://gitlab.com/")) {
                            cleanGroup = cleanGroup.substring("http://gitlab.com/".length());
                        }
                        if (cleanGroup.endsWith("/")) cleanGroup = cleanGroup.substring(0, cleanGroup.length() - 1);
                        if (cleanGroup.contains("#")) cleanGroup = cleanGroup.substring(0, cleanGroup.indexOf("#"));
                        
                        try {
                            GitLabConnector connector = new GitLabConnector();
                            reports = connector.scanGroup(cleanGroup, token);
                        } catch (Exception e) {
                            e.printStackTrace();
                            sendError(t, 500, "{\"error\": \"GitLab Scan Failed: " + e.getMessage().replace("\"", "'") + "\"}");
                            return;
                        }
                    } else {
                         sendError(t, 400, "{\"error\": \"Source not found locally (path does not exist) and no GitLab Token provided.\"}");
                         return;
                    }

                    // Add scan folder info to response
                    Map<String, Object> response = new java.util.HashMap<>();
                    response.put("reports", reports);
                    response.put("scanFolder", getScanFolderName());
                    
                    String jsonResponse = gson.toJson(response);
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.sendResponseHeaders(200, jsonResponse.getBytes().length);
                    try (OutputStream os = t.getResponseBody()) {
                        os.write(jsonResponse.getBytes());
                    }
                    
                    // Clear progress after scan completes
                    clearProgress();
                    
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
             t.sendResponseHeaders(code, jsonMessage.length());
             OutputStream os = t.getResponseBody();
             os.write(jsonMessage.getBytes());
             os.close();
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

    // Handler for Listing Scan Folders
    static class ScanListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equalsIgnoreCase(t.getRequestMethod())) {
                try {
                    File tempDir = new File("temp");
                    List<Map<String, Object>> scanFolders = new ArrayList<>();
                    
                    if (tempDir.exists() && tempDir.isDirectory()) {
                        File[] folders = tempDir.listFiles(File::isDirectory);
                        if (folders != null) {
                            for (File folder : folders) {
                                // Show both old (scan_) and new (Scan_) format folders
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
                    
                    // Sort by lastModified descending (newest first)
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

    // Handler for Deleting Scan Folders
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
                    
                    File tempDir = new File("temp");
                    
                    for (String folderName : folderNames) {
                        File folder = new File(tempDir, folderName);
                        // Accept both old (scan_) and new (Scan_) format folders
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
        
        private boolean deleteDirectory(File file) {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (files != null) {
                    for (File f : files) {
                        deleteDirectory(f);
                    }
                }
            }
            return file.delete();
        }
        
        private void sendError(HttpExchange t, int code, String jsonMessage) throws IOException {
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(code, jsonMessage.length());
            OutputStream os = t.getResponseBody();
            os.write(jsonMessage.getBytes());
            os.close();
        }
    }

    // Handler for Viewing Scan Results
    static class ScanViewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equalsIgnoreCase(t.getRequestMethod())) {
                try {
                    String query = t.getRequestURI().getQuery();
                    String scanName = null;
                    
                    if (query != null) {
                        for (String param : query.split("&")) {
                            String[] pair = param.split("=");
                            if (pair.length == 2 && "scan".equals(pair[0])) {
                                scanName = pair[1];
                            }
                        }
                    }
                    
                    if (scanName == null || scanName.isEmpty()) {
                        sendError(t, 400, "{\"error\": \"Missing scan parameter\"}");
                        return;
                    }
                    
                    File tempDir = new File("temp");
                    File scanFolder = new File(tempDir, scanName);
                    
                    // Accept both old (scan_) and new (Scan_) format folders
                    if (!scanFolder.exists() || !scanFolder.isDirectory() || !scanName.toLowerCase().startsWith("scan")) {
                        sendError(t, 404, "{\"error\": \"Scan folder not found\"}");
                        return;
                    }
                    
                    // Re-scan all repositories in this folder
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
    
    // Handler for Progress Updates
    static class ProgressHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equalsIgnoreCase(t.getRequestMethod())) {
                Map<String, Object> progress = new java.util.HashMap<>();
                progress.put("message", currentProgress);
                progress.put("percent", progressPercent);
                
                Gson gson = new Gson();
                String jsonResponse = gson.toJson(progress);
                
                t.getResponseHeaders().set("Content-Type", "application/json");
                t.sendResponseHeaders(200, jsonResponse.getBytes().length);
                try (OutputStream os = t.getResponseBody()) {
                    os.write(jsonResponse.getBytes());
                }
            }
        }
    }
}
