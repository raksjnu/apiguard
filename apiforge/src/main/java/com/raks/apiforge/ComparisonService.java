package com.raks.apiforge;
import com.raks.apiforge.http.ApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class ComparisonService {
    private static final Logger logger = LoggerFactory.getLogger(ComparisonService.class);
    public List<ComparisonResult> execute(Config config) {
        if ("BASELINE".equalsIgnoreCase(config.getComparisonMode())) {
            return executeBaselineMode(config);
        }
        List<ComparisonResult> allResults = new ArrayList<>();
        logger.info("Generating iterations with strategy: {}", config.getIterationController());
        List<Map<String, Object>> iterations = TestDataGenerator.generate(
                config.getTokens(),
                config.getMaxIterations(),
                config.getIterationController());
        if (config.getTokens() != null && !config.getTokens().isEmpty()) {
            iterations.add(0, new HashMap<>());
        }
        
        // Identify all tokens actually used in the templates/config
        java.util.Set<String> usedTokens = identifyUsedTokens(config);
        
        int iterationCount = 0;
        int totalIterations = iterations.size();
        for (Map<String, Object> currentTokens : iterations) {
            iterationCount++;
            boolean isOriginal = (iterationCount == 1);
            
            // Smart Iteration Check:
            // If this is NOT the original iteration, and NONE of the tokens in the current map 
            // are actually used by the target APIs, then this iteration produces an identical request 
            // to the original (or baseline). We should skip it to save resources.
            if (!isOriginal && !currentTokens.isEmpty()) {
                boolean isRelevent = false;
                for (String tokenKey : currentTokens.keySet()) {
                    if (usedTokens.contains(tokenKey)) {
                        isRelevent = true;
                        break;
                    }
                }
                if (!isRelevent) {
                    logger.info("Skipping iteration {}: Tokens {} do not appear to be used (explicitly or implicitly) in the current API configuration.", iterationCount, currentTokens.keySet());
                    continue;
                }
            }

            logger.info("Running iteration {}: {}{}", iterationCount, currentTokens,
                    isOriginal ? " (Original Input Payload)" : "");
            try {
                if ("REST".equalsIgnoreCase(config.getTestType())) {
                    processApis(config.getRestApis(), currentTokens, allResults, config.getTestType(), isOriginal, config.getIgnoredFields(), config.isIgnoreHeaders(), iterationCount, totalIterations);
                } else if ("SOAP".equalsIgnoreCase(config.getTestType())) {
                    processApis(config.getSoapApis(), currentTokens, allResults, config.getTestType(), isOriginal, config.getIgnoredFields(), config.isIgnoreHeaders(), iterationCount, totalIterations);
                } else {
                    logger.error("Invalid testType specified in config: {}", config.getTestType());
                }
            } catch (Exception e) {
                logger.error("Error during iteration {}: {}", iterationCount, e.getMessage(), e);
            }
        }
        return allResults;
    }
    private List<ComparisonResult> executeBaselineMode(Config config) {
        try {
            Config.BaselineConfig baselineConfig = config.getBaseline();
            if (baselineConfig == null) {
                throw new IllegalArgumentException(
                        "Baseline configuration is required when comparisonMode is BASELINE");
            }
            String storageDir = baselineConfig.getStorageDir();
            BaselineStorageService storageService = new BaselineStorageService(storageDir);
            BaselineComparisonService baselineService = new BaselineComparisonService(storageService);
            String operation = baselineConfig.getOperation();
            if ("CAPTURE".equalsIgnoreCase(operation)) {
                logger.info("Executing baseline CAPTURE mode");
                return baselineService.captureBaseline(config);
            } else if ("COMPARE".equalsIgnoreCase(operation)) {
                logger.info("Executing baseline COMPARE mode");
                return baselineService.compareWithBaseline(config);
            } else {
                throw new IllegalArgumentException(
                        "Invalid baseline operation: " + operation + ". Must be CAPTURE or COMPARE");
            }
        } catch (Exception e) {
            logger.error("Error in baseline mode: {}", e.getMessage(), e);
            List<ComparisonResult> errorResults = new ArrayList<>();
            ComparisonResult errorResult = new ComparisonResult();
            errorResult.setStatus(ComparisonResult.Status.ERROR);
            errorResult.setErrorMessage("Baseline mode failed: " + e.getMessage());
            errorResults.add(errorResult);
            return errorResults;
        }
    }
    private void processApis(Map<String, ApiConfig> apis, Map<String, Object> currentTokens,
            List<ComparisonResult> allResults, String apiType, boolean isOriginal, List<String> ignoredFields, boolean ignoreHeaders, int iterationNumber, int totalIterations) {
        if (apis == null || apis.isEmpty()) {
            logger.warn("No {} APIs configured.", apiType);
            return;
        }
        ApiConfig api1Config = apis.get("api1");
        ApiConfig api2Config = apis.get("api2");
        if (api1Config == null || api2Config == null) {
            logger.error("Comparison requires both 'api1' and 'api2' to be configured for the test type '{}'.",
                    apiType);
            return;
        }
        for (Operation op1 : api1Config.getOperations()) {
            Operation op2 = api2Config.getOperations().stream()
                    .filter(o -> op1.getName().equals(o.getName()))
                    .findFirst()
                    .orElse(null);
            if (op2 == null) {
                logger.warn("No matching operation named '{}' found in api2. Skipping comparison for this operation.",
                        op1.getName());
                continue;
            }
            String method1 = op1.getMethods().get(0);
            String method2 = op2.getMethods().get(0);
            ComparisonResult result = new ComparisonResult();
            String opName = op1.getName();
            if (isOriginal) {
                opName += " (Original Input Payload)";
            }
            result.setOperationName(opName);
            result.setIterationTokens(new HashMap<>(currentTokens));
            result.setTimestamp(java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            ApiClient client1 = new ApiClient(api1Config.getAuthentication());
            ApiClient client2 = new ApiClient(api2Config.getAuthentication());
            ApiCallResult api1CallResult = new ApiCallResult();
            ApiCallResult api2CallResult = new ApiCallResult();
            result.setApi1(api1CallResult);
            result.setApi2(api2CallResult);
            // --- Initialize Metadata and Results ---
            Map<String, Object> meta1 = new java.util.LinkedHashMap<>();
            Map<String, Object> meta2 = new java.util.LinkedHashMap<>();
            api1CallResult.setMetadata(meta1);
            api2CallResult.setMetadata(meta2);

            try {
                String path1 = op1.getPath() != null ? op1.getPath() : "";
                String path2 = op2.getPath() != null ? op2.getPath() : "";
                
                String rawUrl1 = constructUrl(api1Config.getBaseUrl(), path1, op1.getQueryParams(), apiType);
                String rawUrl2 = constructUrl(api2Config.getBaseUrl(), path2, op2.getQueryParams(), apiType);
                
                String url1 = interpolate(rawUrl1, currentTokens);
                String url2 = interpolate(rawUrl2, currentTokens);
                api1CallResult.setUrl(url1);
                api1CallResult.setMethod(method1);
                api1CallResult.setRequestHeaders(op1.getHeaders());
                String payload1 = null;
                if (op1.getPayloadTemplatePath() != null && !op1.getPayloadTemplatePath().isEmpty()) {
                    try {
                        PayloadProcessor processor1 = new PayloadProcessor(op1.getPayloadTemplatePath(), apiType);
                        payload1 = processor1.process(currentTokens);
                    } catch (Exception e) {
                        logger.warn("Could not process payload template: {}", e.getMessage());
                        payload1 = op1.getPayloadTemplatePath();
                    }
                }
                api1CallResult.setRequestPayload(payload1);
                api2CallResult.setUrl(url2);
                api2CallResult.setMethod(method2);
                api2CallResult.setRequestHeaders(op2.getHeaders());
                String payload2 = null;
                if (op2.getPayloadTemplatePath() != null && !op2.getPayloadTemplatePath().isEmpty()) {
                    try {
                        PayloadProcessor processor2 = new PayloadProcessor(op2.getPayloadTemplatePath(), apiType);
                        payload2 = processor2.process(currentTokens);
                    } catch (Exception e) {
                        logger.warn("Could not process payload template: {}", e.getMessage());
                        payload2 = op2.getPayloadTemplatePath();
                    }
                }
                api2CallResult.setRequestPayload(payload2);
                
                api1CallResult.setRequestHeaders(op1.getHeaders());
                api1CallResult.setRequestPayload(payload1);
                api2CallResult.setRequestHeaders(op2.getHeaders());
                api2CallResult.setRequestPayload(payload2);

                // --- Enrich Metadata ---
                if (api1Config.getAuthentication() != null) {
                    Authentication auth = api1Config.getAuthentication();
                    Map<String, String> authInfo = new java.util.LinkedHashMap<>();
                    if (auth.getClientId() != null) authInfo.put("clientId", auth.getClientId());
                    if (auth.getPfxPath() != null) authInfo.put("pfxPath", auth.getPfxPath());
                    if (auth.getClientCertPath() != null) authInfo.put("clientCertPath", auth.getClientCertPath());
                    if (auth.getClientKeyPath() != null) authInfo.put("clientKeyPath", auth.getClientKeyPath());
                    if (auth.getCaCertPath() != null) authInfo.put("caCertPath", auth.getCaCertPath());
                    if (auth.getPassphrase() != null && !auth.getPassphrase().isEmpty()) authInfo.put("passphrase", "********");
                    if (!authInfo.isEmpty()) meta1.put("authentication", authInfo);
                }
                if (payload1 != null) meta1.put("requestSize", payload1.getBytes().length);

                if (api2Config.getAuthentication() != null) {
                    Authentication auth = api2Config.getAuthentication();
                    Map<String, String> authInfo = new java.util.LinkedHashMap<>();
                    if (auth.getClientId() != null && !auth.getClientId().trim().isEmpty()) authInfo.put("clientId", auth.getClientId());
                    if (auth.getPfxPath() != null && !auth.getPfxPath().trim().isEmpty()) authInfo.put("pfxPath", auth.getPfxPath());
                    if (auth.getClientCertPath() != null && !auth.getClientCertPath().trim().isEmpty()) authInfo.put("clientCertPath", auth.getClientCertPath());
                    if (auth.getClientKeyPath() != null && !auth.getClientKeyPath().trim().isEmpty()) authInfo.put("clientKeyPath", auth.getClientKeyPath());
                    if (auth.getCaCertPath() != null && !auth.getCaCertPath().trim().isEmpty()) authInfo.put("caCertPath", auth.getCaCertPath());
                    if (auth.getPassphrase() != null && !auth.getPassphrase().trim().isEmpty()) authInfo.put("passphrase", "********");
                    if (!authInfo.isEmpty()) meta2.put("authentication", authInfo);
                }
                if (op2.getQueryParams() != null && !op2.getQueryParams().isEmpty()) {
                    meta2.put("queryParams", op2.getQueryParams());
                }
                if (payload2 != null) meta2.put("requestSize", payload2.getBytes().length);
                
                // --- Add Contextual Metadata ---
                meta1.put("operation", op1.getName());
                meta1.put("method", method1);
                meta1.put("testType", apiType);
                meta1.put("iterationNumber", iterationNumber);
                meta1.put("totalIterations", totalIterations);

                meta2.put("operation", op2.getName());
                meta2.put("method", method2);
                meta2.put("testType", apiType);
                meta2.put("iterationNumber", iterationNumber);
                meta2.put("totalIterations", totalIterations);

                long start1 = System.currentTimeMillis();
                com.raks.apiforge.http.HttpResponse httpResponse1 = client1.sendRequest(url1, method1, op1.getHeaders(), payload1);
                api1CallResult.setDuration(System.currentTimeMillis() - start1);
                api1CallResult.setStatusCode(httpResponse1.getStatusCode());
                api1CallResult.setResponsePayload(httpResponse1.getBody());
                api1CallResult.setResponseHeaders(httpResponse1.getHeaders());
                api1CallResult.setRequestHeaders(httpResponse1.getRequestHeaders());
                if (httpResponse1.getBody() != null) meta1.put("responseSize", httpResponse1.getBody().getBytes().length);
                meta1.put("statusCode", String.valueOf(httpResponse1.getStatusCode()));
                meta1.put("duration", api1CallResult.getDuration());
                
                long start2 = System.currentTimeMillis();
                com.raks.apiforge.http.HttpResponse httpResponse2 = client2.sendRequest(url2, method2, op2.getHeaders(), payload2);
                api2CallResult.setDuration(System.currentTimeMillis() - start2);
                api2CallResult.setStatusCode(httpResponse2.getStatusCode());
                api2CallResult.setResponsePayload(httpResponse2.getBody());
                api2CallResult.setResponseHeaders(httpResponse2.getHeaders());
                api2CallResult.setRequestHeaders(httpResponse2.getRequestHeaders());
                if (httpResponse2.getBody() != null) meta2.put("responseSize", httpResponse2.getBody().getBytes().length);
                meta2.put("statusCode", String.valueOf(httpResponse2.getStatusCode()));
                meta2.put("duration", api2CallResult.getDuration());
                
                logger.info("Comparison - API1 status: {}, API2 status: {}", 
                    httpResponse1.getStatusCode(), httpResponse2.getStatusCode());
                
                ComparisonEngine.compare(result, apiType, ignoredFields, ignoreHeaders);
                
            } catch (Exception e) {
                logger.error("Error during operation '{}' comparison: {}", op1.getName(), e.getMessage());
                result.setErrorMessage("Comparison failed: " + e.getMessage());
                result.setStatus(ComparisonResult.Status.ERROR);
                
                // Ensure some metadata exists even on error
                if (meta1.isEmpty()) {
                    meta1.put("operation", op1.getName());
                    meta1.put("method", method1);
                }
                if (meta2.isEmpty()) {
                    meta2.put("operation", op2.getName());
                    meta2.put("method", method2);
                }
            }
            allResults.add(result);
        }
    }
    private String constructUrl(String baseUrl, String path, Map<String, String> queryParams, String apiType) {
        if (baseUrl == null) baseUrl = "";
        String url = baseUrl.trim();

        if (!"SOAP".equalsIgnoreCase(apiType)) {
            // Remove trailing slash from base if path exists
            if (path != null && !path.trim().isEmpty()) {
                if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
                String normalizedPath = path.trim().startsWith("/") ? path.trim() : "/" + path.trim();
                
                // Only append path if not already present at the end of baseUrl (before query)
                String baseNoQuery = url.contains("?") ? url.substring(0, url.indexOf("?")) : url;
                if (!baseNoQuery.endsWith(normalizedPath)) {
                    // Inject path BEFORE query string if baseUrl has one
                    if (url.contains("?")) {
                        int qIdx = url.indexOf("?");
                        url = url.substring(0, qIdx) + normalizedPath + url.substring(qIdx);
                    } else {
                        url += normalizedPath;
                    }
                }
            }
        }

        if (queryParams != null && !queryParams.isEmpty()) {
            StringBuilder sb = new StringBuilder(url);
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || key.isEmpty()) continue;
                
                // Avoid duplicating if already in URL string
                if (url.contains(key + "=")) continue;

                sb.append(sb.indexOf("?") == -1 ? "?" : "&");
                sb.append(key).append("=").append(val != null ? val : "");
            }
            url = sb.toString();
        }
        return url;
    }

    private String interpolate(String text, Map<String, Object> tokens) {
        if (text == null || tokens == null) return text;
        String result = text;
        for (Map.Entry<String, Object> entry : tokens.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    public static java.util.Set<String> identifyUsedTokens(Config config) {
        java.util.Set<String> used = new java.util.HashSet<>();
        if (config.getTokens() == null) return used;
        
        List<ApiConfig> apis = new ArrayList<>();
        if (config.getRestApis() != null) {
            apis.add(config.getRestApis().get("api1"));
            apis.add(config.getRestApis().get("api2"));
        }
        if (config.getSoapApis() != null) {
            apis.add(config.getSoapApis().get("api1"));
            apis.add(config.getSoapApis().get("api2"));
        }
        
        for (String token : config.getTokens().keySet()) {
            boolean found = false;
            String strictPattern = "{{" + token + "}}";
            
            for (ApiConfig api : apis) {
                if (api == null) continue;
                String baseUrl = api.getBaseUrl() != null ? api.getBaseUrl().toLowerCase() : "";
                if (baseUrl.contains(strictPattern.toLowerCase()) || baseUrl.contains(token.toLowerCase())) { found = true; break; }
                
                if (api.getOperations() != null) {
                    for (Operation op : api.getOperations()) {
                        String payload = op.getPayloadTemplatePath() != null ? op.getPayloadTemplatePath().toLowerCase() : "";
                        if (payload.contains(strictPattern.toLowerCase()) || payload.contains(token.toLowerCase())) { 
                            found = true; break; 
                        }
                        if (op.getHeaders() != null) {
                            for (Map.Entry<String, String> h : op.getHeaders().entrySet()) {
                                 String hVal = h.getValue() != null ? h.getValue().toLowerCase() : "";
                                 if (hVal.contains(strictPattern.toLowerCase()) || hVal.contains(token.toLowerCase())) { found = true; break; }
                            }
                        }
                        if (found) break;
                    }
                }
                if (found) break;
            }
            if (found) used.add(token);
        }
        return used;
    }
}
