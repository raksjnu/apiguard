package com.raks.aegis.gui;

import com.raks.aegis.AegisMain;
import com.raks.aegis.util.ArchiveExtractor;
import com.raks.aegis.util.SessionManager;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.List;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UnsupportedEncodingException;

public class AegisGUI {
    private static final Logger logger = LoggerFactory.getLogger(AegisGUI.class);
    private static int PORT = 8080;

    public static void main(String[] args) {
        if (args.length > 0) {
            try {
                PORT = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.warn("Invalid port provided, using default: {}", PORT);
            }
        }
        new AegisGUI().start();
    }

    public void start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

            Path appTempDir = Paths.get("temp").toAbsolutePath();
            Files.createDirectories(appTempDir);
            try {
                com.raks.aegis.engine.ReportGenerator.generateRuleGuide(appTempDir);
                logger.info("Rule Guide generated at startup in: {}", appTempDir);
            } catch (Exception e) {
                logger.error("Failed to generate Rule Guide at startup", e);
            }

            server.createContext("/", new StaticResourceHandler());

            server.createContext("/rule_guide.html", new RuleGuidePageHandler());
            server.createContext("/web/aegis/rule_guide.html", new RuleGuidePageHandler());

            server.createContext("/reports/", new ReportHandler());

            server.createContext("/api/validate", new ValidationHandler());
            server.createContext("/api/open", new OpenReportHandler());
            server.createContext("/api/git/repos", new GitDiscoveryReposHandler());
            server.createContext("/api/git/branches", new GitDiscoveryBranchesHandler());
            server.createContext("/download", new DownloadHandler());
            server.createContext("/api/download/sample", new SampleDownloadHandler());

            server.setExecutor(null);
            server.start();

            String url = "http://localhost:" + PORT;
            logger.info("Aegis GUI started at {}", url);
            logger.info("Press Ctrl+C to stop");

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            logger.error("Failed to start GUI server", e);
        }
    }

    static class RuleGuidePageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Path appTempDir = Paths.get("temp").toAbsolutePath();
            Path ruleGuidePath = appTempDir.resolve("rule_guide.html");

            if (Files.exists(ruleGuidePath)) {
                byte[] content = Files.readAllBytes(ruleGuidePath);
                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, content.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content);
                }
            } else {
                String response = "404 Not Found - Rule Guide not generated.";
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }

    static class StaticResourceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/web/aegis/index.html";
            else if (!path.startsWith("/web/aegis/")) {
                path = "/web/aegis/" + (path.startsWith("/") ? path.substring(1) : path);
            }

            InputStream is = getClass().getResourceAsStream(path);
            if (is == null) {

                String altPath = path.replace("/web/aegis/", "/");
                is = getClass().getResourceAsStream(altPath);
            }

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

                    String appTempDir = Paths.get("temp").toAbsolutePath().toString();
                    Files.createDirectories(Paths.get(appTempDir));

                    if ("local".equals(mode)) {
                        projectPath = (String) params.get("path");
                    } else if ("zip".equals(mode) || "jar".equals(mode)) {
                            File uploadFile = (File) params.get("archive");
                            if (uploadFile != null) {
                                String extractPath;
                                if ("zip".equals(mode)) {
                                    extractPath = ArchiveExtractor.extractZip(uploadFile.toPath(), sessionId, appTempDir);
                                } else {
                                    extractPath = ArchiveExtractor.extractJar(uploadFile.toPath(), sessionId, appTempDir);
                                }
                                projectPath = extractPath;
                            }
                        } else if ("git".equals(mode)) {
                            String gitUrl = (String) params.get("gitUrl");
                            String gitUrls = (String) params.get("gitUrls");
                            String gitBranch = (String) params.get("gitBranch");
                            String gitToken = (String) params.get("gitToken");

                            java.util.List<String> urlList = new java.util.ArrayList<>();
                            if (gitUrls != null && !gitUrls.trim().isEmpty()) {
                                for (String u : gitUrls.split(",")) if (!u.trim().isEmpty()) urlList.add(u.trim());
                            } else if (gitUrl != null && !gitUrl.trim().isEmpty()) {
                                urlList.add(gitUrl.trim());
                            }

                            if (urlList.isEmpty()) {
                                throw new IllegalArgumentException("No Git URLs provided");
                            }

                            Path sessionWorkDir = Paths.get(appTempDir, sessionId);
                            Files.createDirectories(sessionWorkDir);

                            for (String url : urlList) {
                                String repoName = url;
                                if (repoName.contains("/")) repoName = repoName.substring(repoName.lastIndexOf("/") + 1);
                                if (repoName.endsWith(".git")) repoName = repoName.substring(0, repoName.length() - 4);

                                Path repoDir = sessionWorkDir.resolve(repoName);
                                logger.info("Cloning Git Repo: {} to {}", url, repoDir);
                                com.raks.aegis.util.GitHelper.cloneRepository(url, gitBranch, gitToken, repoDir.toAbsolutePath().toString());
                            }

                            projectPath = sessionWorkDir.toAbsolutePath().toString();
                        }

                    if (projectPath != null) {
                        logger.info("Running validation on: {}", projectPath);

                        List<String> args = new ArrayList<>();
                        args.add("-p");
                        args.add(projectPath);

                        File customRulesFile = (File) params.get("customRules");
                        String customRulesPath = (String) params.get("customRulesPath");

                        if (customRulesFile != null) {
                            args.add("-c");
                            args.add(customRulesFile.getAbsolutePath());
                        } else if (customRulesPath != null && !customRulesPath.trim().isEmpty()) {
                            args.add("-c");
                            args.add(customRulesPath.trim());
                        }

                        Path reportDir = Paths.get(appTempDir, sessionId, "Aegis-reports");
                        args.add("-o");
                        args.add(reportDir.toAbsolutePath().toString());

                        AegisMain.execute(args.toArray(new String[0]));
                        if (!Files.exists(reportDir)) reportDir = Paths.get("Aegis-reports");

                        if (Files.exists(reportDir)) {
                            Path consolidatedReport = reportDir.resolve("CONSOLIDATED-REPORT.html");
                            if (!Files.exists(consolidatedReport)) {
                                try (java.util.stream.Stream<Path> stream = Files.list(reportDir)) {
                                    consolidatedReport = stream.filter(p -> p.toString().endsWith(".html")).findFirst().orElse(consolidatedReport);
                                }
                            }

                            String relativeReportUrl = "";
                            String reportZipName = null;

                            if (!"local".equals(mode)) {

                                Path sessionBase = Paths.get(appTempDir, sessionId, "Aegis-reports");
                                try {
                                    Path relPath = sessionBase.relativize(consolidatedReport);
                                    relativeReportUrl = relPath.toString().replace("\\", "/");
                                } catch (IllegalArgumentException e) {
                                    logger.warn("Could not relativize report path: " + e.getMessage());
                                }

                                try {

                                    String safeBaseName = Paths.get(projectPath).getFileName().toString();
                                    if(mode.equals("git") && args.contains("-p")) {

                                         if (Files.list(Paths.get(projectPath)).anyMatch(p -> Files.isDirectory(p) && !p.getFileName().toString().equals("Aegis-reports"))) {

                                            safeBaseName = Files.list(Paths.get(projectPath))
                                                .filter(p -> Files.isDirectory(p) && !p.getFileName().toString().equals("Aegis-reports"))
                                                .findFirst()
                                                .map(p -> p.getFileName().toString())
                                                .orElse("GitProject");

                                         } else {
                                             safeBaseName = "GitProject";
                                         }
                                    }

                                    safeBaseName = safeBaseName.replaceAll("[^a-zA-Z0-9._-]", "_");

                                    String zipFileName = safeBaseName + "_validation-report.zip";

                                    Path zipPath = sessionBase.getParent().resolve(zipFileName);
                                    zipFolder(reportDir, zipPath);

                                    reportZipName = zipFileName;
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
                } catch (Throwable e) {
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
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return !name.equalsIgnoreCase("help.html") && !name.equalsIgnoreCase("rule_guide.html");
                    })
                    .forEach(path -> {
                        String zipEntryName = sourceFolderPath.relativize(path).toString().replace("\\", "/");
                        ZipEntry zipEntry = new ZipEntry(zipEntryName);
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
                int semi = boundary.indexOf(';');
                if (semi != -1) boundary = boundary.substring(0, semi);
                boundary = boundary.trim();
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
        private final byte[] boundaryBytes;
        public MultipartParser(InputStream input, String boundary) {
            this.input = input;
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
                            Path tempFile = Files.createTempFile("Aegis-upload-", filename);
                            try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) { fos.write(data, offset, length); }
                            params.put(name, tempFile.toFile());
                        }
                    } else {
                        String value = new String(data, offset, length, StandardCharsets.UTF_8);
                        params.put(name, value);
                    }
                } else {

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

    static class OpenReportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
             if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                 Map<String, Object> params = new java.util.HashMap<>();

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

    static class ReportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            String prefix = "/reports/";
            if (!path.startsWith(prefix)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            String subPath = path.substring(prefix.length());
            int slashIndex = subPath.indexOf("/");

            if (slashIndex == -1) {

                exchange.sendResponseHeaders(404, -1);
                return;
            }

            String sessionId = subPath.substring(0, slashIndex);
            String resourcePath = subPath.substring(slashIndex + 1);

            if (resourcePath.contains("..") || sessionId.contains("..") || sessionId.contains("\\")) {
                 exchange.sendResponseHeaders(403, -1);
                 return;
            }

            Path appTempDir = Paths.get("temp").toAbsolutePath();
            Path sessionDir = appTempDir.resolve(sessionId).resolve("Aegis-reports");

            Path requestFile = sessionDir.resolve(resourcePath);

            if (Files.exists(requestFile) && !Files.isDirectory(requestFile)) {
                String contentType = "text/html"; 
                String name = requestFile.getFileName().toString().toLowerCase();
                if (name.endsWith(".css")) contentType = "text/css";
                else if (name.endsWith(".js")) contentType = "application/javascript";
                else if (name.endsWith(".png")) contentType = "image/png";
                else if (name.endsWith(".svg")) contentType = "image/svg+xml";
                else if (name.endsWith(".xlsx")) contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

                byte[] content = Files.readAllBytes(requestFile);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, content.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content);
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        }
    }

    static class SampleDownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> params = new HashMap<>();
                if (query != null) {
                    for (String pair : query.split("&")) {
                        int idx = pair.indexOf("=");
                        if (idx > 0)
                            params.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
                    }
                }

                String filename = params.get("file");
                if (filename == null || filename.isEmpty() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }

                if (!filename.equals("sample-project.zip") && !filename.equals("sample-rules.yaml")) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }

                InputStream is = AegisGUI.class.getResourceAsStream("/web/aegis/" + filename);
                if (is != null) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    int nRead;
                    byte[] data = new byte[16384];
                    while ((nRead = is.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }
                    buffer.flush();
                    byte[] content = buffer.toByteArray();
                    is.close();

                    if (filename.endsWith(".zip")) {
                         exchange.getResponseHeaders().set("Content-Type", "application/zip");
                    } else if (filename.endsWith(".yaml")) {
                         exchange.getResponseHeaders().set("Content-Type", "application/x-yaml");
                    }

                    exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
                    exchange.sendResponseHeaders(200, content.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(content); }
                } else {
                    logger.error("Sample file not found in resources: /web/aegis/" + filename);
                    exchange.sendResponseHeaders(404, -1);
                }
            } catch (Exception e) {
                logger.error("Sample download failed", e);
                exchange.sendResponseHeaders(500, -1);
            }
        }
    }

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
             String sessionId = params.get("session");

             if (filename == null || filename.isEmpty() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                 exchange.sendResponseHeaders(400, -1);
                 return;
             }

             if (sessionId == null || sessionId.isEmpty() || sessionId.contains("..") || sessionId.contains("/") || sessionId.contains("\\")) {

                  exchange.sendResponseHeaders(400, -1);
                  return;
             }

             Path appTempDir = Paths.get("temp").toAbsolutePath();
             Path sessionDir = appTempDir.resolve(sessionId); 

             Path file = sessionDir.resolve(filename);

             if (Files.exists(file) && !Files.isDirectory(file)) {
                 byte[] content = Files.readAllBytes(file);

                 exchange.getResponseHeaders().set("Content-Type", "application/zip");
                 exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
                 exchange.sendResponseHeaders(200, content.length);
                 try (OutputStream os = exchange.getResponseBody()) { os.write(content); }
             } else {
                 exchange.sendResponseHeaders(404, -1);
             }
        }
    }

    static class GitDiscoveryReposHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {

                String xTokenHeader = exchange.getRequestHeaders().getFirst("x-git-token");
                String xProviderHeader = exchange.getRequestHeaders().getFirst("x-git-provider");

                String maskedHeaderToken = (xTokenHeader != null && xTokenHeader.length() > 4) 
                                           ? xTokenHeader.substring(0, 2) + "***" 
                                           : (xTokenHeader == null ? "null" : "short");

                logger.info("[GitDiscoveryReposHandler] START. Headers -> Token: {}, Provider: {}", maskedHeaderToken, xProviderHeader);

                Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());

                String provider = queryParams.getOrDefault("provider", "gitlab");
                String token = queryParams.getOrDefault("token", "");

                if (token.isEmpty() && xTokenHeader != null) {
                    token = xTokenHeader;
                }

                if (xProviderHeader != null && !xProviderHeader.isBlank()) {
                    provider = xProviderHeader;
                }

                String group = queryParams.getOrDefault("group", queryParams.getOrDefault("query", ""));
                String filter = queryParams.getOrDefault("filter", "");

                logger.info("[GitDiscoveryReposHandler] Resolved Params -> Group: {}, Provider: {}, Filter: {}", group, provider, filter);

                String gitlabUrl = queryParams.get("gitlabUrl");
                String githubUrl = queryParams.get("githubUrl");

                java.util.List<String> repos = com.raks.aegis.util.GitHelper.listRepositories(provider, token, group, filter, gitlabUrl, githubUrl);

                ObjectMapper mapper = new ObjectMapper();
                String response = mapper.writeValueAsString(repos);

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            } catch (Throwable e) {
                logger.error("Git discovery (repos) failed", e);
                String err = "{\"success\":false, \"message\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}";
                exchange.sendResponseHeaders(500, err.length());
                try (OutputStream os = exchange.getResponseBody()) { os.write(err.getBytes()); }
            }
        }
    }

    static class GitDiscoveryBranchesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
                String provider = queryParams.getOrDefault("provider", "gitlab");
                String token = queryParams.getOrDefault("token", "");
                if (token.isEmpty() && exchange.getRequestHeaders().containsKey("x-git-token")) {
                    token = exchange.getRequestHeaders().getFirst("x-git-token");
                }

                if (exchange.getRequestHeaders().containsKey("x-git-provider")) {
                   provider = exchange.getRequestHeaders().getFirst("x-git-provider");
                }
                String repo = queryParams.getOrDefault("repo", "");

                String gitlabUrl = queryParams.get("gitlabUrl");
                String githubUrl = queryParams.get("githubUrl");

                java.util.List<String> branches = com.raks.aegis.util.GitHelper.listBranches(provider, token, repo, gitlabUrl, githubUrl);

                ObjectMapper mapper = new ObjectMapper();
                String response = mapper.writeValueAsString(branches);

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            } catch (Throwable e) {
                logger.error("Git discovery (branches) failed", e);
                String err = "{\"success\":false, \"message\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}";
                exchange.sendResponseHeaders(500, err.length());
                try (OutputStream os = exchange.getResponseBody()) { os.write(err.getBytes()); }
            }
        }
    }

    private static Map<String, String> parseQueryParams(String query) throws UnsupportedEncodingException {
        Map<String, String> params = new HashMap<>();
        if (query != null) {
            for (String pair : query.split("&")) {
                int idx = pair.indexOf("=");
                if (idx > 0)
                    params.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
            }
        }
        return params;
    }
}
