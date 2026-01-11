package com.raks.apiurlcomparison;
import com.raks.apiurlcomparison.http.ApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
public class BaselineComparisonService {
    private static final Logger logger = LoggerFactory.getLogger(BaselineComparisonService.class);
    private final BaselineStorageService storageService;
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
        String runId = storageService.generateRunId(serviceName, date);
        logger.info("Capturing baseline for service: {}, date: {}, run: {}", serviceName, date, runId);
        List<Map<String, Object>> iterations = TestDataGenerator.generate(
                config.getTokens(),
                config.getMaxIterations(),
                config.getIterationController());
        if (config.getTokens() != null && !config.getTokens().isEmpty()) {
            iterations.add(0, new HashMap<>());
        }
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
            logger.info("Capturing iteration {}: {}{}", iterationNumber, currentTokens,
                    isOriginal ? " (Original Input Payload)" : "");
            try {
                ComparisonResult result = executeApiCall(
                        apiConfig, currentTokens, config.getTestType(), iterationNumber, isOriginal);
                result.setStatus(ComparisonResult.Status.MATCH); 
                result.setBaselineServiceName(serviceName);
                result.setBaselineDate(date);
                result.setBaselineRunId(runId);
                String baselinePath = baselineConfig.getStorageDir() + java.io.File.separator + serviceName + java.io.File.separator + date + java.io.File.separator + runId;
                result.setBaselinePath(baselinePath);
                result.setBaselineDescription("Baseline captured to: " + baselinePath);
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
            logger.info("Comparing iteration {}: {}", iterNum, tokens);
            try {
                ComparisonResult result = executeApiCall(
                        apiConfig, tokens, config.getTestType(), iterNum, iterNum == 1);
                result.setBaselineServiceName(serviceName);
                result.setBaselineDate(date);
                result.setBaselineRunId(runId);
                String baselinePath = baselineConfig.getStorageDir() + java.io.File.separator + serviceName + java.io.File.separator + date + java.io.File.separator + runId;
                result.setBaselinePath(baselinePath);
                result.setBaselineDescription(baseline.getMetadata().getDescription());
                result.setBaselineTags(baseline.getMetadata().getTags());
                result.setBaselineCaptureTimestamp(baseline.getMetadata().getCaptureTimestamp());
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
            String testType, int iterationNumber, boolean isOriginal) throws Exception {
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
        String url = constructUrl(apiConfig.getBaseUrl(), path, testType);
        String payload = null;
        if (operation.getPayloadTemplatePath() != null && !operation.getPayloadTemplatePath().isEmpty()) {
            PayloadProcessor processor = new PayloadProcessor(operation.getPayloadTemplatePath(), testType);
            payload = processor.process(tokens);
        }
        long start = System.currentTimeMillis();
        com.raks.apiurlcomparison.http.HttpResponse httpResponse = client.sendRequest(url, method, operation.getHeaders(), payload);
        apiCallResult.setDuration(System.currentTimeMillis() - start);
        

        Map<String, String> actualHeaders = new HashMap<>(operation.getHeaders() != null ? operation.getHeaders() : new HashMap<>());
        if (client.getAccessToken() != null) {
            actualHeaders.put("Authorization", "Bearer " + client.getAccessToken());
        } else if (apiConfig.getAuthentication() != null 
                && apiConfig.getAuthentication().getClientId() != null 
                && apiConfig.getAuthentication().getClientSecret() != null) {
            String auth = apiConfig.getAuthentication().getClientId() + ":" + apiConfig.getAuthentication().getClientSecret();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            actualHeaders.put("Authorization", "Basic " + encodedAuth);
        }

        apiCallResult.setUrl(url);
        apiCallResult.setMethod(method);
        apiCallResult.setRequestHeaders(actualHeaders);
        apiCallResult.setRequestPayload(payload);

        apiCallResult.setStatusCode(httpResponse.getStatusCode());
        apiCallResult.setResponsePayload(httpResponse.getBody());
        apiCallResult.setResponseHeaders(httpResponse.getHeaders());
        result.setStatus(ComparisonResult.Status.MATCH);
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
        if (apiConfig.getAuthentication() != null && apiConfig.getAuthentication().getClientId() != null) {
            authMap.put("type", "basic");
            authMap.put("username", apiConfig.getAuthentication().getClientId());
        }
        IterationMetadata requestMetadata = new IterationMetadata(
                iterationNumber,
                result.getTimestamp(),
                tokenStrings,
                apiCall.getUrl(),
                apiCall.getMethod(),
                operation.getHeaders().get("SOAPAction"),
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
        baselineApi.setResponsePayload(baseline.getResponsePayload());
        baselineApi.setResponseHeaders(baseline.getResponseHeaders());
        Object durationObj = baseline.getResponseMetadata().get("duration");
        if (durationObj instanceof Number) {
            baselineApi.setDuration(((Number) durationObj).longValue());
        }
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

    public List<ComparisonResult> getBaselineAsResults(String serviceName, String date, String runId) throws Exception {
        logger.info("Fetching baseline as results for: {}/{}/{}", serviceName, date, runId);
        BaselineStorageService.BaselineRun baseline = storageService.loadBaseline(serviceName, date, runId);
        List<BaselineStorageService.BaselineIteration> baselineIterations = baseline.getIterations();
        
        List<ComparisonResult> results = new ArrayList<>();
        RunMetadata metadata = baseline.getMetadata();
        String description = metadata.getDescription();
        List<String> tags = metadata.getTags();
        String captureTime = metadata.getCaptureTimestamp();

        String baselinePath = storageService.getRunDirectory(serviceName, date, runId).toString();

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
