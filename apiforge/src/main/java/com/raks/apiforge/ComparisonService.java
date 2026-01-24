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
                    processApis(config.getRestApis(), currentTokens, allResults, config.getTestType(), isOriginal, config.getIgnoredFields(), config.isIgnoreHeaders());
                } else if ("SOAP".equalsIgnoreCase(config.getTestType())) {
                    processApis(config.getSoapApis(), currentTokens, allResults, config.getTestType(), isOriginal, config.getIgnoredFields(), config.isIgnoreHeaders());
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
            List<ComparisonResult> allResults, String apiType, boolean isOriginal, List<String> ignoredFields, boolean ignoreHeaders) {
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
            try {
                String path1 = op1.getPath() != null ? op1.getPath() : "";
                String path2 = op2.getPath() != null ? op2.getPath() : "";
                String url1 = constructUrl(api1Config.getBaseUrl(), path1, apiType);
                String url2 = constructUrl(api2Config.getBaseUrl(), path2, apiType);
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
                
                long start1 = System.currentTimeMillis();
                com.raks.apiforge.http.HttpResponse httpResponse1 = client1.sendRequest(url1, method1, op1.getHeaders(), payload1);
                api1CallResult.setDuration(System.currentTimeMillis() - start1);
                api1CallResult.setStatusCode(httpResponse1.getStatusCode());
                api1CallResult.setResponsePayload(httpResponse1.getBody());
                api1CallResult.setResponseHeaders(httpResponse1.getHeaders());

                // Reflect actual headers sent (including Authorization)
                Map<String, String> actualHeaders1 = new HashMap<>(op1.getHeaders() != null ? op1.getHeaders() : new HashMap<>());
                if (client1.getAccessToken() != null) actualHeaders1.put("Authorization", "Bearer " + client1.getAccessToken());
                else if (api1Config.getAuthentication() != null && api1Config.getAuthentication().getClientId() != null) {
                    String auth = api1Config.getAuthentication().getClientId() + ":" + api1Config.getAuthentication().getClientSecret();
                    actualHeaders1.put("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString(auth.getBytes()));
                }
                api1CallResult.setRequestHeaders(actualHeaders1);
                
                long start2 = System.currentTimeMillis();
                com.raks.apiforge.http.HttpResponse httpResponse2 = client2.sendRequest(url2, method2, op2.getHeaders(), payload2);
                api2CallResult.setDuration(System.currentTimeMillis() - start2);
                api2CallResult.setStatusCode(httpResponse2.getStatusCode());
                api2CallResult.setResponsePayload(httpResponse2.getBody());
                api2CallResult.setResponseHeaders(httpResponse2.getHeaders());

                Map<String, String> actualHeaders2 = new HashMap<>(op2.getHeaders() != null ? op2.getHeaders() : new HashMap<>());
                if (client2.getAccessToken() != null) actualHeaders2.put("Authorization", "Bearer " + client2.getAccessToken());
                else if (api2Config.getAuthentication() != null && api2Config.getAuthentication().getClientId() != null) {
                    String auth = api2Config.getAuthentication().getClientId() + ":" + api2Config.getAuthentication().getClientSecret();
                    actualHeaders2.put("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString(auth.getBytes()));
                }
                api2CallResult.setRequestHeaders(actualHeaders2);
                
                logger.info("Comparison - API1 status: {}, API2 status: {}", 
                    httpResponse1.getStatusCode(), httpResponse2.getStatusCode());
                
                ComparisonEngine.compare(result, apiType, ignoredFields, ignoreHeaders);
                
            } catch (Exception e) {
                logger.error("Error during operation '{}' comparison: {}", op1.getName(), e.getMessage());
                result.setErrorMessage("Operation failed: " + e.getMessage());
                result.setStatus(ComparisonResult.Status.ERROR);
            }
            allResults.add(result);
        }
    }
    private String constructUrl(String baseUrl, String path, String apiType) {
        if ("SOAP".equalsIgnoreCase(apiType)) {
            return baseUrl;
        }
        if (baseUrl == null)
            return "";
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (path == null || path.trim().isEmpty()) {
            return normalizedBase;
        }
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        if (normalizedBase.endsWith(normalizedPath)) {
            return normalizedBase;
        }
        return normalizedBase + normalizedPath;
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
