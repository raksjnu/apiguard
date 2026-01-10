package com.raks.muleguard.gui;

import com.raks.muleguard.MuleGuardMain;
import com.raks.muleguard.util.ArchiveExtractor;
import com.raks.muleguard.util.SessionManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * MuleGuard Standalone GUI
 * Serves a web interface for running validations and handles file uploads.
 */
public class MuleGuardGUI {
    private static final Logger logger = LoggerFactory.getLogger(MuleGuardGUI.class);
    private static final int PORT = 8080;

    public static void main(String[] args) {
        new MuleGuardGUI().start();
    }

    public void start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            
            // Serve static resources and main page
            server.createContext("/", new StaticResourceHandler());
            
            // Serve generated reports
            server.createContext("/reports/", new ReportHandler());
            
            // API endpoints
            server.createContext("/api/validate", new ValidationHandler());
            server.createContext("/api/open", new OpenReportHandler());
            server.createContext("/download", new DownloadHandler()); // Added Download Handler
            
            server.setExecutor(null);
            server.start();

            String url = "http://localhost:" + PORT;
            logger.info("MuleGuard GUI started at {}", url);
            logger.info("Press Ctrl+C to stop");

            // Open browser
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            logger.error("Failed to start GUI server", e);
        }
    }

    /**
     * Handler for downloading files (e.g. reports).
     */
    static class DownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = new HashMap<>();
            if (query != null) {
                for (String pair : query.split("&")) {
                    int idx = pair.indexOf("=");
                    if (idx > 0)
                        params.put(URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8), URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
                }
            }

            String filename = params.get("file");
            if (filename == null || filename.isEmpty() || filename.contains("..")) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            // Expecting file to be in the temp directory (or a specific downloads folder if we architecture it that way)
            // For now, assuming we might want to download the ZIP report which might be in the temp root or session dir.
            // Simplified: We'll look in the app's temp directory.
            Path appTempDir = Paths.get("temp").toAbsolutePath();
            Path file = appTempDir.resolve(filename);

            if (Files.exists(file) && !Files.isDirectory(file)) {
                exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
                byte[] content = Files.readAllBytes(file);
                exchange.sendResponseHeaders(200, content.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(content); }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        }
    }

    /**
     * Handler for static resources and the main index page.
     */
    static class StaticResourceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/web/index.html";
            else if (!path.startsWith("/web/")) path = "/web" + path;

            InputStream is = getClass().getResourceAsStream(path);
            if (is == null && path.startsWith("/web/")) is = getClass().getResourceAsStream(path.substring(4));

            try (InputStream ris = is) {
                if (ris != null) {
                    byte[] content = readAllBytes(ris);
                    String contentType = "text/html";
                    if (path.endsWith(".css")) contentType = "text/css";
                    else if (path.endsWith(".js")) contentType = "application/javascript";
                    else if (path.endsWith(".png")) contentType = "image/png";
                    else if (path.endsWith(".svg")) contentType = "image/svg+xml";

                    exchange.getResponseHeaders().set("Content-Type", contentType);
                    exchange.sendResponseHeaders(200, content.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(content); }
                } else {
                    String response = "404 Not Found";
                    exchange.sendResponseHeaders(404, response.length());
                    try (OutputStream os = exchange.getResponseBody()) { os.write(response.getBytes()); }
                }
            } catch (Exception e) {
                logger.error("Error serving resource: {}", path, e);
                exchange.sendResponseHeaders(500, 0);
            }
        }
    }

    /**
     * Handler for validation requests.
     */
    static class ValidationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    Map<String, Object> params = parseRequest(exchange);
                    String mode = (String) params.getOrDefault("mode", "local");
                    String projectPath = null;
                    String sessionId = (String) params.get("session");
                    
                    if (sessionId == null || sessionId.isEmpty()) {
                        sessionId = SessionManager.createSession();
                    }

                    // Use local 'temp' directory in the application folder
                    String appTempDir = Paths.get("temp").toAbsolutePath().toString();
                    Files.createDirectories(Paths.get(appTempDir));

                    if ("local".equals(mode)) {
                        projectPath = (String) params.get("path");
                    } else if ("zip".equals(mode) || "jar".equals(mode)) {
                        File uploadFile = (File) params.get("archive");
                        if (uploadFile != null) {
                            Path extractPath;
                            if ("zip".equals(mode)) {
                                extractPath = ArchiveExtractor.extractZip(uploadFile.toPath(), sessionId, appTempDir);
                            } else {
                                extractPath = ArchiveExtractor.extractJar(uploadFile.toPath(), sessionId, appTempDir);
                            }
                            projectPath = extractPath.toString();
                        }
                    }

                    if (projectPath != null) {
                        logger.info("Running validation on: {}", projectPath);
                        MuleGuardMain.main(new String[]{"-p", projectPath});
                        
                        Path reportDir = Paths.get(projectPath, "muleguard-reports");
                        if (!Files.exists(reportDir)) reportDir = Paths.get("muleguard-reports");
                        
                        if (Files.exists(reportDir)) {
                            Path consolidatedReport = reportDir.resolve("CONSOLIDATED-REPORT.html");
                            if (!Files.exists(consolidatedReport)) {
                                try (java.util.stream.Stream<Path> stream = Files.list(reportDir)) {
                                    consolidatedReport = stream.filter(p -> p.toString().endsWith(".html")).findFirst().orElse(consolidatedReport);
                                }
                            }
                            
                     // Calculate relative path for web serving (ZIP/JAR mode)
                            String relativeReportUrl = "";
                            String reportZipName = null;
                            
                            if (!"local".equals(mode)) {
                                // For web serving, we need the path relative to the session directory
                                // Structure: {temp}/muleguard-sessions/{sessionId}/{...}/reports/...
                                Path sessionBase = Paths.get(appTempDir, "muleguard-sessions", sessionId);
                                try {
                                    Path relPath = sessionBase.relativize(consolidatedReport);
                                    relativeReportUrl = relPath.toString().replace("\\", "/");
                                } catch (IllegalArgumentException e) {
                                    logger.warn("Could not relativize report path: " + e.getMessage());
                                }
                                
                                // ZIP the report directory for download
                                try {
                                    Path zipPath = reportDir.getParent().resolve("validation-report.zip");
                                    zipFolder(reportDir, zipPath);
                                    // Make zip name relative to app temp dir for download handler
                                    reportZipName = Paths.get("temp").toAbsolutePath().relativize(zipPath).toString();
                                } catch (Exception e) {
                                    logger.error("Failed to zip report directory", e);
                                }
                            }

                            String responseJson = String.format(
                                "{\"status\":\"success\", \"success\":true, \"reportPath\":\"%s\", \"relativeReportUrl\":\"%s\", \"reportZip\":\"%s\", \"sessionId\":\"%s\", \"mode\":\"%s\"}", 
                                consolidatedReport.toAbsolutePath().toString().replace("\\", "/"),
                                relativeReportUrl,
                                (reportZipName != null ? reportZipName.replace("\\", "/") : ""),
                                sessionId,
                                mode
                            );
                            sendResponse(exchange, 200, responseJson);
                        } else {
                            sendError(exchange, "Validation failed: Report directory not found at " + reportDir.toAbsolutePath().toString().replace("\\", "/"));
                        }
                    } else {
                        sendError(exchange, "Missing project path or file upload");
                    }
                } catch (Exception e) {
                    logger.error("Validation error", e);
                    sendError(exchange, "Detailed Error: " + e.getMessage());
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
        
        private void zipFolder(Path sourceFolderPath, Path zipPath) throws IOException {
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
                Files.walk(sourceFolderPath)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(sourceFolderPath.relativize(path).toString().replace("\\", "/"));
                        try {
                            zos.putNextEntry(zipEntry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            logger.error("Error creating zip entry: " + path, e);
                        }
                    });
            }
        }
        
        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(responseBytes); }
        }

        private void sendError(HttpExchange exchange, String message) throws IOException {
            String safeMessage = message.replace("\\", "\\\\").replace("\"", "\\\"");
            String jsonHelper = "{\"status\":\"error\", \"success\":false, \"message\":\"" + safeMessage + "\"}";
            sendResponse(exchange, 500, jsonHelper);
        }

        private Map<String, Object> parseRequest(HttpExchange exchange) throws IOException {
            Map<String, Object> params = new HashMap<>();
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType != null && contentType.startsWith("multipart/form-data")) {
                String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
                if (boundary.startsWith("\"") && boundary.endsWith("\"")) boundary = boundary.substring(1, boundary.length() - 1);
                try (InputStream is = exchange.getRequestBody()) {
                    MultipartParser parser = new MultipartParser(is, boundary);
                    params.putAll(parser.parse());
                }
            } else {
                String query = new String(readAllBytes(exchange.getRequestBody()), StandardCharsets.UTF_8);
                if (!query.isEmpty()) {
                    String[] pairs = query.split("&");
                    for (String pair : pairs) {
                        int idx = pair.indexOf("=");
                        if (idx > 0) params.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
                    }
                }
            }
            return params;
        }
    }
    
    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) buffer.write(data, 0, nRead);
        buffer.flush();
        return buffer.toByteArray();
    }

    static class MultipartParser {
        private final InputStream input;
        private final String boundary;
        private final byte[] boundaryBytes;
        public MultipartParser(InputStream input, String boundary) {
            this.input = input;
            this.boundary = boundary;
            this.boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        }
        public Map<String, Object> parse() throws IOException {
            Map<String, Object> params = new HashMap<>();
            byte[] data = readAllBytes(input);
            int offset = 0;
            while (offset < data.length) {
                int boundaryIndex = indexOf(data, boundaryBytes, offset);
                if (boundaryIndex == -1) break;
                offset = boundaryIndex + boundaryBytes.length;
                if (offset + 2 <= data.length && data[offset] == '-' && data[offset+1] == '-') break;
                if (offset + 2 <= data.length && data[offset] == '\r' && data[offset+1] == '\n') offset += 2;
                Map<String, String> headers = new HashMap<>();
                while (true) {
                    int lineEnd = indexOf(data, new byte[]{'\r', '\n'}, offset);
                    if (lineEnd == -1 || lineEnd == offset) { offset += 2; break; }
                    String line = new String(data, offset, lineEnd - offset, StandardCharsets.ISO_8859_1);
                    int colon = line.indexOf(':');
                    if (colon > 0) headers.put(line.substring(0, colon).trim().toLowerCase(), line.substring(colon + 1).trim());
                    offset = lineEnd + 2;
                }
                String disposition = headers.get("content-disposition");
                if (disposition != null) {
                    String name = extractAttribute(disposition, "name");
                    String filename = extractAttribute(disposition, "filename");
                    int nextBoundary = indexOf(data, boundaryBytes, offset);
                    if (nextBoundary == -1) nextBoundary = data.length;
                    int contentEnd = nextBoundary - 2;
                    int length = contentEnd - offset;
                    if (filename != null && !filename.isEmpty()) {
                        if (length > 0) {
                            Path tempFile = Files.createTempFile("muleguard-upload-", filename);
                            try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) { fos.write(data, offset, length); }
                            params.put(name, tempFile.toFile());
                        }
                    } else {
                        String value = new String(data, offset, length, StandardCharsets.UTF_8);
                        params.put(name, value);
                    }
                }
            }
            return params;
        }
        private String extractAttribute(String header, String attribute) {
            int start = header.indexOf(attribute + "=\"");
            if (start != -1) {
                start += attribute.length() + 2;
                int end = header.indexOf("\"", start);
                if (end != -1) return header.substring(start, end);
            }
            return null;
        }
        private int indexOf(byte[] data, byte[] pattern, int start) {
            for (int i = start; i <= data.length - pattern.length; i++) {
                boolean match = true;
                for (int j = 0; j < pattern.length; j++) {
                    if (data[i + j] != pattern[j]) { match = false; break; }
                }
                if (match) return i;
            }
            return -1;
        }
    }

    /**
     * Handler for opening local reports via Desktop.browse().
     */
    static class OpenReportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
             if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                 Map<String, Object> params = new java.util.HashMap<>();
                 // Quick URL decode
                 String query = new String(readAllBytes(exchange.getRequestBody()), StandardCharsets.UTF_8);
                 if (!query.isEmpty()) {
                     for (String pair : query.split("&")) {
                         int idx = pair.indexOf("=");
                         if (idx > 0) params.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
                     }
                 }
                 
                 String pathStr = (String) params.get("path");
                 if (pathStr != null) {
                     Path path = Paths.get(pathStr);
                     if (Files.exists(path)) {
                         if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                             try {
                                 Desktop.getDesktop().browse(path.toUri());
                                 String resp = "{\"success\":true}";
                                 exchange.getResponseHeaders().set("Content-Type", "application/json");
                                 exchange.sendResponseHeaders(200, resp.length());
                                 try(OutputStream os=exchange.getResponseBody()){os.write(resp.getBytes());}
                             } catch(Exception e) {
                                 String resp = "{\"success\":false, \"message\":\"Failed to browse: "+e.getMessage()+"\"}";
                                 exchange.sendResponseHeaders(500, resp.length());
                                 try(OutputStream os=exchange.getResponseBody()){os.write(resp.getBytes());}
                             }
                         } else {
                            String resp = "{\"success\":false, \"message\":\"Desktop not supported\"}";
                            exchange.sendResponseHeaders(400, resp.length());
                            try(OutputStream os=exchange.getResponseBody()){os.write(resp.getBytes());}
                         }
                     } else {
                         String resp = "{\"success\":false, \"message\":\"File not found\"}";
                         exchange.sendResponseHeaders(404, resp.length());
                         try(OutputStream os=exchange.getResponseBody()){os.write(resp.getBytes());}
                     }
                 } else {
                     exchange.sendResponseHeaders(400, 0);
                 }
             } else {
                 exchange.sendResponseHeaders(405, 0);
             }
        }
    }

    /**
     * Handler for serving generated reports for ZIP/JAR sessions.
     * URI Format: /reports/{sessionId}/{relativePath...}
     */
    static class ReportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            // Expected: /reports/sessionId/nested/path/to/report.html
            
            String prefix = "/reports/";
            if (!path.startsWith(prefix)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            
            String subPath = path.substring(prefix.length());
            int slashIndex = subPath.indexOf("/");
            
            if (slashIndex == -1) {
                // Must specify at least session ID
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            
            String sessionId = subPath.substring(0, slashIndex);
            String resourcePath = subPath.substring(slashIndex + 1);
            
            // Security check: prevent ../ traversal
            if (resourcePath.contains("..") || sessionId.contains("..") || sessionId.contains("\\")) {
                 exchange.sendResponseHeaders(403, -1);
                 return;
            }
            
            // Use local temp dir
            Path appTempDir = Paths.get("temp").toAbsolutePath();
            Path sessionDir = appTempDir.resolve("muleguard-sessions").resolve(sessionId);
            
            // Resource path is relative to the session root
            Path requestFile = sessionDir.resolve(resourcePath);
            
            if (Files.exists(requestFile) && !Files.isDirectory(requestFile)) {
                String contentType = "text/html"; 
                String name = requestFile.getFileName().toString().toLowerCase();
                if (name.endsWith(".css")) contentType = "text/css";
                else if (name.endsWith(".js")) contentType = "application/javascript";
                else if (name.endsWith(".png")) contentType = "image/png";
                else if (name.endsWith(".svg")) contentType = "image/svg+xml";
                
                byte[] content = Files.readAllBytes(requestFile);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, content.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content);
                }
            } else {
                 String msg = "404 Not Found: " + resourcePath;
                 exchange.sendResponseHeaders(404, msg.length());
                 try (OutputStream os = exchange.getResponseBody()) {
                     os.write(msg.getBytes());
                 }
            }
        }
    }
}
