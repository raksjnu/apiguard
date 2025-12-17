package com.raks.apiurlcomparison;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing baseline storage with date/run folder structure:
 * baselines/{serviceName}/{YYYYMMDD}/{run-XXX}/iteration-XXX/
 */
public class BaselineStorageService {

    private static final Logger logger = LoggerFactory.getLogger(BaselineStorageService.class);
    private static final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final String baseStorageDir;

    public BaselineStorageService(String baseStorageDir) {
        this.baseStorageDir = baseStorageDir;
    }

    /**
     * Save a complete baseline run with all iterations
     */
    public void saveBaseline(RunMetadata runMetadata, List<BaselineIteration> iterations) throws IOException {
        String serviceName = runMetadata.getServiceName();
        String date = runMetadata.getCaptureDate();
        String runId = runMetadata.getRunId();

        Path runDir = getRunDirectory(serviceName, date, runId);
        Files.createDirectories(runDir);

        // Save run metadata
        mapper.writeValue(runDir.resolve("metadata.json").toFile(), runMetadata);
        logger.info("Saved run metadata to: {}", runDir.resolve("metadata.json"));

        // Save each iteration
        for (BaselineIteration iteration : iterations) {
            saveIteration(runDir, iteration);
        }

        // Save summary
        saveSummary(runDir, iterations);

        logger.info("Baseline saved: {}/{}/{} with {} iterations", serviceName, date, runId, iterations.size());
    }

    private void saveIteration(Path runDir, BaselineIteration iteration) throws IOException {
        int iterNum = iteration.getIterationNumber();
        Path iterDir = runDir.resolve(String.format("iteration-%03d", iterNum));
        Files.createDirectories(iterDir);

        Files.writeString(iterDir.resolve("request.xml"), iteration.getRequestPayload());
        mapper.writeValue(iterDir.resolve("request-headers.json").toFile(), iteration.getRequestHeaders());
        mapper.writeValue(iterDir.resolve("request-metadata.json").toFile(), iteration.getRequestMetadata());

        Files.writeString(iterDir.resolve("response.xml"), iteration.getResponsePayload());
        mapper.writeValue(iterDir.resolve("response-headers.json").toFile(), iteration.getResponseHeaders());
        mapper.writeValue(iterDir.resolve("response-metadata.json").toFile(), iteration.getResponseMetadata());
    }

    private void saveSummary(Path runDir, List<BaselineIteration> iterations) throws IOException {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalIterations", iterations.size());

        List<Map<String, Object>> iterSummaries = new ArrayList<>();
        for (BaselineIteration iter : iterations) {
            Map<String, Object> iterSum = new HashMap<>();
            iterSum.put("iterationNumber", iter.getIterationNumber());
            iterSum.put("tokens", iter.getRequestMetadata().getTokensUsed());
            iterSum.put("statusCode", iter.getResponseMetadata().get("statusCode"));
            iterSum.put("duration", iter.getResponseMetadata().get("duration"));
            iterSummaries.add(iterSum);
        }
        summary.put("iterations", iterSummaries);

        mapper.writeValue(runDir.resolve("summary.json").toFile(), summary);
    }

    /**
     * Load a baseline run
     */
    public BaselineRun loadBaseline(String serviceName, String date, String runId) throws IOException {
        Path runDir = getRunDirectory(serviceName, date, runId);

        if (!Files.exists(runDir)) {
            throw new IOException("Baseline not found: " + runDir);
        }

        RunMetadata runMetadata = mapper.readValue(runDir.resolve("metadata.json").toFile(), RunMetadata.class);

        List<BaselineIteration> iterations = new ArrayList<>();
        File[] iterDirs = runDir.toFile().listFiles((dir, name) -> name.startsWith("iteration-"));

        if (iterDirs != null) {
            Arrays.sort(iterDirs);
            for (File iterDir : iterDirs) {
                iterations.add(loadIteration(iterDir.toPath()));
            }
        }

        logger.info("Loaded baseline: {}/{}/{} with {} iterations", serviceName, date, runId, iterations.size());
        return new BaselineRun(runMetadata, iterations);
    }

    @SuppressWarnings("unchecked")
    private BaselineIteration loadIteration(Path iterDir) throws IOException {
        String requestPayload = Files.readString(iterDir.resolve("request.xml"));
        Map<String, String> requestHeaders = mapper.readValue(iterDir.resolve("request-headers.json").toFile(),
                Map.class);
        IterationMetadata requestMetadata = mapper.readValue(iterDir.resolve("request-metadata.json").toFile(),
                IterationMetadata.class);

        String responsePayload = Files.readString(iterDir.resolve("response.xml"));
        Map<String, String> responseHeaders = mapper.readValue(iterDir.resolve("response-headers.json").toFile(),
                Map.class);
        Map<String, Object> responseMetadata = mapper.readValue(iterDir.resolve("response-metadata.json").toFile(),
                Map.class);

        return new BaselineIteration(
                requestMetadata.getIterationNumber(),
                requestPayload,
                requestHeaders,
                requestMetadata,
                responsePayload,
                responseHeaders,
                responseMetadata);
    }

    /**
     * Generate next run ID for a given service and date
     */
    public String generateRunId(String serviceName, String date) {
        Path dateDir = getDateDirectory(serviceName, date);

        if (!Files.exists(dateDir)) {
            return "run-001";
        }

        File[] runDirs = dateDir.toFile().listFiles((dir, name) -> name.startsWith("run-"));

        if (runDirs == null || runDirs.length == 0) {
            return "run-001";
        }

        int maxRunNum = 0;
        for (File runDir : runDirs) {
            String name = runDir.getName();
            try {
                int runNum = Integer.parseInt(name.substring(4));
                maxRunNum = Math.max(maxRunNum, runNum);
            } catch (NumberFormatException e) {
                logger.warn("Invalid run directory name: {}", name);
            }
        }

        return String.format("run-%03d", maxRunNum + 1);
    }

    /**
     * List all services
     */
    public List<String> listServices() {
        File baseDir = new File(baseStorageDir);
        if (!baseDir.exists()) {
            return Collections.emptyList();
        }

        File[] serviceDirs = baseDir.listFiles(File::isDirectory);
        if (serviceDirs == null) {
            return Collections.emptyList();
        }

        return Arrays.stream(serviceDirs)
                .map(File::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * List all dates for a service
     */
    public List<String> listDates(String serviceName) {
        Path serviceDir = Paths.get(baseStorageDir, serviceName);
        if (!Files.exists(serviceDir)) {
            return Collections.emptyList();
        }

        File[] dateDirs = serviceDir.toFile().listFiles(File::isDirectory);
        if (dateDirs == null) {
            return Collections.emptyList();
        }

        return Arrays.stream(dateDirs)
                .map(File::getName)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    /**
     * List all runs for a service and date
     */
    public List<RunInfo> listRuns(String serviceName, String date) throws IOException {
        Path dateDir = getDateDirectory(serviceName, date);
        if (!Files.exists(dateDir)) {
            return Collections.emptyList();
        }

        File[] runDirs = dateDir.toFile().listFiles((dir, name) -> name.startsWith("run-"));
        if (runDirs == null) {
            return Collections.emptyList();
        }

        List<RunInfo> runs = new ArrayList<>();
        for (File runDir : runDirs) {
            File metadataFile = new File(runDir, "metadata.json");
            if (metadataFile.exists()) {
                RunMetadata metadata = mapper.readValue(metadataFile, RunMetadata.class);
                runs.add(new RunInfo(metadata.getRunId(), metadata.getDescription(),
                        metadata.getTags(), metadata.getTotalIterations(),
                        metadata.getCaptureTimestamp()));
            }
        }

        runs.sort(Comparator.comparing(RunInfo::getRunId));
        return runs;
    }

    public static String getTodayDate() {
        return LocalDate.now().format(DATE_FORMATTER);
    }

    private Path getRunDirectory(String serviceName, String date, String runId) {
        return Paths.get(baseStorageDir, serviceName, date, runId);
    }

    private Path getDateDirectory(String serviceName, String date) {
        return Paths.get(baseStorageDir, serviceName, date);
    }

    // Inner classes
    public static class BaselineRun {
        private final RunMetadata metadata;
        private final List<BaselineIteration> iterations;

        public BaselineRun(RunMetadata metadata, List<BaselineIteration> iterations) {
            this.metadata = metadata;
            this.iterations = iterations;
        }

        public RunMetadata getMetadata() {
            return metadata;
        }

        public List<BaselineIteration> getIterations() {
            return iterations;
        }
    }

    public static class BaselineIteration {
        private final int iterationNumber;
        private final String requestPayload;
        private final Map<String, String> requestHeaders;
        private final IterationMetadata requestMetadata;
        private final String responsePayload;
        private final Map<String, String> responseHeaders;
        private final Map<String, Object> responseMetadata;

        public BaselineIteration(int iterationNumber, String requestPayload, Map<String, String> requestHeaders,
                IterationMetadata requestMetadata, String responsePayload,
                Map<String, String> responseHeaders, Map<String, Object> responseMetadata) {
            this.iterationNumber = iterationNumber;
            this.requestPayload = requestPayload;
            this.requestHeaders = requestHeaders;
            this.requestMetadata = requestMetadata;
            this.responsePayload = responsePayload;
            this.responseHeaders = responseHeaders;
            this.responseMetadata = responseMetadata;
        }

        public int getIterationNumber() {
            return iterationNumber;
        }

        public String getRequestPayload() {
            return requestPayload;
        }

        public Map<String, String> getRequestHeaders() {
            return requestHeaders;
        }

        public IterationMetadata getRequestMetadata() {
            return requestMetadata;
        }

        public String getResponsePayload() {
            return responsePayload;
        }

        public Map<String, String> getResponseHeaders() {
            return responseHeaders;
        }

        public Map<String, Object> getResponseMetadata() {
            return responseMetadata;
        }
    }

    public static class RunInfo {
        private final String runId;
        private final String description;
        private final List<String> tags;
        private final int totalIterations;
        private final String timestamp;

        public RunInfo(String runId, String description, List<String> tags, int totalIterations, String timestamp) {
            this.runId = runId;
            this.description = description;
            this.tags = tags;
            this.totalIterations = totalIterations;
            this.timestamp = timestamp;
        }

        public String getRunId() {
            return runId;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getTags() {
            return tags;
        }

        public int getTotalIterations() {
            return totalIterations;
        }

        public String getTimestamp() {
            return timestamp;
        }
    }
}
