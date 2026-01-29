package com.raks.apiforge;
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
public class BaselineStorageService {
    private static final Logger logger = LoggerFactory.getLogger(BaselineStorageService.class);
    private static final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final String baseStorageDir;
    public BaselineStorageService(String baseStorageDir) {
        this.baseStorageDir = baseStorageDir;
    }
    public void saveBaseline(RunMetadata runMetadata, List<BaselineIteration> iterations) throws IOException {
        String serviceName = runMetadata.getServiceName();
        String date = runMetadata.getCaptureDate();
        String runId = runMetadata.getRunId();
        String protocol = getProtocolFromType(runMetadata.getTestType());
        
        Path runDir = getRunDirectory(protocol, serviceName, date, runId);
        Files.createDirectories(runDir);
        mapper.writeValue(runDir.resolve("metadata.json").toFile(), runMetadata);
        logger.info("Saved run metadata to: {}", runDir.resolve("metadata.json"));
        for (BaselineIteration iteration : iterations) {
            saveIteration(runDir, iteration, runMetadata.getTestType());
        }
        saveSummary(runDir, iterations);
        logger.info("Baseline saved: {} ({})/{}/{} with {} iterations", serviceName, runMetadata.getTestType(), date, runId, iterations.size());
    }
    private void saveIteration(Path runDir, BaselineIteration iteration, String testType) throws IOException {
        int iterNum = iteration.getIterationNumber();
        Path iterDir = runDir.resolve(String.format("iteration-%03d", iterNum));
        Files.createDirectories(iterDir);
        
        String reqExt = "JMS".equalsIgnoreCase(testType) ? "txt" : "xml";
        if (iteration.getRequestPayload() != null && iteration.getRequestPayload().trim().startsWith("{")) reqExt = "json";
        
        writePayload(iterDir.resolve("request." + reqExt), iteration.getRequestPayload());
        mapper.writeValue(iterDir.resolve("request-headers.json").toFile(), iteration.getRequestHeaders());
        mapper.writeValue(iterDir.resolve("request-metadata.json").toFile(), iteration.getRequestMetadata());
        
        String resExt = reqExt;
        writePayload(iterDir.resolve("response." + resExt), iteration.getResponsePayload());
        mapper.writeValue(iterDir.resolve("response-headers.json").toFile(), iteration.getResponseHeaders());
        mapper.writeValue(iterDir.resolve("response-metadata.json").toFile(), iteration.getResponseMetadata());
    }

    private void writePayload(Path path, String payload) throws IOException {
        if (payload != null) {
            Files.writeString(path, payload);
        } else {
            Files.writeString(path, "");
        }
    }

    public String copyReferencedFile(Path runDir, String sourcePath) {
        if (sourcePath == null || sourcePath.isEmpty()) return null;
        try {
            Path src = Paths.get(sourcePath);
            if (!Files.exists(src)) {
                // Try resolving against baseStorageDir if relative
                Path resolved = Paths.get(baseStorageDir).resolve(sourcePath);
                if (Files.exists(resolved)) {
                    src = resolved;
                } else {
                    logger.warn("Source file not found for copying: {} (checked absolute and relative to {})", sourcePath, baseStorageDir);
                    return sourcePath;
                }
            }
            Path certsDir = runDir.resolve("certs");
            Files.createDirectories(certsDir);
            Path dest = certsDir.resolve(src.getFileName());
            Files.copy(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            logger.info("Copied certificate/key to baseline: {}", dest);
            
            // Return relative path for portability within the run folder
            return "certs/" + src.getFileName().toString();
        } catch (Exception e) {
            logger.error("Failed to copy referenced file: {}", sourcePath, e);
            return sourcePath;
        }
    }

    public String resolveCertPath(String protocol, String serviceName, String date, String runId, String path) {
        if (path == null || path.isEmpty()) return null;
        if (new File(path).isAbsolute()) return path;
        
        // If relative, resolve against the run directory
        Path runDir = getRunDirectory(protocol, serviceName, date, runId);
        return runDir.resolve(path).toAbsolutePath().toString();
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
    public BaselineRun loadBaseline(String serviceName, String date, String runId) throws IOException {
        String protocol = detectProtocol(serviceName, date, runId);
        Path runDir = getRunDirectory(protocol, serviceName, date, runId);
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
    private BaselineIteration loadIteration(Path iterDir) throws IOException {
        String requestPayload = findAndReadPayload(iterDir, "request.");
        Map<String, String> requestHeaders = (Map<String, String>) mapper.readValue(iterDir.resolve("request-headers.json").toFile(),
                Map.class);
        IterationMetadata requestMetadata = mapper.readValue(iterDir.resolve("request-metadata.json").toFile(),
                IterationMetadata.class);
        
        String responsePayload = findAndReadPayload(iterDir, "response.");
        Map<String, String> responseHeaders = (Map<String, String>) mapper.readValue(iterDir.resolve("response-headers.json").toFile(),
                Map.class);
        Map<String, Object> responseMetadata = (Map<String, Object>) mapper.readValue(iterDir.resolve("response-metadata.json").toFile(),
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

    private String findAndReadPayload(Path iterDir, String prefix) throws IOException {
        File[] files = iterDir.toFile().listFiles((dir, name) -> name.startsWith(prefix));
        if (files != null && files.length > 0) {
            return Files.readString(files[0].toPath());
        }
        return "";
    }
    public String generateRunId(String serviceName, String date, String testType) {
        String protocol = getProtocolFromType(testType);
        Path dateDir = getDateDirectory(protocol, serviceName, date);
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
    public List<String> listServices(String protocolFilter) {
        Set<String> services = new TreeSet<>(Comparator.reverseOrder());
        File baseDir = new File(baseStorageDir);
        if (!baseDir.exists()) return Collections.emptyList();

        List<String> protocolsToCheck = new ArrayList<>();
        if (protocolFilter != null && !protocolFilter.isEmpty() && !"ALL".equalsIgnoreCase(protocolFilter)) {
            protocolsToCheck.add(protocolFilter.toLowerCase());
        } else {
            protocolsToCheck.addAll(Arrays.asList("rest", "jms", "soap"));
            // If ALL, also check root for legacy
            File[] rootFiles = baseDir.listFiles();
            if (rootFiles != null) {
                for (File f : rootFiles) {
                    if (f.isDirectory() && !isProtocolDir(f.getName())) {
                        services.add(f.getName());
                    }
                }
            }
        }

        // Check Protocol Subdirs
        for (String p : protocolsToCheck) {
            File pDir = new File(baseDir, p);
            if (pDir.exists() && pDir.isDirectory()) {
                File[] pFiles = pDir.listFiles(f -> f.isDirectory() && !f.getName().equalsIgnoreCase("certs"));
                if (pFiles != null) {
                    for (File f : pFiles) {
                         // Fix: Return relative path "protocol/service" instead of just "service"
                         services.add(p + "/" + f.getName());
                    }
                }
            }
        }
        return new ArrayList<>(services);
    }

    private boolean isProtocolDir(String name) {
        return Arrays.asList("rest", "jms", "soap").contains(name.toLowerCase());
    }
    public List<String> listDates(String serviceName) {
        Set<String> dates = new TreeSet<>(Comparator.reverseOrder());
        // 1. Check Root
        Path rootServiceDir = Paths.get(baseStorageDir, serviceName);
        if (Files.exists(rootServiceDir)) {
             File[] dirs = rootServiceDir.toFile().listFiles(File::isDirectory);
             if (dirs != null) for (File d : dirs) dates.add(d.getName());
        }
        // 2. Check Protocols
        for (String p : Arrays.asList("rest", "jms", "soap")) {
            Path pServiceDir = Paths.get(baseStorageDir, p, serviceName);
            if (Files.exists(pServiceDir)) {
                File[] dirs = pServiceDir.toFile().listFiles(File::isDirectory);
                if (dirs != null) for (File d : dirs) dates.add(d.getName());
            }
        }
        return new ArrayList<>(dates);
    }
    public List<RunInfo> listRuns(String serviceName, String date) throws IOException {
        String protocol = detectProtocol(serviceName, date, null); // date-level detect
        Path dateDir = getDateDirectory(protocol, serviceName, date);
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

    public String getRunEndpoint(String serviceName, String date, String runId) throws IOException {
        String protocol = detectProtocol(serviceName, date, runId);
        Path runDir = getRunDirectory(protocol, serviceName, date, runId);
        

        Path iterDir = runDir.resolve("iteration-001");
        if (!Files.exists(iterDir)) {
            File[] files = runDir.toFile().listFiles((dir, name) -> name.startsWith("iteration-"));
            if (files == null || files.length == 0) {
                return null;
            }
            Arrays.sort(files);
            iterDir = files[0].toPath();
        }

        Path metadataFile = iterDir.resolve("request-metadata.json");
        if (!Files.exists(metadataFile)) {
            return null;
        }

        try {
            IterationMetadata metadata = mapper.readValue(metadataFile.toFile(), IterationMetadata.class);
            return metadata.getEndpoint();
        } catch (Exception e) {
            logger.warn("Failed to read endpoint from metadata: {}", e.getMessage());
            return null;
        }
    }

    public java.util.Map<String, String> getRunRequestHeaders(String serviceName, String date, String runId) throws IOException {
        String protocol = detectProtocol(serviceName, date, runId);
        Path runDir = getRunDirectory(protocol, serviceName, date, runId);
        

        Path iterDir = runDir.resolve("iteration-001");
        if (!Files.exists(iterDir)) {
             try (java.util.stream.Stream<Path> stream = Files.list(runDir)) {
                 iterDir = stream
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("iteration-"))
                    .sorted()
                    .findFirst()
                    .orElse(null);
             }
        }
        
        if (iterDir != null) {
             Path headersFile = iterDir.resolve("request-headers.json"); 
             if (Files.exists(headersFile)) {
                 return mapper.readValue(headersFile.toFile(), java.util.Map.class);
             }
        }
        return Collections.emptyMap();
    }

    public RunMetadata getRunMetadata(String serviceName, String date, String runId) throws IOException {
        String protocol = detectProtocol(serviceName, date, runId);
        Path runDir = getRunDirectory(protocol, serviceName, date, runId);
        if (!Files.exists(runDir)) return null;
        
        Path metadataFile = runDir.resolve("metadata.json");
        if (Files.exists(metadataFile)) {
            return mapper.readValue(metadataFile.toFile(), RunMetadata.class);
        }
        return null;
    }

    public String getRunRequestPayload(String serviceName, String date, String runId) throws IOException {
        String protocol = detectProtocol(serviceName, date, runId);
        Path runDir = getRunDirectory(protocol, serviceName, date, runId);
        

        Path iterDir = runDir.resolve("iteration-001");
        if (!Files.exists(iterDir)) {
             try (java.util.stream.Stream<Path> stream = Files.list(runDir)) {
                 iterDir = stream
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("iteration-"))
                    .sorted()
                    .findFirst()
                    .orElse(null);
             }
        }
        
        if (iterDir != null) {
             Path requestFile = iterDir.resolve("request.xml"); 
             if (Files.exists(requestFile)) {
                 return new String(Files.readAllBytes(requestFile), java.nio.charset.StandardCharsets.UTF_8);
             }
             Path requestJson = iterDir.resolve("request.json"); 
             if (Files.exists(requestJson)) {
                 return new String(Files.readAllBytes(requestJson), java.nio.charset.StandardCharsets.UTF_8);
             }
             Path requestTxt = iterDir.resolve("request.txt");
             if (Files.exists(requestTxt)) {
                 return new String(Files.readAllBytes(requestTxt), java.nio.charset.StandardCharsets.UTF_8);
             }
        }
        return null;
    }
    public static String getTodayDate() {
        return LocalDate.now().format(DATE_FORMATTER);
    }
    public Path getRunDirectory(String protocol, String serviceName, String date, String runId) {
        if (protocol == null || protocol.isEmpty()) {
            return Paths.get(baseStorageDir, serviceName, date, runId); // Fallback to root
        }
        return Paths.get(baseStorageDir, protocol, serviceName, date, runId);
    }
    private Path getDateDirectory(String protocol, String serviceName, String date) {
        if (protocol == null || protocol.isEmpty()) {
             return Paths.get(baseStorageDir, serviceName, date); // Fallback to root
        }
        return Paths.get(baseStorageDir, protocol, serviceName, date);
    }

    public String detectProtocol(String serviceName, String date, String runId) {
        // If runId is provided, we check for the specific run folder
        // If runId is null, we check for the service/date folder
        for (String p : Arrays.asList("rest", "jms", "soap")) {
            Path path = (runId != null) ? getRunDirectory(p, serviceName, date, runId) : getDateDirectory(p, serviceName, date);
            if (Files.exists(path)) return p;
        }
        return ""; // Fallback to root
    }

    public String getProtocolFromType(String testType) {
        if (testType == null) return "rest"; // Default
        String type = testType.toUpperCase();
        if (type.contains("JMS")) return "jms";
        if (type.contains("SOAP")) return "soap"; 
        return "rest";
    }
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
    public List<SearchResult> searchBaselines(String token, String storageDir, boolean exactMatch) {
        List<SearchResult> allMatches = new ArrayList<>();
        if (token == null || token.isEmpty()) return allMatches;
        
        String lowerToken = token.toLowerCase();
        java.util.regex.Pattern pattern = null;
        if (exactMatch) {
            pattern = java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(lowerToken) + "\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
        }

        File root = new File(storageDir);
        if (!root.exists()) return allMatches;

        final java.util.regex.Pattern finalPattern = pattern;
        try (java.util.stream.Stream<Path> stream = Files.walk(root.toPath())) {
            stream.filter(p -> Files.isRegularFile(p))
                .filter(p -> p.getFileName().toString().endsWith(".json") || 
                             p.getFileName().toString().startsWith("request.") || 
                             p.getFileName().toString().startsWith("response."))
                .forEach(p -> {
                    try {
                        String content = Files.readString(p);
                        boolean found = false;
                        if (exactMatch && finalPattern != null) {
                            found = finalPattern.matcher(content).find();
                        } else {
                            found = content.toLowerCase().contains(lowerToken);
                        }
                        
                        if (found) {
                            SearchResult res = buildSearchResult(p, content, token);
                            if (res != null) allMatches.add(res);
                        }
                    } catch (IOException e) {
                        logger.warn("Could not read file during search: {}", p, e);
                    }
                });
        } catch (IOException e) {
            logger.error("Error walking directory for search", e);
        }
        return allMatches;
    }

    private SearchResult buildSearchResult(Path path, String content, String token) {
        Path rel = Paths.get(baseStorageDir).relativize(path);
        String[] parts = rel.toString().split(File.separator.equals("\\") ? "\\\\" : File.separator);
        
        if (parts.length < 3) return null;

        SearchResult res = new SearchResult();
        res.setFilePath(rel.toString().replace("\\", "/"));
        
        int idx = content.toLowerCase().indexOf(token.toLowerCase());
        int start = Math.max(0, idx - 40);
        int end = Math.min(content.length(), idx + token.length() + 40);
        res.setSnippet("..." + content.substring(start, end).replace("\n", " ").replace("\r", " ") + "...");

        for (String p : parts) {
            if (p.startsWith("run-")) res.setRunId(p);
            else if (p.startsWith("iteration-")) res.setIteration(p);
            else if (p.matches("\\d{8}")) res.setDate(p);
            else if (isProtocolDir(p)) res.setProtocol(p);
            else if (res.getServiceName() == null) res.setServiceName(p);
        }
        
        return res;
    }

    public static class SearchResult {
        private String serviceName;
        private String protocol;
        private String date;
        private String runId;
        private String iteration;
        private String filePath;
        private String snippet;

        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getRunId() { return runId; }
        public void setRunId(String runId) { this.runId = runId; }
        public String getIteration() { return iteration; }
        public void setIteration(String iteration) { this.iteration = iteration; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getSnippet() { return snippet; }
        public void setSnippet(String snippet) { this.snippet = snippet; }
    }
}
