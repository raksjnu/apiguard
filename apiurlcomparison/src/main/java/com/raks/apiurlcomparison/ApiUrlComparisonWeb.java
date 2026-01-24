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
            if (op.getPayloadTemplatePath() != null && !op.getPayloadTemplatePath().isEmpty()) {
                try {
                    java.nio.file.Path path = java.nio.file.Paths.get(op.getPayloadTemplatePath());

                    if (java.nio.file.Files.exists(path)) {
                        String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
                        op.setPayloadTemplatePath(content);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to resolve payload path: {}", op.getPayloadTemplatePath());
                }
            }
        }
    }
}
