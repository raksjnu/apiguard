package com.raks.apiurlcomparison;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.Desktop;
import java.net.URI;
import java.util.List;
import static spark.Spark.*;
public class ApiUrlComparisonWeb {
    private static final Logger logger = LoggerFactory.getLogger(ApiUrlComparisonWeb.class);
    private static final int DEFAULT_PORT = 4567;
    private static java.io.File explicitConfigFile;

    public static void setConfigFile(java.io.File file) {
        explicitConfigFile = file;
    }

    private static Config loadConfig() throws java.io.IOException {
        ObjectMapper yamlMapper = new ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
        
        if (explicitConfigFile != null && explicitConfigFile.exists()) {
            logger.info("Loading config from explicit file: {}", explicitConfigFile.getAbsolutePath());
            return yamlMapper.readValue(explicitConfigFile, Config.class);
        }


        java.io.File cwdConfig = new java.io.File("config.yaml");
        if (cwdConfig.exists()) {
            logger.info("Loading config from CWD: {}", cwdConfig.getAbsolutePath());
            return yamlMapper.readValue(cwdConfig, Config.class);
        }


        logger.info("Loading config from classpath resources");
        try (java.io.InputStream is = ApiUrlComparisonWeb.class.getClassLoader().getResourceAsStream("config.yaml")) {
             if (is == null) {
                 throw new java.io.FileNotFoundException("config.yaml not found in resources");
             }
             return yamlMapper.readValue(is, Config.class);
        }
    }
    

    private static String getStorageDir() {
        try {
            Config config = loadConfig();
            if (config.getBaseline() != null && config.getBaseline().getStorageDir() != null) {
                return config.getBaseline().getStorageDir();
            }
        } catch (Exception e) {
             logger.warn("Could not read config for storage dir, using default 'baselines'", e);
        }
        return "baselines";
    }

    public static void main(String[] args) {
        int port = findAvailablePort(DEFAULT_PORT);
        port(port);
        staticFiles.location("/public"); 
        logger.info("Starting Web GUI on port {}", port);


        post("/api/compare", (req, res) -> {
            res.type("application/json");
            try {
                ObjectMapper mapper = new ObjectMapper();
                Config config = mapper.readValue(req.body(), Config.class);
                logger.info("Received comparison request via Web GUI");
                ComparisonService service = new ComparisonService();
                List<ComparisonResult> results = service.execute(config);
                return mapper.writeValueAsString(results);
            } catch (Exception e) {
                logger.error("Error processing web request", e);
                res.status(500);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });
        get("/api/config", (req, res) -> {
            res.type("application/json");
            try {
                Config config = loadConfig();

                resolvePayloads(config);
                ObjectMapper jsonMapper = new ObjectMapper();
                return jsonMapper.writeValueAsString(config);
            } catch (Exception e) {
                logger.error("Error reading config", e);

                return "{}";
            }
        });
        get("/api/baselines/services", (req, res) -> {
            res.type("application/json");
            try {
                ObjectMapper mapper = new ObjectMapper();
                String queryWorkDir = req.queryParams("workDir");
                String storageDir = (queryWorkDir != null && !queryWorkDir.isEmpty()) ? queryWorkDir : getStorageDir();
                BaselineStorageService storageService = new BaselineStorageService(storageDir);
                List<String> services = storageService.listServices();
                return mapper.writeValueAsString(services);
            } catch (Exception e) {
                logger.error("Error fetching baseline services", e);
                res.status(500);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });
        get("/api/baselines/dates/:serviceName", (req, res) -> {
            res.type("application/json");
            try {
                String serviceName = req.params(":serviceName");
                ObjectMapper mapper = new ObjectMapper();
                String queryWorkDir = req.queryParams("workDir");
                String storageDir = (queryWorkDir != null && !queryWorkDir.isEmpty()) ? queryWorkDir : getStorageDir();
                BaselineStorageService storageService = new BaselineStorageService(storageDir);
                List<String> dates = storageService.listDates(serviceName);
                return mapper.writeValueAsString(dates);
            } catch (Exception e) {
                logger.error("Error fetching baseline dates", e);
                res.status(500);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });
        get("/api/baselines/runs/:serviceName/:date", (req, res) -> {
            res.type("application/json");
            try {
                String serviceName = req.params(":serviceName");
                String date = req.params(":date");
                ObjectMapper mapper = new ObjectMapper();
                String queryWorkDir = req.queryParams("workDir");
                String storageDir = (queryWorkDir != null && !queryWorkDir.isEmpty()) ? queryWorkDir : getStorageDir();
                BaselineStorageService storageService = new BaselineStorageService(storageDir);
                List<BaselineStorageService.RunInfo> runs = storageService.listRuns(serviceName, date);
                return mapper.writeValueAsString(runs);
            } catch (Exception e) {
                logger.error("Error fetching baseline runs", e);
                res.status(500);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });

        get("/api/baselines/runs/:serviceName/:date/:runId/endpoint", (req, res) -> {
            res.type("application/json");
            try {
                String serviceName = req.params(":serviceName");
                String date = req.params(":date");
                String runId = req.params(":runId");
                ObjectMapper mapper = new ObjectMapper();
                String queryWorkDir = req.queryParams("workDir");
                String storageDir = (queryWorkDir != null && !queryWorkDir.isEmpty()) ? queryWorkDir : getStorageDir();
                BaselineStorageService storageService = new BaselineStorageService(storageDir);
                String endpoint = storageService.getRunEndpoint(serviceName, date, runId);
                String payload = storageService.getRunRequestPayload(serviceName, date, runId);
                java.util.Map<String, String> headers = storageService.getRunRequestHeaders(serviceName, date, runId);
                RunMetadata metadata = storageService.getRunMetadata(serviceName, date, runId);
                
                java.util.Map<String, Object> result = new java.util.HashMap<>();
                result.put("endpoint", endpoint);
                result.put("payload", payload);
                result.put("headers", headers);
                result.put("metadata", metadata);
                
                return mapper.writeValueAsString(result);
            } catch (Exception e) {
                logger.error("Error fetching baseline endpoint", e);
                res.status(500);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });

        get("/api/baselines/runs/:serviceName/:date/:runId/details", (req, res) -> {
            res.type("application/json");
            try {
                String serviceName = req.params(":serviceName");
                String date = req.params(":date");
                String runId = req.params(":runId");
                
                String queryWorkDir = req.queryParams("workDir");
                String storageDir = (queryWorkDir != null && !queryWorkDir.isEmpty()) ? queryWorkDir : getStorageDir();
                
                BaselineStorageService storageService = new BaselineStorageService(storageDir);
                BaselineComparisonService comparisonService = new BaselineComparisonService(storageService);
                
                List<ComparisonResult> results = comparisonService.getBaselineAsResults(serviceName, date, runId);
                
                ObjectMapper mapper = new ObjectMapper();
                return mapper.writeValueAsString(results);
            } catch (Exception e) {
                logger.error("Error fetching baseline full details", e);
                res.status(500);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });
        
        get("/api/utils/ping", (req, res) -> {
            res.type("application/json");
            String targetUrl = req.queryParams("url");
            if (targetUrl == null || targetUrl.isEmpty()) return "{\"error\": \"URL is required\"}";
            
            long start = System.currentTimeMillis();
            java.util.Map<String, Object> pingRes = new java.util.HashMap<>();
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(targetUrl).openConnection();
                conn.setRequestMethod("GET"); // HEAD might be blocked by some servers, GET is more reliable for health checks
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
            return new ObjectMapper().writeValueAsString(pingRes);
        });

        get("/api/utils/wsdl", (req, res) -> {
            String targetUrl = req.queryParams("url");
            if (targetUrl == null || targetUrl.isEmpty()) return "{\"error\": \"URL is required\"}";
            
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(targetUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                
                int code = conn.getResponseCode();
                if (code >= 200 && code < 400) {
                    try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        res.type("text/xml");
                        return sb.toString();
                    }
                } else {
                    res.status(code);
                    return "Error fetching WSDL: HTTP " + code;
                }
            } catch (Exception e) {
                res.status(500);
                return "Error fetching WSDL: " + e.getMessage();
            }
        });

        get("/api/baselines/export", (req, res) -> {
            String service = req.queryParams("service");
            if (service == null) service = "ALL";
            
            String queryWorkDir = req.queryParams("workDir");
            String storageDir = (queryWorkDir != null && !queryWorkDir.isEmpty()) ? queryWorkDir : getStorageDir();
            
            UtilityService utilService = new UtilityService();
            java.io.File zipFile = utilService.exportBaselines(storageDir, service);
            
            res.header("Content-Disposition", "attachment; filename=" + zipFile.getName());
            res.type("application/zip");
            
            try (java.io.InputStream is = new java.io.FileInputStream(zipFile)) {
                javax.servlet.ServletOutputStream os = res.raw().getOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = is.read(buffer)) > 0) {
                    os.write(buffer, 0, len);
                }
                os.flush();
            } finally {
                zipFile.delete();
            }
            return res.raw();
        });

        post("/api/baselines/import", (req, res) -> {
            boolean overwrite = "true".equalsIgnoreCase(req.queryParams("overwrite"));
            String queryWorkDir = req.queryParams("workDir");
            String storageDir = (queryWorkDir != null && !queryWorkDir.isEmpty()) ? queryWorkDir : getStorageDir();

            UtilityService utilService = new UtilityService();
            // Using raw input stream for the zip file
            byte[] bytes = req.bodyAsBytes();
            try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bytes)) {
                if (!overwrite) {
                    List<String> conflicts = utilService.detectConflicts(storageDir, bais);
                    if (!conflicts.isEmpty()) {
                        res.status(409); // Conflict
                        return new ObjectMapper().writeValueAsString(conflicts);
                    }
                    // Reset stream if no conflicts
                    bais.reset(); 
                }
                
                // Re-read or just use the stream if no conflicts
                try (java.io.ByteArrayInputStream importStream = new java.io.ByteArrayInputStream(bytes)) {
                    utilService.importBaselines(storageDir, importStream);
                }
                return "{\"success\": true}";
            } catch (Exception e) {
                logger.error("Import failed", e);
                res.status(500);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });
        awaitInitialization();
        logger.info("Server started. Access at http://localhost:{}", port);
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI("http://localhost:" + port));
            }
        } catch (Exception e) {
            logger.warn("Could not open browser automatically: {}", e.getMessage());
        }
    }
    private static int findAvailablePort(int startPort) {
        int port = startPort;
        while (port < 65535) {
            try (java.net.ServerSocket socket = new java.net.ServerSocket(port)) {
                return port;
            } catch (java.io.IOException e) {
                logger.warn("Port {} is already in use, trying next...", port);
                port++;
            }
        }
        throw new RuntimeException("No available ports found starting from " + startPort);
    }
    
    private static void resolvePayloads(Config config) {
        if (config.getRestApis() != null) {
            resolveApiPayloads(config.getRestApis().get("api1"));
            resolveApiPayloads(config.getRestApis().get("api2"));
        }
        if (config.getSoapApis() != null) {
            resolveApiPayloads(config.getSoapApis().get("api1"));
            resolveApiPayloads(config.getSoapApis().get("api2"));
        }
    }

    private static void resolveApiPayloads(ApiConfig api) {
        if (api == null || api.getOperations() == null) return;
        for (Operation op : api.getOperations()) {
            String templatePath = op.getPayloadTemplatePath();
            if (templatePath != null && !templatePath.isEmpty() && !templatePath.trim().startsWith("<")) {
                try {
                    java.nio.file.Path path = java.nio.file.Paths.get(templatePath);

                    if (java.nio.file.Files.exists(path)) {
                        logger.info("Resolving payload from file: {}", path.toAbsolutePath());
                        String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
                        op.setPayloadTemplatePath(content);
                    } else {
                        // Try classpath fallback
                        String resourcePath = templatePath.replace('\\', '/');
                        if (!resourcePath.startsWith("/")) resourcePath = "/" + resourcePath;
                        try (java.io.InputStream is = ApiUrlComparisonWeb.class.getResourceAsStream(resourcePath)) {
                            if (is != null) {
                                logger.info("Resolving payload from classpath: {}", resourcePath);
                                String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                                op.setPayloadTemplatePath(content);
                            } else {
                                logger.debug("Payload template not found as file or resource: {}", templatePath);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to resolve payload path: {} - {}", templatePath, e.getMessage());
                }
            }
        }
    }
}
