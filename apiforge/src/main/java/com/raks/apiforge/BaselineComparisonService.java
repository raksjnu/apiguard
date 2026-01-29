package com.raks.apiforge;
import com.raks.apiforge.http.ApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
public class BaselineComparisonService {
    private static final Logger logger = LoggerFactory.getLogger(BaselineComparisonService.class);
    private final BaselineStorageService storageService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public BaselineComparisonService(BaselineStorageService storageService) {
        this.storageService = storageService;
    }
    public List<ComparisonResult> captureBaseline(Config config) throws Exception {
        Config.BaselineConfig baselineConfig = config.getBaseline();
        if (baselineConfig == null || baselineConfig.getServiceName() == null) {
            throw new IllegalArgumentException("Baseline configuration with serviceName is required for CAPTURE mode");
        }
        String serviceName = baselineConfig.getServiceName();
        String date = BaselineStorageService.getTodayDate();
        String runId = storageService.generateRunId(serviceName, date, config.getTestType());
        logger.info("Capturing baseline for service: {}, date: {}, run: {}, type: {}", serviceName, date, runId, config.getTestType());
        List<Map<String, Object>> iterations = TestDataGenerator.generate(
                config.getTokens(),
                config.getMaxIterations(),
                config.getIterationController());
        if (config.getTokens() != null && !config.getTokens().isEmpty()) {
            iterations.add(0, new HashMap<>());
        }
        
        // Identify used tokens using the shared utility from ComparisonService
        java.util.Set<String> usedTokens = ComparisonService.identifyUsedTokens(config);

        Map<String, ApiConfig> apis = "SOAP".equalsIgnoreCase(config.getTestType())
                ? config.getSoapApis()
                : config.getRestApis();
        if (apis == null || apis.get("api1") == null) {
            throw new IllegalArgumentException("api1 configuration is required for baseline capture");
        }
        ApiConfig apiConfig = apis.get("api1");
        List<ComparisonResult> results = new ArrayList<>();
        List<BaselineStorageService.BaselineIteration> baselineIterations = new ArrayList<>();
        int iterationNumber = 0;
        for (Map<String, Object> currentTokens : iterations) {
            iterationNumber++;
            boolean isOriginal = (iterationNumber == 1);
            
            // Smart Iteration Check (Shared Logic)
            if (!isOriginal && !currentTokens.isEmpty()) {
                boolean isRelevent = false;
                for (String tokenKey : currentTokens.keySet()) {
                    if (usedTokens.contains(tokenKey)) {
                        isRelevent = true;
                        break;
                    }
                }
                if (!isRelevent) {
                    logger.info("Skipping iteration {}: Tokens {} do not appear to be used (explicitly or implicitly) in the current API configuration.", iterationNumber, currentTokens.keySet());
                    continue;
                }
            }

            logger.info("Capturing iteration {}: {}{}", iterationNumber, currentTokens,
                    isOriginal ? " (Original Input Payload)" : "");
            try {
                // CAPTURE mode: No forced data, process templates normally
                ComparisonResult result = executeApiCall(
                        apiConfig, currentTokens, config.getTestType(), iterationNumber, iterations.size(), isOriginal, null, null);
                
                // USER REQUEST CHECK: If status code is error, mark as ERROR
                if (result.getApi1().getStatusCode() >= 400) {
                     result.setStatus(ComparisonResult.Status.ERROR);
                     result.setErrorMessage("HTTP Error " + result.getApi1().getStatusCode());
                } else {
                     result.setStatus(ComparisonResult.Status.MATCH);
                } 
                result.setBaselineServiceName(serviceName);
                result.setBaselineDate(date);
                result.setBaselineRunId(runId);
                String protocol = storageService.getProtocolFromType(config.getTestType());
                String baselinePath = storageService.getRunDirectory(protocol, serviceName, date, runId).toString();
                result.setBaselinePath(baselinePath);
                result.setBaselineDescription(baselineConfig.getDescription());
                result.setBaselineTags(baselineConfig.getTags());
                result.setBaselineCaptureTimestamp(ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                results.add(result);
                BaselineStorageService.BaselineIteration baselineIter = convertToBaselineIteration(
                        result, iterationNumber, currentTokens, apiConfig, config.getTestType());
                baselineIterations.add(baselineIter);
            } catch (Exception e) {
                logger.error("Error capturing iteration {}: {}", iterationNumber, e.getMessage(), e);
                ComparisonResult errorResult = new ComparisonResult();
                errorResult.setOperationName(apiConfig.getOperations().get(0).getName());
                errorResult.setStatus(ComparisonResult.Status.ERROR);
                errorResult.setErrorMessage("Capture failed: " + e.getMessage());
                results.add(errorResult);
            }
        }
        RunMetadata runMetadata = createRunMetadata(
                runId, serviceName, date, apiConfig, config, baselineIterations.size());
        
        // --- Certificate Persistence Fix ---
        if (runMetadata.getConfigUsed() != null && runMetadata.getConfigUsed().containsKey("authentication")) {
            Authentication auth = objectMapper.convertValue(runMetadata.getConfigUsed().get("authentication"), Authentication.class);
            String protocol = storageService.getProtocolFromType(config.getTestType());
            Path runDir = storageService.getRunDirectory(protocol, serviceName, date, runId);
            
            auth.setPfxPath(storageService.copyReferencedFile(runDir, auth.getPfxPath()));
            auth.setClientCertPath(storageService.copyReferencedFile(runDir, auth.getClientCertPath()));
            auth.setClientKeyPath(storageService.copyReferencedFile(runDir, auth.getClientKeyPath()));
            auth.setCaCertPath(storageService.copyReferencedFile(runDir, auth.getCaCertPath()));
            
            runMetadata.getConfigUsed().put("authentication", auth);
        }
        
        storageService.saveBaseline(runMetadata, baselineIterations);
        logger.info("Baseline captured successfully: {}/{}/{} with {} iterations",
                serviceName, date, runId, baselineIterations.size());
        return results;
    }
    public List<ComparisonResult> compareWithBaseline(Config config) throws Exception {
        Config.BaselineConfig baselineConfig = config.getBaseline();
        if (baselineConfig == null || baselineConfig.getServiceName() == null
                || baselineConfig.getCompareDate() == null || baselineConfig.getCompareRunId() == null) {
            throw new IllegalArgumentException(
                    "Baseline configuration with serviceName, compareDate, and compareRunId is required for COMPARE mode");
        }
        String serviceName = baselineConfig.getServiceName();
        String date = baselineConfig.getCompareDate();
        String runId = baselineConfig.getCompareRunId();
        logger.info("Comparing with baseline: {}/{}/{}", serviceName, date, runId);
        BaselineStorageService.BaselineRun baseline = storageService.loadBaseline(serviceName, date, runId);
        List<BaselineStorageService.BaselineIteration> baselineIterations = baseline.getIterations();
        logger.info("Loaded baseline with {} iterations", baselineIterations.size());
        Map<String, ApiConfig> apis = "SOAP".equalsIgnoreCase(config.getTestType())
                ? config.getSoapApis()
                : config.getRestApis();
        if (apis == null || apis.get("api1") == null) {
            throw new IllegalArgumentException("api1 configuration is required for baseline comparison");
        }
        ApiConfig apiConfig = apis.get("api1");
        List<ComparisonResult> results = new ArrayList<>();
        for (BaselineStorageService.BaselineIteration baselineIter : baselineIterations) {
            int iterNum = baselineIter.getIterationNumber();
            Map<String, Object> tokens = convertTokensToMap(baselineIter.getRequestMetadata().getTokensUsed());
            
            // Smart Iteration Check (Compare Mode)
            // Even replay should respect usage? Maybe not, but let's check config to be safe if user wants filtering.
            // Actually, for Compare mode, we usually respect the Baseline's existence. 
            // But if the user provided specific tokens in the UI that override/filter, we might need logic.
            // For now, let's leave Compare mode valid as it replays exact history.
            
            logger.info("Comparing iteration {}: {}", iterNum, tokens);
            try {
                // HISTORICAL INTEGRITY FIX: Use exact request data from baseline for replay
                Map<String, String> historicalHeaders = baselineIter.getRequestHeaders();
                String historicalPayload = baselineIter.getRequestPayload();
                
                // SECURITY PERSISTENCE: 
                // 1. Resolve any relative paths in the current apiConfig (likely populated from UI History)
                if (apiConfig.getAuthentication() != null) {
                    resolveRelativeCertPaths(apiConfig.getAuthentication(), serviceName, date, runId);
                }

                // 2. STRICT FIDELITY: Always use baseline's saved security context if available
                ApiConfig effectiveApiConfig = apiConfig;
                Map<String, Object> savedConfig = baseline.getMetadata().getConfigUsed();
                if (savedConfig != null && savedConfig.containsKey("authentication")) {
                    Authentication baselineAuth = objectMapper.convertValue(savedConfig.get("authentication"), Authentication.class);
                    resolveRelativeCertPaths(baselineAuth, serviceName, date, runId);

                    logger.info("Using strict security context from saved baseline metadata for replay");
                    ApiConfig cloned = cloneApiConfig(apiConfig);
                    cloned.setAuthentication(baselineAuth);
                    effectiveApiConfig = cloned;
                }
                
                ComparisonResult result = executeApiCall(
                        effectiveApiConfig, tokens, config.getTestType(), iterNum, baselineIterations.size(), iterNum == 1, 
                        historicalPayload, historicalHeaders);
                result.setBaselineServiceName(serviceName);
                result.setBaselineDate(date);
                result.setBaselineRunId(runId);
                String protocol = storageService.detectProtocol(serviceName, date, runId);
                String baselinePath = storageService.getRunDirectory(protocol, serviceName, date, runId).toString();
                result.setBaselinePath(baselinePath);
                result.setBaselineDescription(baseline.getMetadata().getDescription());
                result.setBaselineTags(baseline.getMetadata().getTags());
                result.setBaselineCaptureTimestamp(baseline.getMetadata().getCaptureTimestamp());
                compareWithBaselineIteration(result, baselineIter, config.getTestType(), config.getIgnoredFields(), config.isIgnoreHeaders());
                results.add(result);

            } catch (Exception e) {
                logger.error("Error comparing iteration {}: {}", iterNum, e.getMessage(), e);
                ComparisonResult errorResult = new ComparisonResult();
                errorResult.setOperationName(apiConfig.getOperations().get(0).getName());
                errorResult.setIterationTokens(tokens); 
                errorResult.setStatus(ComparisonResult.Status.ERROR);
                errorResult.setErrorMessage("Comparison failed: " + e.getMessage());
                results.add(errorResult);
            }
        }
        logger.info("Baseline comparison completed: {} iterations", results.size());
        return results;
    }
    private ComparisonResult executeApiCall(ApiConfig apiConfig, Map<String, Object> tokens,
            String testType, int iterationNumber, int totalIterations, boolean isOriginal,
            String forcedPayload, Map<String, String> forcedHeaders) throws Exception {
        Operation operation = apiConfig.getOperations().get(0);
        String method = operation.getMethods().get(0);
        ComparisonResult result = new ComparisonResult();
        String opName = operation.getName();
        if (isOriginal) {
            opName += " (Original Input Payload)";
        }
        result.setOperationName(opName);
        result.setIterationTokens(new HashMap<>(tokens));
        result.setTimestamp(ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        ApiClient client = new ApiClient(apiConfig.getAuthentication());
        ApiCallResult apiCallResult = new ApiCallResult();
        result.setApi1(apiCallResult);
        String path = operation.getPath() != null ? operation.getPath() : "";
        String rawUrl = constructUrl(apiConfig.getBaseUrl(), path, operation.getQueryParams(), testType);
        String url = interpolate(rawUrl, tokens);
        
        String payload = forcedPayload;
        Map<String, String> headers = forcedHeaders;

        if (payload == null && operation.getPayloadTemplatePath() != null && !operation.getPayloadTemplatePath().isEmpty()) {
            PayloadProcessor processor = new PayloadProcessor(operation.getPayloadTemplatePath(), testType);
            payload = processor.process(tokens);
            logger.info("Processed payload (template): {}", payload);
        } else {
            logger.info("Using forced/existing payload: {}", payload);
        }
        
        if (headers == null) {
            headers = new HashMap<>(operation.getHeaders() != null ? operation.getHeaders() : new HashMap<>());
        } else {
            // Ensure we don't modify the forcedHeaders map directly if it's shared
            headers = new HashMap<>(headers);
        }
        
        apiCallResult.setUrl(url);
        apiCallResult.setMethod(method);
        apiCallResult.setRequestPayload(payload);
        
        // Enrich Metadata BEFORE the call
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        if (apiConfig.getAuthentication() != null) {
            Authentication auth = apiConfig.getAuthentication();
            Map<String, String> authInfo = new java.util.LinkedHashMap<>();
            if (auth.isEnableAuth() && auth.getClientId() != null) {
                authInfo.put("type", "basic");
                authInfo.put("clientId", auth.getClientId());
            }
            if (auth.isUseMTLS()) {
                if (auth.getPfxPath() != null && !auth.getPfxPath().trim().isEmpty()) authInfo.put("pfxPath", auth.getPfxPath());
                if (auth.getClientCertPath() != null && !auth.getClientCertPath().trim().isEmpty()) authInfo.put("clientCertPath", auth.getClientCertPath());
                if (auth.getClientKeyPath() != null && !auth.getClientKeyPath().trim().isEmpty()) authInfo.put("clientKeyPath", auth.getClientKeyPath());
                if (auth.getCaCertPath() != null && !auth.getCaCertPath().trim().isEmpty()) authInfo.put("caCertPath", auth.getCaCertPath());
                if (auth.getPassphrase() != null && !auth.getPassphrase().isEmpty()) authInfo.put("passphrase", "********");
            }
            if (!authInfo.isEmpty()) metadata.put("authentication", authInfo);
        }
        if (operation.getQueryParams() != null && !operation.getQueryParams().isEmpty()) {
            metadata.put("queryParams", operation.getQueryParams());
        }
        if (payload != null) {
            metadata.put("requestSize", payload.getBytes().length);
        }

        // --- Add Contextual Metadata ---
        metadata.put("operation", operation.getName());
        metadata.put("method", method);
        metadata.put("testType", testType);
        metadata.put("iterationNumber", iterationNumber);
        metadata.put("totalIterations", totalIterations);

        apiCallResult.setMetadata(metadata);

        long start = System.currentTimeMillis();
        com.raks.apiforge.http.HttpResponse httpResponse = client.sendRequest(url, method, headers, payload);
        apiCallResult.setDuration(System.currentTimeMillis() - start);
        
        apiCallResult.setRequestHeaders(httpResponse.getRequestHeaders());
        apiCallResult.setStatusCode(httpResponse.getStatusCode());
        apiCallResult.setResponsePayload(httpResponse.getBody());
        apiCallResult.setResponseHeaders(httpResponse.getHeaders());
        
        if (httpResponse.getBody() != null) {
            metadata.put("responseSize", httpResponse.getBody().getBytes().length);
        }
        metadata.put("statusCode", String.valueOf(httpResponse.getStatusCode()));
        metadata.put("duration", apiCallResult.getDuration());

        apiCallResult.setMetadata(metadata);
        
        // --- Status Determination Fix ---
        if (httpResponse.getStatusCode() >= 400) {
            result.setStatus(ComparisonResult.Status.ERROR);
            result.setErrorMessage("HTTP Error " + httpResponse.getStatusCode());
        } else {
            result.setStatus(ComparisonResult.Status.MATCH);
        }
        
        return result;
    }
    private BaselineStorageService.BaselineIteration convertToBaselineIteration(
            ComparisonResult result, int iterationNumber, Map<String, Object> tokens,
            ApiConfig apiConfig, String testType) {
        ApiCallResult apiCall = result.getApi1();
        Operation operation = apiConfig.getOperations().get(0);
        Map<String, String> tokenStrings = new HashMap<>();
        tokens.forEach((k, v) -> tokenStrings.put(k, String.valueOf(v)));
        Map<String, String> authMap = new HashMap<>();
        Authentication auth = apiConfig.getAuthentication();
        if (auth != null) {
            if (auth.isEnableAuth() && auth.getClientId() != null && !auth.getClientId().trim().isEmpty()) {
                authMap.put("type", "basic");
                authMap.put("clientId", auth.getClientId());
            }
            if (auth.isUseMTLS()) {
                if (auth.getPfxPath() != null && !auth.getPfxPath().trim().isEmpty()) authMap.put("pfxPath", auth.getPfxPath());
                if (auth.getClientCertPath() != null && !auth.getClientCertPath().trim().isEmpty()) authMap.put("clientCertPath", auth.getClientCertPath());
                if (auth.getClientKeyPath() != null && !auth.getClientKeyPath().trim().isEmpty()) authMap.put("clientKeyPath", auth.getClientKeyPath());
                if (auth.getCaCertPath() != null && !auth.getCaCertPath().trim().isEmpty()) authMap.put("caCertPath", auth.getCaCertPath());
            }
        }
        IterationMetadata requestMetadata = new IterationMetadata(
                iterationNumber,
                result.getTimestamp(),
                tokenStrings,
                apiCall.getUrl(),
                apiCall.getMethod(),
                operation.getName(),
                authMap);
        Map<String, Object> responseMetadata = new HashMap<>();
        responseMetadata.put("statusCode", apiCall.getStatusCode());
        responseMetadata.put("duration", apiCall.getDuration());
        responseMetadata.put("timestamp", result.getTimestamp());

        responseMetadata.put("contentType", apiCall.getResponseHeaders().getOrDefault("Content-Type", "text/xml;charset=UTF-8"));
        return new BaselineStorageService.BaselineIteration(
                iterationNumber,
                apiCall.getRequestPayload(),
                apiCall.getRequestHeaders(),
                requestMetadata,
                apiCall.getResponsePayload(),
                apiCall.getResponseHeaders(),
                responseMetadata);
    }
    private void compareWithBaselineIteration(ComparisonResult result,
            BaselineStorageService.BaselineIteration baseline,
            String testType, List<String> ignoredFields, boolean ignoreHeaders) {
        ApiCallResult baselineApi = new ApiCallResult();
        baselineApi.setUrl(baseline.getRequestMetadata().getEndpoint());
        baselineApi.setStatusCode((Integer) baseline.getResponseMetadata().get("statusCode"));
        baselineApi.setMethod(baseline.getRequestMetadata().getMethod());
        baselineApi.setRequestPayload(baseline.getRequestPayload());
        baselineApi.setRequestHeaders(baseline.getRequestHeaders());
        baselineApi.setResponsePayload(baseline.getResponsePayload());
        baselineApi.setResponseHeaders(baseline.getResponseHeaders());
        Object durationObj = baseline.getResponseMetadata().get("duration");
        if (durationObj instanceof Number) {
            baselineApi.setDuration(((Number) durationObj).longValue());
        }
        
        // --- Populate Metadata for API 2 (Baseline) ---
        Map<String, Object> meta2 = new java.util.LinkedHashMap<>();
        if (baseline.getRequestMetadata() != null) {
            meta2.put("operation", baseline.getRequestMetadata().getSoapAction());
            meta2.put("method", baseline.getRequestMetadata().getMethod());
            meta2.put("iterationNumber", baseline.getIterationNumber());
            meta2.put("totalIterations", result.getApi1().getMetadata().get("totalIterations")); // Pull from result for symmetry
            
            if (baseline.getRequestMetadata().getAuthentication() != null) {
                meta2.put("authentication", baseline.getRequestMetadata().getAuthentication());
            }
        }
        if (baseline.getResponseMetadata() != null) {
            meta2.put("statusCode", String.valueOf(baseline.getResponseMetadata().get("statusCode")));
            meta2.put("duration", baseline.getResponseMetadata().get("duration"));
            if (baseline.getResponsePayload() != null) {
                meta2.put("responseSize", baseline.getResponsePayload().getBytes().length);
            }
        }
        baselineApi.setMetadata(meta2);

        result.setBaselineCaptureTimestamp(baseline.getRequestMetadata().getTimestamp());
        result.setApi2(baselineApi);

        ComparisonEngine.compare(result, testType, ignoredFields, ignoreHeaders);
    }
    private RunMetadata createRunMetadata(String runId, String serviceName, String date,
            ApiConfig apiConfig, Config config, int totalIterations) {
        Map<String, Object> configUsed = new HashMap<>();
        configUsed.put("maxIterations", config.getMaxIterations());
        configUsed.put("iterationController", config.getIterationController());
        configUsed.put("testType", config.getTestType());
        if (apiConfig.getAuthentication() != null) {
            configUsed.put("authentication", apiConfig.getAuthentication());
        }
        return new RunMetadata(
                runId,
                serviceName,
                date,
                ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                config.getTestType(),
                apiConfig.getBaseUrl(),
                apiConfig.getOperations().get(0).getName(),
                totalIterations,
                config.getBaseline().getDescription(),
                config.getBaseline().getTags(),
                configUsed);
    }
    private Map<String, Object> convertTokensToMap(Map<String, String> stringTokens) {
        Map<String, Object> result = new HashMap<>();
        if (stringTokens != null) {
            result.putAll(stringTokens);
        }
        return result;
    }

    private void resolveRelativeCertPaths(Authentication auth, String serviceName, String date, String runId) {
        if (auth == null) return;
        String protocol = storageService.detectProtocol(serviceName, date, runId);
        auth.setPfxPath(storageService.resolveCertPath(protocol, serviceName, date, runId, auth.getPfxPath()));
        auth.setClientCertPath(storageService.resolveCertPath(protocol, serviceName, date, runId, auth.getClientCertPath()));
        auth.setClientKeyPath(storageService.resolveCertPath(protocol, serviceName, date, runId, auth.getClientKeyPath()));
        auth.setCaCertPath(storageService.resolveCertPath(protocol, serviceName, date, runId, auth.getCaCertPath()));
    }

    private ApiConfig cloneApiConfig(ApiConfig original) {
        ApiConfig cloned = new ApiConfig();
        cloned.setBaseUrl(original.getBaseUrl());
        cloned.setOperations(original.getOperations());
        cloned.setAuthentication(original.getAuthentication());
        return cloned;
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

    public List<ComparisonResult> getBaselineAsResults(String serviceName, String date, String runId) throws Exception {
        logger.info("Fetching baseline as results for: {}/{}/{}", serviceName, date, runId);
        BaselineStorageService.BaselineRun baseline = storageService.loadBaseline(serviceName, date, runId);
        List<BaselineStorageService.BaselineIteration> baselineIterations = baseline.getIterations();
        
        List<ComparisonResult> results = new ArrayList<>();
        RunMetadata metadata = baseline.getMetadata();
        String description = metadata.getDescription();
        List<String> tags = metadata.getTags();
        String captureTime = metadata.getCaptureTimestamp();

        String protocol = storageService.detectProtocol(serviceName, date, runId);
        String baselinePath = storageService.getRunDirectory(protocol, serviceName, date, runId).toString();

        for (BaselineStorageService.BaselineIteration iter : baselineIterations) {
            ComparisonResult result = new ComparisonResult();
            

            result.setOperationName(metadata.getOperation() + " (Baseline)");
            result.setIterationTokens(convertTokensToMap(iter.getRequestMetadata().getTokensUsed()));
            result.setTimestamp(iter.getRequestMetadata().getTimestamp());
            

            result.setStatus(ComparisonResult.Status.MATCH);
            

            result.setBaselineServiceName(serviceName);
            result.setBaselineDate(date);
            result.setBaselineRunId(runId);
            result.setBaselinePath(baselinePath);
            result.setBaselineDescription(description);
            result.setBaselineTags(tags);
            result.setBaselineCaptureTimestamp(captureTime);


            ApiCallResult apiCall = new ApiCallResult();
            apiCall.setUrl(iter.getRequestMetadata().getEndpoint());
            apiCall.setMethod(iter.getRequestMetadata().getMethod());
            apiCall.setRequestHeaders(iter.getRequestHeaders());
            apiCall.setRequestPayload(iter.getRequestPayload());
            
            apiCall.setResponsePayload(iter.getResponsePayload());
            apiCall.setResponseHeaders(iter.getResponseHeaders());
            
            // --- Metadata Population for History View ---
            Map<String, Object> callMetadata = new java.util.LinkedHashMap<>();
            IterationMetadata iterMeta = iter.getRequestMetadata();
            if (iterMeta.getAuthentication() != null && !iterMeta.getAuthentication().isEmpty()) {
                Map<String, String> authInfo = new java.util.LinkedHashMap<>(iterMeta.getAuthentication());
                // Mask sensitive details if they were saved (though usually they are paths or masks)
                if (authInfo.containsKey("passphrase")) authInfo.put("passphrase", "********");
                callMetadata.put("authentication", authInfo);
            }
            if (iterMeta.getTokensUsed() != null && !iterMeta.getTokensUsed().isEmpty()) {
                callMetadata.put("iterationTokens", iterMeta.getTokensUsed());
            }
            // Add other stats
            callMetadata.put("requestSize", iter.getRequestPayload() != null ? iter.getRequestPayload().getBytes().length : 0);
            callMetadata.put("responseSize", iter.getResponsePayload() != null ? iter.getResponsePayload().getBytes().length : 0);
            
            // --- Add Contextual Metadata ---
            callMetadata.put("operation", metadata.getOperation());
            callMetadata.put("method", iter.getRequestMetadata().getMethod());
            callMetadata.put("testType", metadata.getTestType());
            callMetadata.put("iterationNumber", iter.getIterationNumber());
            callMetadata.put("totalIterations", metadata.getTotalIterations());

            apiCall.setMetadata(callMetadata);

            Object statusCodeObj = iter.getResponseMetadata().get("statusCode");
            if (statusCodeObj instanceof Integer) apiCall.setStatusCode((Integer) statusCodeObj);
            
            Object durationObj = iter.getResponseMetadata().get("duration");
            if (durationObj instanceof Number) apiCall.setDuration(((Number) durationObj).longValue());

            result.setApi1(apiCall);
            

            result.setApi2(null);

            results.add(result);
        }
        
        return results;
    }
}
