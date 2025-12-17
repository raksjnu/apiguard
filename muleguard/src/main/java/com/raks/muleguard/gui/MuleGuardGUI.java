package com.raks.muleguard.gui;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.raks.muleguard.MuleGuardMain;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class MuleGuardGUI {

    private static int PORT = 8080; // Default starting port
    private static final int PORT_START = 8080;
    private static final int PORT_END = 8089;
    private static String lastProjectPath = null; // Track last validated project path

    public static void main(String[] args) {
        // Allow port to be specified as command-line argument
        if (args.length > 0) {
            try {
                PORT = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0] + ". Using default port 8080.");
            }
        }

        HttpServer server = null;
        int actualPort = PORT;

        // Try to find an available port in the range
        for (int port = PORT; port <= PORT_END; port++) {
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
                actualPort = port;
                break; // Successfully bound to port
            } catch (java.net.BindException e) {
                if (port == PORT_END) {
                    System.err.println("============================================================");
                    System.err.println("  ERROR: All ports in range " + PORT_START + "-" + PORT_END
                            + " are in use!");
                    System.err.println("  Please free up a port or specify a different port.");
                    System.err.println("============================================================");
                    return;
                }
                // Port in use, try next one
                if (port == PORT) {
                    System.out.println("Port " + port + " is in use, trying next port...");
                }
            } catch (Exception e) {
                System.err.println("Failed to start server on port " + port + ": " + e.getMessage());
                return;
            }
        }

        if (server == null) {
            System.err.println("Failed to start GUI server - no available ports found.");
            return;
        }

        try {
            System.out.println("============================================================");

            // Register a unified handler that serves both the GUI and reports
            // This is necessary because HTTP server context matching is prefix-based
            server.createContext("/", new UnifiedHandler());

            // API endpoint to run validation
            server.createContext("/api/validate", new ValidationHandler());

            server.setExecutor(null); // Use default executor
            server.start();

            System.out.println("============================================================");
            System.out.println("          MuleGuard GUI Server Started");
            System.out.println("============================================================");
            if (actualPort != PORT) {
                System.out
                        .println("  NOTE: Port " + PORT + " was in use, using port " + actualPort + " instead");
                System.out.println("============================================================");
            }
            System.out.println("  Open your browser and navigate to:");
            System.out.println("");
            System.out.println("  http://localhost:" + actualPort);
            System.out.println("");
            System.out.println("  Press Ctrl+C to stop the server");
            System.out.println("============================================================");

            // Try to open browser automatically
            try {
                String os = System.getProperty("os.name").toLowerCase();
                String url = "http://localhost:" + actualPort;
                ProcessBuilder pb;
                if (os.contains("win")) {
                    pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
                } else if (os.contains("mac")) {
                    pb = new ProcessBuilder("open", url);
                } else if (os.contains("nix") || os.contains("nux")) {
                    pb = new ProcessBuilder("xdg-open", url);
                } else {
                    pb = null;
                }
                if (pb != null) {
                    pb.start();
                }
            } catch (Exception e) {
                // Silently fail if browser can't be opened
            }

        } catch (Exception e) {
            System.err.println("Failed to start GUI server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Unified handler that serves both the GUI form and report files
    static class UnifiedHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();

            System.out.println(
                    "[DEBUG] Received request: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());

            // Check if this is a report request
            boolean isReportRequest = path.startsWith("/report/") ||
                    (path.endsWith(".html") && !path.equals("/")) ||
                    (query != null && query.startsWith("path="));

            if (isReportRequest) {
                // Delegate to report serving logic
                serveReport(exchange, path, query);
            } else {
                // Serve the GUI form
                serveGUI(exchange);
            }
        }

        private void serveGUI(HttpExchange exchange) throws IOException {
            String html = generateHomePage();
            byte[] response = html.getBytes("UTF-8");

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
                os.flush();
            }
            System.out.println("[DEBUG] Response sent: " + response.length + " bytes");
        }

        private void serveReport(HttpExchange exchange, String requestPath, String query) throws IOException {
            Path reportPath = null;

            // If there's a query parameter with full path, use that
            if (query != null && query.startsWith("path=")) {
                String fullPath = java.net.URLDecoder.decode(query.substring(5), "UTF-8");
                reportPath = Paths.get(fullPath);
            } else {
                // Extract the relative path
                String relativePath;
                if (requestPath.startsWith("/report/")) {
                    // Path like /report/checklist.html
                    relativePath = requestPath.substring("/report/".length());
                } else if (requestPath.startsWith("/") && requestPath.endsWith(".html")) {
                    // Path like /CONSOLIDATED-REPORT.html or /checklist.html
                    relativePath = requestPath.substring(1); // Remove leading /
                } else {
                    // Not a report request
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }

                if (relativePath.isEmpty()) {
                    sendError(exchange, 400, "Missing report path");
                    return;
                }

                // Decode URL-encoded path
                relativePath = java.net.URLDecoder.decode(relativePath, "UTF-8");

                // Find the reports directory
                Path reportsDir;
                if (lastProjectPath != null) {
                    reportsDir = Paths.get(lastProjectPath).resolve("muleguard-reports");
                } else {
                    // Fallback to default location
                    reportsDir = Paths.get(System.getProperty("user.dir")).resolve("testData")
                            .resolve("muleguard-reports");
                }
                reportPath = reportsDir.resolve(relativePath).normalize();

                // Security check: ensure the resolved path is still within the reports
                // directory
                if (!reportPath.startsWith(reportsDir)) {
                    sendError(exchange, 403, "Access denied");
                    return;
                }
            }

            if (!Files.exists(reportPath)) {
                System.err.println("[ERROR] Report not found: " + reportPath);
                sendError(exchange, 404, "Report not found: " + reportPath.getFileName());
                return;
            }

            try {
                byte[] content = Files.readAllBytes(reportPath);

                // Set content type based on file extension
                String contentType = "text/html; charset=UTF-8";
                String fileName = reportPath.getFileName().toString().toLowerCase();
                if (fileName.endsWith(".css")) {
                    contentType = "text/css; charset=UTF-8";
                } else if (fileName.endsWith(".js")) {
                    contentType = "application/javascript; charset=UTF-8";
                } else if (fileName.endsWith(".xlsx")) {
                    contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                } else if (fileName.endsWith(".svg")) {
                    contentType = "image/svg+xml; charset=UTF-8";
                }

                // For HTML files, rewrite relative links to use /report/ prefix
                if (fileName.endsWith(".html")) {
                    String htmlContent = new String(content, "UTF-8");

                    // Add <base> tag to set the base URL for all relative links
                    if (htmlContent.contains("<head>")) {
                        htmlContent = htmlContent.replace("<head>", "<head>\n<base href=\"/report/\">");
                        System.out.println("[DEBUG] Added base tag to: " + fileName);
                    }

                    // Rewrite absolute paths that start with / but aren't /report/
                    htmlContent = htmlContent.replaceAll("href=\"/(?!report/)([^\"]+)\"", "href=\"/report/$1\"");

                    // Rewrite relative href attributes
                    htmlContent = htmlContent.replaceAll("href=\"(?!http|/|#)([^\"]+)\"", "href=\"/report/$1\"");

                    // Rewrite src attributes for relative resources
                    htmlContent = htmlContent.replaceAll("src=\"(?!http|/|#)([^\"]+)\"", "src=\"/report/$1\"");

                    System.out.println("[DEBUG] Rewrote HTML links for: " + fileName);
                    content = htmlContent.getBytes("UTF-8");
                }

                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, content.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content);
                }

                System.out.println("[DEBUG] Served report: " + reportPath.getFileName());
            } catch (IOException e) {
                sendError(exchange, 500, "Error reading report: " + e.getMessage());
            }
        }

        private void sendError(HttpExchange exchange, int code, String message) throws IOException {
            byte[] response = message.getBytes("UTF-8");
            exchange.sendResponseHeaders(code, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }

        private String generateHomePage() {
            try {
                // Get current directory as default
                String defaultPath = System.getProperty("user.dir") + File.separator + "testData";

                String htmlTemplate = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <title>MuleGuard - Validation Tool</title>
                            <style>
                                :root {
                                    --truist-purple: #663399;
                                    --truist-purple-light: #7d4fb2;
                                    --text-white: #FFFFFF;
                                }
                                * { margin: 0; padding: 0; box-sizing: border-box; }
                                body {
                                    font-family: Arial, sans-serif;
                                    background-color: #f0f0f0;
                                    margin: 0;
                                }
                                .container {
                                    background: white;
                                    border: 5px solid var(--truist-purple);
                                    border-radius: 8px;
                                    padding: 20px 40px;
                                    margin: 20px;
                                    box-shadow: 0 4px 12px rgba(0,0,0,0.1);
                                }
                                h1 {
                                    color: var(--truist-purple);
                                    margin-bottom: 10px;
                                    font-size: 2.5em;
                                }
                                .subtitle {
                                    color: #666;
                                    margin-bottom: 30px;
                                    font-size: 1.1em;
                                }
                                .form-group {
                                    margin-bottom: 25px;
                                    max-width: 1200px;
                                }
                                label {
                                    display: block;
                                    margin-bottom: 8px;
                                    color: var(--truist-purple);
                                    font-weight: bold;
                                    font-size: 1.1em;
                                }
                                input[type="text"] {
                                    width: 100%%;
                                    min-width: 600px;
                                    padding: 16px 20px;
                                    border: 2px solid #ddd;
                                    border-radius: 6px;
                                    font-size: 18px;
                                    font-family: 'Courier New', monospace;
                                    transition: border-color 0.3s;
                                }
                                input[type="text"]:focus {
                                    outline: none;
                                    border-color: var(--truist-purple);
                                }
                                .hint {
                                    font-size: 0.9em;
                                    color: #666;
                                    margin-top: 5px;
                                }
                                button {
                                    background: var(--truist-purple);
                                    color: white;
                                    border: none;
                                    padding: 15px 40px;
                                    font-size: 1.2em;
                                    font-weight: bold;
                                    border-radius: 6px;
                                    cursor: pointer;
                                    transition: all 0.3s;
                                    box-shadow: 0 4px 15px rgba(102, 51, 153, 0.4);
                                    width: 100%%;
                                }
                                button:hover {
                                    background: var(--truist-purple-light);
                                    transform: translateY(-2px);
                                    box-shadow: 0 6px 20px rgba(102, 51, 153, 0.6);
                                }
                                button:disabled {
                                    background: #ccc;
                                    cursor: not-allowed;
                                    transform: none;
                                }
                                .status {
                                    margin-top: 25px;
                                    padding: 15px;
                                    border-radius: 6px;
                                    display: none;
                                }
                                .status.success {
                                    background: #e8f5e9;
                                    border: 2px solid #4caf50;
                                    color: #2e7d32;
                                }
                                .status.error {
                                    background: #ffebee;
                                    border: 2px solid #f44336;
                                    color: #c62828;
                                }
                                .status.info {
                                    background: #e3f2fd;
                                    border: 2px solid #2196f3;
                                    color: #1565c0;
                                }
                                .spinner {
                                    border: 4px solid #f3f3f3;
                                    border-top: 4px solid var(--truist-purple);
                                    border-radius: 50%;
                                    width: 40px;
                                    height: 40px;
                                    animation: spin 1s linear infinite;
                                    margin: 20px auto;
                                    display: none;
                                }
                                @keyframes spin {
                                    0% { transform: rotate(0deg); }
                                    100%% { transform: rotate(360deg); }
                                }
                                .report-link {
                                    display: inline-block;
                                    margin-top: 15px;
                                    padding: 12px 30px;
                                    background: var(--truist-purple);
                                    color: white;
                                    text-decoration: none;
                                    border-radius: 6px;
                                    font-weight: bold;
                                    transition: all 0.3s;
                                }
                                .report-link:hover {
                                    background: var(--truist-purple-light);
                                    transform: translateY(-2px);
                                }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <div style="text-align: center; margin-bottom: 20px;">
                                    <img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI4MCIgaGVpZ2h0PSI4MCIgdmlld0JveD0iMCAwIDgwIDgwIj4KICA8IS0tIFNoaWVsZCBiYWNrZ3JvdW5kIC0tPgogIDxwYXRoIGQ9Ik0gMTUgMjAgTCA0MCAxMCBMIDY1IDIwIEwgNjUgNDUgUSA2NSA2MCA0MCA3NSBRIDE1IDYwIDE1IDQ1IFoiIGZpbGw9IiM2NjMzOTkiIHN0cm9rZT0iIzY2MzM5OSIgc3Ryb2tlLXdpZHRoPSIyIi8+CiAgPCEtLSBSQUtTIHRleHQgaW5zaWRlIHNoaWVsZCAtLT4KICA8dGV4dCB4PSI0MCIgeT0iNTAiIGZvbnQtZmFtaWx5PSJBcmlhbCwgc2Fucy1zZXJpZiIgZm9udC1zaXplPSIxOCIgZm9udC13ZWlnaHQ9ImJvbGQiIGZpbGw9IndoaXRlIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIj5SQUtTPC90ZXh0Pgo8L3N2Zz4=" alt="MuleGuard Logo" style="height: 80px;">
                                    <h1 style="margin-top: 15px;">MuleGuard</h1>
                                </div>
                                <p class="subtitle">Comprehensive MuleSoft Application Validation Tool</p>

                                <form id="validationForm">
                                    <div class="form-group">
                                        <label for="projectPath">Mule Project Folders Location</label>
                                        <input
                                            type="text"
                                            id="projectPath"
                                            name="projectPath"
                                            value="{{DEFAULT_PATH}}"
                                            placeholder="Enter full path or relative path (e.g., ./testData)"
                                            required
                                        />
                                        <div class="hint">
                                            💡 You can use absolute path (e.g., C:\\projects\\muleapps) or relative path (e.g., ./testData)
                                        </div>
                                    </div>

                                    <button type="submit" id="validateBtn">
                                        🚀 Run Validation - MuleGuard
                                    </button>
                                </form>

                                <div class="spinner" id="spinner"></div>

                                <div class="status" id="status"></div>
                            </div>

                            <script>
                                const form = document.getElementById('validationForm');
                                const validateBtn = document.getElementById('validateBtn');
                                const spinner = document.getElementById('spinner');
                                const status = document.getElementById('status');

                                form.addEventListener('submit', async (e) => {
                                    e.preventDefault();

                                    const projectPath = document.getElementById('projectPath').value.trim();

                                    if (!projectPath) {
                                        showStatus('error', 'Please enter a project path');
                                        return;
                                    }

                                    // Show loading state
                                    validateBtn.disabled = true;
                                    validateBtn.textContent = '⏳ Running Validation...';
                                    spinner.style.display = 'block';
                                    status.style.display = 'none';

                                    try {
                                        const response = await fetch('/api/validate', {
                                            method: 'POST',
                                            headers: {
                                                'Content-Type': 'application/x-www-form-urlencoded',
                                            },
                                            body: 'projectPath=' + encodeURIComponent(projectPath)
                                        });

                                        const result = await response.json();

                                        if (result.success) {
                                            showStatus('success',
                                                '✅ Validation completed successfully!<br><br>' +
                                                '<strong>Results:</strong><br>' +
                                                '• Total APIs Scanned: ' + result.totalApis + '<br>' +
                                                '• Total Rules Checked: ' + result.totalRules + '<br>' +
                                                '• Passed: ' + result.passed + '<br>' +
                                                '• Failed: ' + result.failed + '<br><br>' +
                                                '<a href="/report?path=' + encodeURIComponent(result.reportPath) + '" class="report-link" target="_blank">📊 Open Dashboard Report</a>'
                                            );
                                        } else {
                                            showStatus('error', '❌ Validation failed: ' + result.message);
                                        }
                                    } catch (error) {
                                        showStatus('error', '❌ Error: ' + error.message);
                                    } finally {
                                        validateBtn.disabled = false;
                                        validateBtn.textContent = '🚀 Run Validation - MuleGuard';
                                        spinner.style.display = 'none';
                                    }
                                });

                                function showStatus(type, message) {
                                    status.className = 'status ' + type;
                                    status.innerHTML = message;
                                    status.style.display = 'block';
                                }
                            </script>
                        </body>
                        </html>
                        """;

                // Use replace instead of formatted to avoid issues with special characters
                return htmlTemplate.replace("{{DEFAULT_PATH}}", defaultPath.replace("\\", "\\\\"));
            } catch (Exception e) {
                System.err.println("[ERROR] Failed to generate HTML: " + e.getMessage());
                e.printStackTrace();
                return "\u003chtml\u003e\u003cbody\u003e\u003ch1\u003eError\u003c/h1\u003e\u003cp\u003e"
                        + e.getMessage() + "\u003c/p\u003e\u003c/body\u003e\u003c/html\u003e";
            }
        }
    }

    static class ValidationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("[DEBUG] Validation request received: " + exchange.getRequestMethod());

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            // Read request body
            String body = new String(exchange.getRequestBody().readAllBytes());
            Map<String, String> params = parseFormData(body);
            String projectPath = params.get("projectPath");

            if (projectPath == null || projectPath.trim().isEmpty()) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Project path is required\"}");
                return;
            }

            try {
                System.out.println("[DEBUG] Project path received: " + projectPath);

                // Run validation
                Path path = Paths.get(projectPath);
                if (!Files.exists(path)) {
                    System.err.println("[ERROR] Path does not exist: " + projectPath);
                    sendResponse(exchange, 400,
                            "{\"success\":false,\"message\":\"Path does not exist: " + escapeJson(projectPath) + "\"}");
                    return;
                }

                System.out.println("[DEBUG] Path exists, starting validation...");

                // Store the project path for report resolution
                lastProjectPath = projectPath;

                // Capture output to parse statistics
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(baos);
                PrintStream oldOut = System.out;
                PrintStream oldErr = System.err;

                // Run MuleGuard validation (System.exit has been disabled in MuleGuardMain)
                try {
                    System.setOut(ps);
                    System.setErr(ps);

                    MuleGuardMain.main(new String[] { "-p", projectPath });
                    System.out.println("[DEBUG] Validation completed");
                } catch (Exception e) {
                    System.err.println("[ERROR] Validation failed: " + e.getMessage());
                    e.printStackTrace();
                    // Do not send response here, let the outer try-catch handle it if report is not
                    // found
                } finally {
                    System.setOut(oldOut);
                    System.setErr(oldErr);
                }

                // Parse output for statistics
                String output = baos.toString();
                System.out.println("[DEBUG] Captured output length: " + output.length());

                int totalApis = countOccurrences(output, "Validating API:")
                        + countOccurrences(output, "Validating Config:");
                int totalPassed = 0;
                int totalFailed = 0;

                // Parse each validation result line
                String[] lines = output.split("\n");
                for (String line : lines) {
                    if (line.contains("Passed:") && line.contains("Failed:")) {
                        try {
                            int passed = Integer.parseInt(line
                                    .substring(line.indexOf("Passed:") + 8, line.indexOf("|", line.indexOf("Passed:")))
                                    .trim());
                            int failed = Integer.parseInt(line.substring(line.indexOf("Failed:") + 8).trim());
                            totalPassed += passed;
                            totalFailed += failed;
                        } catch (Exception e) {
                            // Ignore parsing errors
                        }
                    }
                }

                int totalRules = totalPassed + totalFailed;

                System.out.println("[DEBUG] Stats - APIs: " + totalApis + ", Rules: " + totalRules + ", Passed: "
                        + totalPassed + ", Failed: " + totalFailed);

                // Find the generated report
                Path reportPath = path.resolve("muleguard-reports").resolve("CONSOLIDATED-REPORT.html");

                if (!Files.exists(reportPath)) {
                    System.err.println("[ERROR] Report not found at: " + reportPath);
                    sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Report generation failed\"}");
                    return;
                }

                System.out.println("[DEBUG] Report found at: " + reportPath);

                String response = String.format(
                        "{\"success\":true,\"message\":\"Validation completed\",\"reportPath\":\"%s\",\"totalApis\":%d,\"totalRules\":%d,\"passed\":%d,\"failed\":%d}",
                        reportPath.toAbsolutePath().toString().replace("\\", "\\\\"),
                        totalApis, totalRules, totalPassed, totalFailed);

                sendResponse(exchange, 200, response);

            } catch (Exception e) {
                System.err.println("[ERROR] Unexpected error in validation: " + e.getMessage());
                e.printStackTrace();
                try {
                    String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                    sendResponse(exchange, 500, "{\"success\":false,\"message\":\"" + escapeJson(errorMsg) + "\"}");
                } catch (IOException ioEx) {
                    System.err.println("[ERROR] Failed to send error response: " + ioEx.getMessage());
                }
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            byte[] responseBytes = response.getBytes("UTF-8");

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
                os.flush();
            }

            System.out.println("[DEBUG] Sent response: " + statusCode + ", " + responseBytes.length + " bytes");
        }

        private Map<String, String> parseFormData(String body) {
            Map<String, String> params = new HashMap<>();
            for (String pair : body.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    try {
                        params.put(
                                java.net.URLDecoder.decode(kv[0], "UTF-8"),
                                java.net.URLDecoder.decode(kv[1], "UTF-8"));
                    } catch (Exception e) {
                        // Skip invalid pairs
                    }
                }
            }
            return params;
        }

        private int countOccurrences(String str, String substring) {
            int count = 0;
            int index = 0;
            while ((index = str.indexOf(substring, index)) != -1) {
                count++;
                index += substring.length();
            }
            return count;
        }

        private String escapeJson(String s) {
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
        }
    }

    // Handler to serve report files
    static class ReportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestPath = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();

            Path reportPath = null;

            // If there's a query parameter with full path, use that
            if (query != null && query.startsWith("path=")) {
                String fullPath = java.net.URLDecoder.decode(query.substring(5), "UTF-8");
                reportPath = Paths.get(fullPath);
            } else {
                // Extract the relative path
                String relativePath;
                if (requestPath.startsWith("/report/")) {
                    // Path like /report/checklist.html
                    relativePath = requestPath.substring("/report/".length());
                } else if (requestPath.startsWith("/") && requestPath.endsWith(".html")) {
                    // Path like /CONSOLIDATED-REPORT.html or /checklist.html
                    relativePath = requestPath.substring(1); // Remove leading /
                } else {
                    // Not a report request, let HomePageHandler handle it
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }

                if (relativePath.isEmpty()) {
                    sendError(exchange, 400, "Missing report path");
                    return;
                }

                // Decode URL-encoded path
                relativePath = java.net.URLDecoder.decode(relativePath, "UTF-8");

                // Find the reports directory
                Path reportsDir;
                if (lastProjectPath != null) {
                    reportsDir = Paths.get(lastProjectPath).resolve("muleguard-reports");
                } else {
                    // Fallback to default location
                    reportsDir = Paths.get(System.getProperty("user.dir")).resolve("testData")
                            .resolve("muleguard-reports");
                }
                reportPath = reportsDir.resolve(relativePath).normalize();

                // Security check: ensure the resolved path is still within the reports
                // directory
                if (!reportPath.startsWith(reportsDir)) {
                    sendError(exchange, 403, "Access denied");
                    return;
                }
            }

            if (!Files.exists(reportPath)) {
                System.err.println("[ERROR] Report not found: " + reportPath);
                sendError(exchange, 404, "Report not found: " + reportPath.getFileName());
                return;
            }

            try {
                byte[] content = Files.readAllBytes(reportPath);

                // Set content type based on file extension
                String contentType = "text/html; charset=UTF-8";
                String fileName = reportPath.getFileName().toString().toLowerCase();
                if (fileName.endsWith(".css")) {
                    contentType = "text/css; charset=UTF-8";
                } else if (fileName.endsWith(".js")) {
                    contentType = "application/javascript; charset=UTF-8";
                } else if (fileName.endsWith(".xlsx")) {
                    contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                }

                // For HTML files, rewrite relative links to use /report/ prefix
                if (fileName.endsWith(".html")) {
                    String htmlContent = new String(content, "UTF-8");

                    // Add <base> tag to set the base URL for all relative links
                    // This ensures relative links are resolved relative to /report/ instead of the
                    // query parameter path
                    if (htmlContent.contains("<head>")) {
                        htmlContent = htmlContent.replace("<head>", "<head>\n<base href=\"/report/\">");
                        System.out.println("[DEBUG] Added base tag to: " + fileName);
                    }

                    // Rewrite absolute paths that start with / but aren't /report/ or http://
                    // This fixes Dashboard and Checklist buttons that use
                    // href="/CONSOLIDATED-REPORT.html"
                    htmlContent = htmlContent.replaceAll("href=\"/(?!report/)([^\"]+)\"", "href=\"/report/$1\"");

                    // Also rewrite href attributes for relative links (but not already rewritten
                    // ones)
                    // Match href="something" but not href="http://", href="/", href="#", or
                    // href="/report/"
                    htmlContent = htmlContent.replaceAll("href=\"(?!http|/|#)([^\"]+)\"", "href=\"/report/$1\"");

                    // Rewrite src attributes for relative resources
                    htmlContent = htmlContent.replaceAll("src=\"(?!http|/|#)([^\"]+)\"", "src=\"/report/$1\"");

                    System.out.println("[DEBUG] Rewrote HTML links for: " + fileName);
                    content = htmlContent.getBytes("UTF-8");
                }

                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, content.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content);
                }

                System.out.println("[DEBUG] Served report: " + reportPath.getFileName());
            } catch (IOException e) {
                sendError(exchange, 500, "Error reading report: " + e.getMessage());
            }
        }

        private void sendError(HttpExchange exchange, int code, String message) throws IOException {
            byte[] response = message.getBytes("UTF-8");
            exchange.sendResponseHeaders(code, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
}
