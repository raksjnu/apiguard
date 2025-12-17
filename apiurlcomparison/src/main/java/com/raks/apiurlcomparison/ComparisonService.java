package com.raks.apiurlcomparison;

import com.raks.apiurlcomparison.http.ApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ComparisonService {
    private static final Logger logger = LoggerFactory.getLogger(ComparisonService.class);

    public List<ComparisonResult> execute(Config config) {
        // Check if we're in baseline mode
        if ("BASELINE".equalsIgnoreCase(config.getComparisonMode())) {
            return executeBaselineMode(config);
        }

        // Original LIVE comparison mode
        List<ComparisonResult> allResults = new ArrayList<>();

        // Prepare for iterations
        logger.info("Generating iterations with strategy: {}", config.getIterationController());
        List<Map<String, Object>> iterations = TestDataGenerator.generate(
                config.getTokens(),
                config.getMaxIterations(),
                config.getIterationController());

        // Enforce "Original Input Payload" (Raw Execution) as the first iteration
        // This ensures the payload is executed "as-is" before any token replacement
        // occurs.
        if (config.getTokens() != null && !config.getTokens().isEmpty()) {
            iterations.add(0, new HashMap<>());
        }

        int iterationCount = 0;
        for (Map<String, Object> currentTokens : iterations) {
            iterationCount++;
            boolean isOriginal = (iterationCount == 1);
            logger.info("Running iteration {}: {}{}", iterationCount, currentTokens,
                    isOriginal ? " (Original Input Payload)" : "");

            try {
                if ("REST".equalsIgnoreCase(config.getTestType())) {
                    processApis(config.getRestApis(), currentTokens, allResults, config.getTestType(), isOriginal);
                } else if ("SOAP".equalsIgnoreCase(config.getTestType())) {
                    processApis(config.getSoapApis(), currentTokens, allResults, config.getTestType(), isOriginal);
                } else {
                    logger.error("Invalid testType specified in config: {}", config.getTestType());
                }
            } catch (Exception e) {
                logger.error("Error during iteration {}: {}", iterationCount, e.getMessage(), e);
            }
        }
        return allResults;
    }

    /**
     * Execute baseline mode (CAPTURE or COMPARE)
     */
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
            List<ComparisonResult> allResults, String apiType, boolean isOriginal) {
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

        // Assuming operations should be matched by name for comparison
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

                // Populate API 1 details
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
                        // Fallback to raw content if processing fails
                        payload1 = op1.getPayloadTemplatePath();
                    }
                }
                api1CallResult.setRequestPayload(payload1);

                // Populate API 2 details
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

                // Execute API 1
                long start1 = System.currentTimeMillis();
                String response1 = client1.sendRequest(url1, method1, op1.getHeaders(), payload1);
                api1CallResult.setDuration(System.currentTimeMillis() - start1);
                api1CallResult.setResponsePayload(response1);

                // Execute API 2
                long start2 = System.currentTimeMillis();
                String response2 = client2.sendRequest(url2, method2, op2.getHeaders(), payload2);
                api2CallResult.setDuration(System.currentTimeMillis() - start2);
                api2CallResult.setResponsePayload(response2);

                // Compare
                ComparisonEngine.compare(result, apiType);

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

        // Normalize base: remove trailing slash
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        // If path is missing or empty, just return base (GUI case)
        if (path == null || path.trim().isEmpty()) {
            return normalizedBase;
        }

        // Normalize path: ensure leading slash
        String normalizedPath = path.startsWith("/") ? path : "/" + path;

        // If normalizedBase already ends with the path, use it as is (avoid
        // duplication)
        if (normalizedBase.endsWith(normalizedPath)) {
            return normalizedBase;
        }

        return normalizedBase + normalizedPath;
    }
}
