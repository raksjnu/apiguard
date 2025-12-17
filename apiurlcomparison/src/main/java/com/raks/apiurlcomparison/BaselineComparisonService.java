package com.raks.apiurlcomparison;

import com.raks.apiurlcomparison.http.ApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service for baseline testing operations (CAPTURE and COMPARE)
 */
public class BaselineComparisonService {

    private static final Logger logger = LoggerFactory.getLogger(BaselineComparisonService.class);
    private final BaselineStorageService storageService;

    public BaselineComparisonService(BaselineStorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * Capture baseline: Execute API calls and save responses
     */
    public List<ComparisonResult> captureBaseline(Config config) throws Exception {
        Config.BaselineConfig baselineConfig = config.getBaseline();

        if (baselineConfig == null || baselineConfig.getServiceName() == null) {
            throw new IllegalArgumentException("Baseline configuration with serviceName is required for CAPTURE mode");
        }

        String serviceName = baselineConfig.getServiceName();
        String date = BaselineStorageService.getTodayDate();
        String runId = storageService.generateRunId(serviceName, date);

        logger.info("Capturing baseline for service: {}, date: {}, run: {}", serviceName, date, runId);

        // Generate iterations
        List<Map<String, Object>> iterations = TestDataGenerator.generate(
                config.getTokens(),
                config.getMaxIterations(),
                config.getIterationController());

        if (config.getTokens() != null && !config.getTokens().isEmpty()) {
            iterations.add(0, new HashMap<>());
        }

        // Get API config
        Map<String, ApiConfig> apis = "SOAP".equalsIgnoreCase(config.getTestType())
                ? config.getSoapApis()
                : config.getRestApis();

        if (apis == null || apis.get("api1") == null) {
            throw new IllegalArgumentException("api1 configuration is required for baseline capture");
        }

        ApiConfig apiConfig = apis.get("api1");

        // Execute iterations
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

                // Set status to CAPTURED with run ID for capture mode
                result.setStatus(ComparisonResult.Status.MATCH); // Keep internal status as MATCH
                result.setBaselineServiceName(serviceName);
                result.setBaselineDate(date);
                result.setBaselineRunId(runId);

                // Set formatted baseline path for display
                String baselinePath = baselineConfig.getStorageDir() + "\\" + serviceName + "\\" + date + "\\" + runId;
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

        // Create and save run metadata
        RunMetadata runMetadata = createRunMetadata(
                runId, serviceName, date, apiConfig, config, baselineIterations.size());

        storageService.saveBaseline(runMetadata, baselineIterations);

        logger.info("Baseline captured successfully: {}/{}/{} with {} iterations",
                serviceName, date, runId, baselineIterations.size());

        return results;
    }

    /**
     * Compare with baseline: Execute API calls and compare against saved baseline
     */
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

        // Load baseline
        BaselineStorageService.BaselineRun baseline = storageService.loadBaseline(serviceName, date, runId);
        List<BaselineStorageService.BaselineIteration> baselineIterations = baseline.getIterations();

        logger.info("Loaded baseline with {} iterations", baselineIterations.size());

        // Get API config
        Map<String, ApiConfig> apis = "SOAP".equalsIgnoreCase(config.getTestType())
                ? config.getSoapApis()
                : config.getRestApis();

        if (apis == null || apis.get("api1") == null) {
            throw new IllegalArgumentException("api1 configuration is required for baseline comparison");
        }

        ApiConfig apiConfig = apis.get("api1");

        // Execute current API calls and compare
        List<ComparisonResult> results = new ArrayList<>();

        for (BaselineStorageService.BaselineIteration baselineIter : baselineIterations) {
            int iterNum = baselineIter.getIterationNumber();
            Map<String, Object> tokens = convertTokensToMap(baselineIter.getRequestMetadata().getTokensUsed());

            logger.info("Comparing iteration {}: {}", iterNum, tokens);

            try {
                ComparisonResult result = executeApiCall(
                        apiConfig, tokens, config.getTestType(), iterNum, iterNum == 1);

                // Add baseline metadata to result
                result.setBaselineServiceName(serviceName);
                result.setBaselineDate(date);
                result.setBaselineRunId(runId);

                // Set formatted baseline path for display
                String baselinePath = baselineConfig.getStorageDir() + "\\" + serviceName + "\\" + date + "\\" + runId;
                result.setBaselinePath(baselinePath);

                result.setBaselineDescription(baseline.getMetadata().getDescription());
                result.setBaselineTags(baseline.getMetadata().getTags());
                result.setBaselineCaptureTimestamp(baseline.getMetadata().getCaptureTimestamp());

                // Compare with baseline
                compareWithBaselineIteration(result, baselineIter, config.getTestType());

                results.add(result);

            } catch (Exception e) {
                logger.error("Error comparing iteration {}: {}", iterNum, e.getMessage(), e);
                ComparisonResult errorResult = new ComparisonResult();
                errorResult.setOperationName(apiConfig.getOperations().get(0).getName());
                errorResult.setIterationTokens(tokens); // Fix NullPointerException in HTML report
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

        apiCallResult.setUrl(url);
        apiCallResult.setMethod(method);
        apiCallResult.setRequestHeaders(operation.getHeaders());
        apiCallResult.setRequestPayload(payload);

        long start = System.currentTimeMillis();
        String response = client.sendRequest(url, method, operation.getHeaders(), payload);
        apiCallResult.setDuration(System.currentTimeMillis() - start);
        apiCallResult.setResponsePayload(response);
        apiCallResult.setStatusCode(200);

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
        responseMetadata.put("contentType", "text/xml;charset=UTF-8");

        return new BaselineStorageService.BaselineIteration(
                iterationNumber,
                apiCall.getRequestPayload(),
                apiCall.getRequestHeaders(),
                requestMetadata,
                apiCall.getResponsePayload(),
                new HashMap<>(),
                responseMetadata);
    }

    private void compareWithBaselineIteration(ComparisonResult result,
            BaselineStorageService.BaselineIteration baseline,
            String testType) {

        ApiCallResult baselineApi = new ApiCallResult();
        baselineApi.setUrl(baseline.getRequestMetadata().getEndpoint());
        baselineApi.setMethod(baseline.getRequestMetadata().getMethod());
        baselineApi.setRequestPayload(baseline.getRequestPayload());
        baselineApi.setResponsePayload(baseline.getResponsePayload());

        // Handle duration - could be Integer or Long from JSON deserialization
        Object durationObj = baseline.getResponseMetadata().get("duration");
        if (durationObj instanceof Number) {
            baselineApi.setDuration(((Number) durationObj).longValue());
        }

        baselineApi.setStatusCode((Integer) baseline.getResponseMetadata().get("statusCode"));

        result.setApi2(baselineApi);

        ComparisonEngine.compare(result, testType);
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
}
