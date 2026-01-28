package com.raks.apiforge;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.raks.apiforge.jms.EmbeddedBrokerService;
import com.raks.apiforge.jms.JmsConnectorFactory;
import com.raks.apiforge.jms.JmsService;
import java.security.KeyStore;
import static spark.Spark.*;
public class ApiForgeWeb {
    private static final Logger logger = LoggerFactory.getLogger(ApiForgeWeb.class);
    private static final int DEFAULT_PORT = 4567;
    private static final ObjectMapper mapper = new ObjectMapper();
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
        try (java.io.InputStream is = ApiForgeWeb.class.getClassLoader().getResourceAsStream("config.yaml")) {
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
                String type = req.queryParams("type");
                String storageDir = (queryWorkDir != null && !queryWorkDir.isEmpty()) ? queryWorkDir : getStorageDir();
                BaselineStorageService storageService = new BaselineStorageService(storageDir);
                List<String> services = storageService.listServices(type);
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
        
        post("/api/certificates/upload", (req, res) -> {
            res.type("application/json");
            try {
                String fileName = req.queryParams("fileName");
                String queryWorkDir = req.queryParams("workDir");
                String storageDir = (queryWorkDir != null && !queryWorkDir.isEmpty()) ? queryWorkDir : getStorageDir();
                
                CertificateService certService = new CertificateService();
                Map<String, Object> result = certService.uploadCertificate(storageDir, fileName, req.bodyAsBytes());
                return mapper.writeValueAsString(result);
            } catch (Exception e) {
                logger.error("Certificate upload failed", e);
                res.status(500);
                return mapper.writeValueAsString(Collections.singletonMap("error", e.getMessage()));
            }
        });

        post("/api/certificates/validate", (req, res) -> {
            res.type("application/json");
            try {
                Map<String, Object> body = mapper.readValue(req.body(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                CertificateService certService = new CertificateService();
                Map<String, Object> result = certService.validateCertificate(body);
                return mapper.writeValueAsString(result);
            } catch (Exception e) {
                logger.error("Certificate validation failed", e);
                return "{\"valid\": false, \"error\": \"" + e.getMessage() + "\"}";
            }
        });
        // --- JMS API Endpoints ---

        // 1. Connect
        post("/api/jms/connect", (req, res) -> {
            res.type("application/json");
            try {
                ObjectMapper mapper = new ObjectMapper();
                JmsConnectorFactory.JmsConfig config = mapper.readValue(req.body(), JmsConnectorFactory.JmsConfig.class);
                JmsService.getInstance().connect(config);
                return "{\"success\": true, \"message\": \"Connected to " + config.getProvider() + "\"}";
            } catch (Exception e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });

        // 2. Disconnect
        post("/api/jms/disconnect", (req, res) -> {
            res.type("application/json");
            JmsService.getInstance().close();
            return "{\"success\": true}";
        });

        // 3. Send Message (Single or Batch)
        post("/api/jms/send", (req, res) -> {
            res.type("application/json");
            try {
                java.util.Map<String, Object> body = mapper.readValue(req.body(), java.util.Map.class);
                String destination = (String) body.get("destination");
                String destType = (String) body.get("destType");
                String payload = (String) body.get("payload");
                List<String> payloads = (List<String>) body.get("payloads");
                java.util.Map<String, Object> props = (java.util.Map<String, Object>) body.get("properties");
                int rateLimit = body.containsKey("rateLimit") ? (int) body.get("rateLimit") : 0;
                
                if (payloads != null && !payloads.isEmpty()) {
                    JmsService.getInstance().sendBatch(destination, payloads, rateLimit, destType);
                } else {
                    JmsService.getInstance().sendMessage(destination, payload, props, destType);
                }
                return "{\"success\": true}";
            } catch (Exception e) {
                logger.error("JMS Send failed", e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });
        
        // 4. Browsing
        post("/api/jms/browse", (req, res) -> {
            res.type("application/json");
            try {
                java.util.Map<String, Object> body = mapper.readValue(req.body(), java.util.Map.class);
                String destination = (String) body.get("destination");
                List<java.util.Map<String, Object>> msgs = JmsService.getInstance().browse(destination);
                return mapper.writeValueAsString(msgs);
            } catch (Exception e) {
                 return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });
        
        // 5. Consume (Single or Multiple)
        post("/api/jms/consume", (req, res) -> {
            res.type("application/json");
            try {
                java.util.Map<String, Object> body = mapper.readValue(req.body(), java.util.Map.class);
                String destination = (String) body.get("destination");
                String destType = (String) body.get("destType");
                int count = body.containsKey("count") ? Integer.parseInt(String.valueOf(body.get("count"))) : 1;
                
                if (count > 1) {
                    List<java.util.Map<String, Object>> msgs = JmsService.getInstance().receiveMultiple(destination, destType, count, 2000);
                    if (msgs.isEmpty()) return "{\"success\": true, \"empty\": true}";
                    return mapper.writeValueAsString(msgs);
                } else {
                    java.util.Map<String, Object> msg = JmsService.getInstance().receiveOnce(destination, destType, 2000);
                    if (msg == null) return "{\"success\": true, \"empty\": true}";
                    return mapper.writeValueAsString(msg);
                }
            } catch (Exception e) {
                 return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });

        // 6. Listen Start/Stop/Stats
        post("/api/jms/listen/start", (req, res) -> {
            res.type("application/json");
            try {
                java.util.Map<String, Object> body = mapper.readValue(req.body(), java.util.Map.class);
                String dest = (String) body.get("destination");
                String type = (String) body.get("destType");
                String count = String.valueOf(body.getOrDefault("consumers", "1"));
                JmsService.getInstance().startListeners(count, dest, type);
                return "{\"success\": true}";
            } catch (Exception e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });
        
        post("/api/jms/listen/stop", (req, res) -> {
            res.type("application/json");
            JmsService.getInstance().stopListeners();
            return "{\"success\": true}";
        });
        
        get("/api/jms/listen/stats", (req, res) -> {
            res.type("application/json");
            return mapper.writeValueAsString(JmsService.getInstance().getListenerStats());
        });

        // 7. Destination Management
        post("/api/jms/drain", (req, res) -> {
             res.type("application/json");
             try {
                java.util.Map<String, Object> body = mapper.readValue(req.body(), java.util.Map.class);
                int count = JmsService.getInstance().drainQueue((String)body.get("destination"), (String)body.get("destType"));
                return "{\"success\": true, \"count\": " + count + "}";
             } catch (Exception e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
             }
        });

        post("/api/jms/create", (req, res) -> {
            res.type("application/json");
            try {
                java.util.Map<String, Object> body = mapper.readValue(req.body(), java.util.Map.class);
                JmsService.getInstance().createDestinationAdmin((String)body.get("destination"), (String)body.get("destType"));
                return "{\"success\": true}";
            } catch (Exception e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });

        post("/api/jms/delete", (req, res) -> {
            res.type("application/json");
            try {
                java.util.Map<String, Object> body = mapper.readValue(req.body(), java.util.Map.class);
                boolean deleted = JmsService.getInstance().deleteDestination((String)body.get("destination"), (String)body.get("destType"));
                return "{\"success\": " + deleted + "}";
            } catch (Exception e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });

        post("/api/jms/stats", (req, res) -> {
            res.type("application/json");
            try {
                java.util.Map<String, Object> body = mapper.readValue(req.body(), java.util.Map.class);
                return mapper.writeValueAsString(JmsService.getInstance().getDestinationMetadata((String)body.get("destination"), (String)body.get("destType")));
            } catch (Exception e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });

        // 8. Embedded Broker
        post("/api/jms/embed/start", (req, res) -> {
            res.type("application/json");
            try {
                boolean success = EmbeddedBrokerService.startBroker();
                if (success) return "{\"success\": true}";
                return "{\"error\": \"Broker failed to start (check server logs)\"}";
            } catch (Throwable e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });

        post("/api/jms/embed/stop", (req, res) -> {
            res.type("application/json");
            EmbeddedBrokerService.stopBroker();
            return "{\"success\": true}";
        });

        get("/api/jms/embed/status", (req, res) -> {
            res.type("application/json");
            return "{\"running\": " + EmbeddedBrokerService.isRunning() + "}";
        });


        post("/api/jms/compare-selected", (req, res) -> {
            res.type("application/json");
            try {
                java.util.Map<String, Object> body = mapper.readValue(req.body(), java.util.Map.class);
                java.util.Map<String, Object> m1 = (java.util.Map<String, Object>) body.get("msg1");
                java.util.Map<String, Object> m2 = (java.util.Map<String, Object>) body.get("msg2");
                
                ComparisonResult result = new ComparisonResult();
                result.setOperationName("Manual JMS Comparison");
                result.setTimestamp(java.time.LocalDateTime.now().toString());
                
                ApiCallResult r1 = new ApiCallResult();
                r1.setResponsePayload((String) m1.get("payload"));
                // Safely convert headers to Map<String, String>
                Map<String, Object> h1 = (Map<String, Object>) m1.get("headers");
                Map<String, String> stringHeaders1 = new HashMap<>();
                if (h1 != null) h1.forEach((k, v) -> stringHeaders1.put(k, String.valueOf(v)));
                r1.setResponseHeaders(stringHeaders1);
                r1.setStatusCode(200);
                
                ApiCallResult r2 = new ApiCallResult();
                r2.setResponsePayload((String) m2.get("payload"));
                // Safely convert headers to Map<String, String>
                Map<String, Object> h2 = (Map<String, Object>) m2.get("headers");
                Map<String, String> stringHeaders2 = new HashMap<>();
                if (h2 != null) h2.forEach((k, v) -> stringHeaders2.put(k, String.valueOf(v)));
                r2.setResponseHeaders(stringHeaders2);
                r2.setStatusCode(200);
                
                result.setApi1(r1);
                result.setApi2(r2);
                
                ComparisonEngine.compare(result, "JMS", null, false);
                
                return mapper.writeValueAsString(result);
            } catch (Exception e) {
                logger.error("Manual comparison failed", e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });

        get("/api/jms/status", (req, res) -> {
            try {
                return mapper.writeValueAsString(JmsService.getInstance().getStatus());
            } catch (Exception e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });

        post("/api/jms/browse", (req, res) -> {
            try {
                Map<String, Object> body = mapper.readValue(req.body(), Map.class);
                String destination = (String) body.get("destination");
                List<Map<String, Object>> messages = JmsService.getInstance().browse(destination);
                return mapper.writeValueAsString(messages);
            } catch (Exception e) {
                logger.error("Failed to browse queue", e);
                res.status(500);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });

        post("/api/jms/consume", (req, res) -> {
            try {
                Map<String, Object> body = mapper.readValue(req.body(), Map.class);
                String destination = (String) body.get("destination");
                String destType = (String) body.get("destType");
                int count = body.containsKey("count") ? Integer.parseInt(String.valueOf(body.get("count"))) : 1;
                
                if (count > 1) {
                    List<Map<String, Object>> msgs = JmsService.getInstance().receiveMultiple(destination, destType, count, 2000);
                    if (msgs.isEmpty()) return "{\"success\": true, \"empty\": true}";
                    return mapper.writeValueAsString(msgs);
                } else {
                    Map<String, Object> msg = JmsService.getInstance().receiveOnce(destination, destType, 2000);
                    if (msg == null) return "{\"success\": true, \"empty\": true}";
                    return mapper.writeValueAsString(msg);
                }
            } catch (Exception e) {
                logger.error("Failed to consume message", e);
                res.status(500);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });

        post("/api/jms/stats", (req, res) -> {
            try {
                Map<String, Object> body = mapper.readValue(req.body(), Map.class);
                String destination = (String) body.get("destination");
                String destType = (String) body.get("destType");
                return mapper.writeValueAsString(JmsService.getInstance().getDestinationMetadata(destination, destType));
            } catch (Exception e) {
                res.status(500);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });


        post("/api/jms/baselines/capture", (req, res) -> {
            res.type("application/json");
            try {
                java.util.Map<String, Object> body = mapper.readValue(req.body(), java.util.Map.class);
                String serviceName = (String) body.get("serviceName");
                String description = (String) body.get("description");
                List<java.util.Map<String, Object>> messages = (List<java.util.Map<String, Object>>) body.get("messages");
                
                String storageDir = getStorageDir();
                BaselineStorageService storage = new BaselineStorageService(storageDir);
                String date = BaselineStorageService.getTodayDate();
                String runId = storage.generateRunId(serviceName, date, "JMS");
                
                RunMetadata metadata = new RunMetadata();
                metadata.setRunId(runId);
                metadata.setServiceName(serviceName);
                metadata.setCaptureDate(date);
                metadata.setCaptureTimestamp(java.time.LocalDateTime.now().toString());
                metadata.setTestType("JMS");
                metadata.setDescription(description);
                metadata.setTotalIterations(messages.size());
                
                List<BaselineStorageService.BaselineIteration> iterations = new ArrayList<>();
                for (int i = 0; i < messages.size(); i++) {
                    java.util.Map<String, Object> msg = messages.get(i);
                    // In capture mode, we might just be saving "responses" that we browsed
                    // or a "request" that we sent. Let's assume the body contains the payload.
                    String payload = (String) msg.get("payload");
                    Map<String, Object> rawHeaders = (Map<String, Object>) msg.get("headers");
                    Map<String, String> headers = stringifyHeaders(rawHeaders);
                    
                    IterationMetadata iterMeta = new IterationMetadata();
                    iterMeta.setIterationNumber(i + 1);
                    iterMeta.setTimestamp(java.time.LocalDateTime.now().toString());
                    
                    iterations.add(new BaselineStorageService.BaselineIteration(
                        i + 1, payload, headers, iterMeta,
                        payload, headers, new java.util.HashMap<>()
                    ));
                }
                
                storage.saveBaseline(metadata, iterations);
                return "{\"success\": true, \"runId\": \"" + runId + "\"}";
            } catch (Exception e) {
                logger.error("JMS Baseline capture failed", e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });

        post("/api/jms/baselines/replay", (req, res) -> {
            res.type("application/json");
            try {
                java.util.Map<String, Object> body = mapper.readValue(req.body(), java.util.Map.class);
                String service = (String) body.get("serviceName");
                String dateValue = (String) body.get("date");
                String date = normalizeDate(dateValue);
                String runId = (String) body.get("runId");
                String targetDest = (String) body.get("destination");
                String destType = (String) body.get("destType");
                
                BaselineStorageService storage = new BaselineStorageService(getStorageDir());
                BaselineStorageService.BaselineRun run = storage.loadBaseline(service, date, runId);
                
                List<ComparisonResult> results = new ArrayList<>();
                for (BaselineStorageService.BaselineIteration iter : run.getIterations()) {
                    // 1. Send (Replay)
                    JmsService.getInstance().sendMessage(targetDest, iter.getRequestPayload(), (Map)iter.getRequestHeaders(), destType);
                    
                    // 2. Wait and Browse (Auto-detect response)
                    Thread.sleep(1000); 
                    List<Map<String, Object>> browsed = JmsService.getInstance().browse(targetDest);
                    
                    Map<String, Object> latest = browsed.isEmpty() ? null : browsed.get(browsed.size() - 1);
                    
                    // 3. Compare
                    ComparisonResult result = new ComparisonResult();
                    result.setOperationName("JMS Replay Parity: " + iter.getIterationNumber());
                    
                    ApiCallResult r1 = new ApiCallResult(); // Saved Baseline (Golden)
                    r1.setResponsePayload(iter.getResponsePayload());
                    r1.setResponseHeaders(stringifyHeaders((Map)iter.getResponseHeaders()));
                    r1.setStatusCode(200);
                    
                    ApiCallResult r2 = new ApiCallResult(); // New browsed message
                    if (latest != null) {
                        r2.setResponsePayload((String) latest.get("body"));
                        
                        // Merge system and custom properties for a complete view
                        Map<String, Object> allProps = new LinkedHashMap<>();
                        Map<String, Object> systemProps = (Map<String, Object>) latest.get("systemProperties");
                        Map<String, Object> customProps = (Map<String, Object>) latest.get("customProperties");
                        if (systemProps != null) allProps.putAll(systemProps);
                        if (customProps != null) allProps.putAll(customProps);
                        
                        r2.setResponseHeaders(stringifyHeaders(allProps));
                        r2.setStatusCode(200);
                    } else {
                        r2.setStatusCode(404);
                        result.setErrorMessage("No message found in destination after replay.");
                    }
                    
                    result.setApi1(r1);
                    result.setApi2(r2);
                    ComparisonEngine.compare(result, "JMS", null, false);
                    results.add(result);
                }
                
                return mapper.writeValueAsString(results);
            } catch (Exception e) {
                logger.error("JMS Replay failed", e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });

        get("/api/jms/destinations", (d_req, d_res) -> {
            try {
                return mapper.writeValueAsString(JmsService.getInstance().getDestinations());
            } catch (Exception e) {
                d_res.status(500);
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
                        try (java.io.InputStream is = ApiForgeWeb.class.getResourceAsStream(resourcePath)) {
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

    private static Map<String, String> stringifyHeaders(Map<String, Object> props) {
        if (props == null) return Collections.emptyMap();
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            result.put(entry.getKey(), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
        }
        return result;
    }

    private static String normalizeDate(String date) {
        if (date == null) return null;
        return date.replace("-", "").replace("/", "");
    }
}
