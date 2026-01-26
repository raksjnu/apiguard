package com.raks.raksanalyzer.api.rest;
import com.raks.raksanalyzer.analyzer.AnalyzerFactory;
import com.raks.raksanalyzer.core.config.ConfigurationManager;
import com.raks.raksanalyzer.domain.enums.ExecutionMode;
import com.raks.raksanalyzer.domain.enums.ProjectType;
import com.raks.raksanalyzer.domain.model.AnalysisRequest;
import com.raks.raksanalyzer.domain.model.AnalysisResult;
import com.raks.raksanalyzer.generator.excel.ExcelGenerator;
import com.raks.raksanalyzer.generator.word.WordGenerator;
import com.raks.raksanalyzer.generator.pdf.PdfGenerator;
import com.raks.raksanalyzer.model.OutputFormatConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
@jakarta.ws.rs.Path("/analyze")
public class AnalysisResource {
    private static final Logger logger = LoggerFactory.getLogger(AnalysisResource.class);
    private final ConfigurationManager config = ConfigurationManager.getInstance();
    private static final Map<String, AnalysisResult> results = new ConcurrentHashMap<>();
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response analyze(AnalysisRequest request) {
        String analysisId = UUID.randomUUID().toString();
        logger.info("Received analysis request: {}", request);
        logger.info("Code Verification: BUILD_FIX_CLEANUP_UI_REWRITE");
        try {
            request.setAnalysisId(analysisId);
            if (request.getConfigFilePath() == null || request.getConfigFilePath().isEmpty()) {
                String sysConfig = System.getProperty("raksanalyzer.config.path");
                if (sysConfig != null && !sysConfig.isEmpty()) {
                    request.setConfigFilePath(sysConfig);
                    logger.info("Used custom configuration from CLI args: {}", sysConfig);
                }
            }

            // LICENSE VALIDATION FOR STANDALONE
            try {
                String licenseKey = loadLicenseKey();
                com.raks.raksanalyzer.license.LicenseValidator.validate(licenseKey);
            } catch (Exception e) {
                logger.error("License Validation Failed: {}", e.getMessage());
                Map<String, Object> error = new HashMap<>();
                error.put("status", "FAILED");
                error.put("message", "License Error: " + e.getMessage());
                return Response.status(Response.Status.FORBIDDEN).entity(error).build();
            }

            String inputSourceType = request.getInputSourceType();
            String inputPath = request.getInputPath();
            String projectName = "Project_" + analysisId.substring(0, 8); 
            String outputDir;
            if ("jar".equals(inputSourceType) || "zip".equals(inputSourceType) || "git".equals(inputSourceType)) {
                String tempDir = config.getProperty("framework.temp.directory", "./temp");
                String uploadId = request.getUploadId();
                if (uploadId == null || uploadId.isEmpty()) {
                    uploadId = analysisId;
                    request.setUploadId(uploadId);
                }
                outputDir = tempDir + "/analysis/" + uploadId;
                logger.info("Using temp directory for {}: {}", inputSourceType, outputDir);
            } else {
                outputDir = config.getProperty("framework.output.directory", "./output");
                logger.info("Using local output directory: {}", outputDir);
            }
            String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path outputPath = Paths.get(outputDir);
            if (!outputPath.isAbsolute()) {
                outputPath = Paths.get(System.getProperty("user.dir")).resolve(outputDir);
            }
            outputPath = outputPath.toAbsolutePath().normalize();
            if (!java.nio.file.Files.exists(outputPath)) {
                java.nio.file.Files.createDirectories(outputPath);
            }
            String excelPath = outputPath.resolve(projectName + "_" + timestamp + ".xlsx").toString();
            String wordPath = outputPath.resolve(projectName + "_" + timestamp + ".docx").toString();
            String pdfPath = outputPath.resolve(projectName + "_" + timestamp + ".pdf").toString();
            AnalysisResult pendingResult = new AnalysisResult();
            pendingResult.setAnalysisId(analysisId);
            pendingResult.setOutputDirectory(outputPath.toString());
            pendingResult.setExcelReportPath(excelPath);
            pendingResult.setWordDocumentPath(wordPath);
            pendingResult.setPdfDocumentPath(pdfPath);
            results.put(analysisId, pendingResult);
            Thread analysisThread = new Thread(() -> executeAnalysis(request));
            analysisThread.start();
            String normalizedOutputDir = outputPath.toString()
                .replace("\\", "/");  
            if (normalizedOutputDir.startsWith("./")) {
                normalizedOutputDir = normalizedOutputDir.substring(2);
            } else if (normalizedOutputDir.startsWith(".\\")) {
                normalizedOutputDir = normalizedOutputDir.substring(2);
            }
            Map<String, Object> response = new HashMap<>();
            response.put("status", "STARTED");
            response.put("analysisId", analysisId);
            response.put("message", "Analysis started successfully");
            response.put("projectName", projectName);
            response.put("excelPath", excelPath);
            response.put("wordPath", wordPath);
            response.put("pdfPath", pdfPath);
            response.put("outputDirectory", normalizedOutputDir);
            return Response.ok(response).build();
        } catch (Exception e) {
            logger.error("Error starting analysis", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "ERROR");
            error.put("message", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
        }
    }
    @GET
    @jakarta.ws.rs.Path("/status/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatus(@PathParam("id") String analysisId) {
        AnalysisResult result = results.get(analysisId);
        if (result == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "NOT_FOUND");
            error.put("message", "Analysis not found: " + analysisId);
            return Response.status(Response.Status.NOT_FOUND).entity(error).build();
        }
        Map<String, Object> response = new HashMap<>();
        String status;
        if (result.getEndTime() != null) {
            status = result.isSuccess() ? "COMPLETED" : "FAILED";
        } else {
            status = "IN_PROGRESS";
        }
        response.put("status", status);
        response.put("analysisId", result.getAnalysisId());
        response.put("success", result.isSuccess());
        response.put("startTime", result.getStartTime() != null ? result.getStartTime().toString() : null);
        response.put("endTime", result.getEndTime() != null ? result.getEndTime().toString() : null);
        response.put("durationMs", result.getDurationMillis());
        response.put("flowCount", result.getFlows() != null ? result.getFlows().size() : 0);
        response.put("propertyCount", result.getProperties() != null ? result.getProperties().size() : 0);
        response.put("excelPath", result.getExcelReportPath());
        response.put("wordPath", result.getWordDocumentPath());
        response.put("pdfPath", result.getPdfDocumentPath());
        response.put("progressMessage", result.getProgressMessage());
        response.put("progressPercentage", result.getProgressPercentage());
        if (!result.isSuccess()) {
            response.put("errorMessage", result.getErrorMessage());
        }
        return Response.ok(response).build();
    }
    @POST
    @jakarta.ws.rs.Path("/open/{id}/{type}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response openFile(@PathParam("id") String analysisId, @PathParam("type") String type) {
        AnalysisResult result = results.get(analysisId);
        if (result == null) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "Analysis not found")).build();
        }
        String filePath = null;
        if ("excel".equalsIgnoreCase(type)) {
            filePath = result.getExcelReportPath();
        } else if ("word".equalsIgnoreCase(type)) {
            filePath = result.getWordDocumentPath();
        } else if ("pdf".equalsIgnoreCase(type)) {
            filePath = result.getPdfDocumentPath();
        } else if ("folder".equalsIgnoreCase(type)) {
            String anyPath = result.getExcelReportPath();
            if (anyPath == null) anyPath = result.getWordDocumentPath();
            if (anyPath == null) anyPath = result.getPdfDocumentPath();
            if (anyPath != null) {
                java.io.File file = new java.io.File(anyPath);
                filePath = file.getParent();
            }
        }
        if (filePath == null) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "File not found for type: " + type)).build();
        }
        try {
            java.io.File file = new java.io.File(filePath);
            if (!file.exists()) {
                 return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "File does not exist on disk")).build();
            }
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(file);
                return Response.ok(Map.of("status", "OPENED", "path", filePath)).build();
            } else {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Desktop operations not supported on this system")).build();
            }
        } catch (Exception e) {
            logger.error("Failed to open file: " + filePath, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to open file: " + e.getMessage())).build();
        }
    }
    @GET
    @jakarta.ws.rs.Path("/download/{id}/{type}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response download(@PathParam("id") String analysisId, @PathParam("type") String type) {
        AnalysisResult result = results.get(analysisId);
        if (result == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String filePath = null;
        String fileName = null;
        if ("excel".equalsIgnoreCase(type)) {
            filePath = result.getExcelReportPath();
            fileName = "analysis-" + analysisId + ".xlsx";
        } else if ("word".equalsIgnoreCase(type)) {
            filePath = result.getWordDocumentPath();
            fileName = "design-doc-" + analysisId + ".docx";
        } else if ("pdf".equalsIgnoreCase(type)) {
            filePath = result.getPdfDocumentPath();
            fileName = "document-" + analysisId + ".pdf";
        }
        if (filePath == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        java.io.File file = new java.io.File(filePath);
        if (!file.exists()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(file)
            .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
            .build();
    }
    private AnalysisRequest buildAnalysisRequest(Map<String, Object> requestData) {
        AnalysisRequest request = new AnalysisRequest();
        String projectType = getString(requestData, "projectTechnologyType");
        if (projectType != null) {
            request.setProjectTechnologyType(ProjectType.valueOf(projectType));
        } else {
            throw new IllegalArgumentException("Project Type is required (MULE or TIBCO_BW5 or TIBCO_BW6 or SPRING_BOOT)");
        }
        String executionMode = getString(requestData, "documentGenerationExecutionMode");
        if (executionMode != null) {
            request.setDocumentGenerationExecutionMode(ExecutionMode.valueOf(executionMode));
        } else {
            request.setDocumentGenerationExecutionMode(ExecutionMode.FULL); 
        }
        String envScope = getString(requestData, "environmentAnalysisScope");
        if (envScope != null && !envScope.equalsIgnoreCase("ALL")) {
            List<String> environments = Arrays.asList(envScope.split(","));
            request.setSelectedEnvironments(environments);
        } else {
            List<String> allEnvs = config.getEnvironmentNames();
            request.setSelectedEnvironments(allEnvs);
        }
        request.setEnvironmentAnalysisScope(envScope);
        request.setInputSourceType(getString(requestData, "inputSourceType"));
        request.setInputPath(getString(requestData, "inputPath"));
        request.setGitBranch(getString(requestData, "gitBranch"));
        String configPath = getString(requestData, "configFilePath");
        if (configPath == null) {
            configPath = System.getProperty("raksanalyzer.config.path");
        }
        request.setConfigFilePath(configPath);
        OutputFormatConfig formatConfig = new OutputFormatConfig();
        formatConfig.setPdfEnabled(getBoolean(requestData, "generatePdf", true));
        formatConfig.setWordEnabled(getBoolean(requestData, "generateWord", false));
        formatConfig.setExcelEnabled(getBoolean(requestData, "generateExcel", false));
        request.setOutputFormatConfig(formatConfig);
        logger.info("Format config - PDF: {}, Word: {}, Excel: {}", 
                   formatConfig.isPdfEnabled(), formatConfig.isWordEnabled(), formatConfig.isExcelEnabled());
        return request;
    }
    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    private boolean getBoolean(Map<String, Object> data, String key, boolean defaultValue) {
        Object value = data.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return "true".equalsIgnoreCase(value.toString());
    }
    private String extractProjectName(String inputPath) {
        if (inputPath == null || inputPath.trim().isEmpty()) {
            return "project";
        }
        try {
            Path path = Paths.get(inputPath);
            if (!path.isAbsolute()) {
                path = Paths.get(System.getProperty("user.dir")).resolve(inputPath);
            }
            if (java.nio.file.Files.isDirectory(path)) {
                try (java.util.stream.Stream<Path> entries = java.nio.file.Files.list(path)) {
                    List<Path> muleProjects = entries
                        .filter(java.nio.file.Files::isDirectory)
                        .filter(dir -> java.nio.file.Files.exists(dir.resolve("pom.xml")))
                        .collect(java.util.stream.Collectors.toList());
                    if (muleProjects.size() == 1) {
                        return muleProjects.get(0).getFileName().toString();
                    }
                }
                return path.getFileName().toString();
            } else {
                String fileName = path.getFileName().toString();
                int dotIndex = fileName.lastIndexOf('.');
                return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
            }
        } catch (Exception e) {
            logger.warn("Error extracting project name from path: {}", inputPath, e);
            String cleanPath = inputPath.replace("\\", "/");
            String[] parts = cleanPath.split("/");
            return parts[parts.length - 1];
        }
    }
    private void executeAnalysis(AnalysisRequest request) {
        try {
            logger.info("Executing analysis: {}", request);
            String inputSourceType = request.getInputSourceType();
            Path inputPath;
            String uploadId = request.getUploadId();

            if ("git".equals(inputSourceType)) {
                if (uploadId == null) {
                    uploadId = UUID.randomUUID().toString();
                    request.setUploadId(uploadId);
                }
                String tempDir = config.getProperty("framework.temp.directory", "./temp");
                
                // Extract repo name from URL to make path more identifiable
                String repoName = "repo";
                String url = request.getInputPath();
                if (url != null && url.contains("/")) {
                    repoName = url.substring(url.lastIndexOf("/") + 1);
                    if (repoName.endsWith(".git")) {
                        repoName = repoName.substring(0, repoName.length() - 4);
                    }
                }
                
                Path gitClonePath = Paths.get(tempDir).resolve("git").resolve(uploadId).resolve(repoName).toAbsolutePath().normalize();
                
                if (!java.nio.file.Files.exists(gitClonePath)) {
                    java.nio.file.Files.createDirectories(gitClonePath);
                }

                logger.info("Cloning Git repository: {} (branch: {}) to {}", 
                            request.getInputPath(), request.getGitBranch(), gitClonePath);
                
                String providerType = "github"; // Default or detect from URL
                if (request.getInputPath().contains("gitlab")) providerType = "gitlab";
                
                // For execution mode, we might need a token if it's private. 
                // We'll assume the URL might have it or it's public for now, 
                // but the UI will pass the token in the future.
                // For now, let's use a simple JGit clone if no token provided.
                
                com.raks.raksanalyzer.provider.GitProvider provider;
                String token = request.getGitToken();
                if (providerType.equals("github")) {
                    provider = new com.raks.raksanalyzer.provider.GitHubProvider(token);
                } else {
                    provider = new com.raks.raksanalyzer.provider.GitLabProvider(token);
                }
                
                provider.cloneRepository(request.getInputPath(), gitClonePath.toFile(), request.getGitBranch());
                inputPath = gitClonePath;
            } else {
                inputPath = Paths.get(request.getInputPath()).toAbsolutePath().normalize();
            }

            AnalysisResult ar = results.get(request.getAnalysisId());
            if (ar != null) {
                ar.setProgressMessage("Discovering projects...");
                ar.setProgressPercentage(10);
                ar.setSourceUrl(request.getInputPath());
            }

            // Ensure output directory is set in ar for generators to use
            if (ar != null && (ar.getOutputDirectory() == null || ar.getOutputDirectory().isEmpty())) {
                String outDir = config.getProperty("framework.output.directory", "./output");
                if ("git".equals(inputSourceType) || "zip".equals(inputSourceType) || "jar".equals(inputSourceType)) {
                    String tempDir = config.getProperty("framework.temp.directory", "./temp");
                    outDir = tempDir + "/analysis/" + uploadId;
                }
                ar.setOutputDirectory(Paths.get(outDir).toAbsolutePath().normalize().toString());
                logger.info("Set output directory for analysis {}: {}", request.getAnalysisId(), ar.getOutputDirectory());
            }

            List<com.raks.raksanalyzer.core.discovery.DiscoveredProject> projects = 
                com.raks.raksanalyzer.core.discovery.ProjectDiscovery.findProjects(inputPath, request.getProjectTechnologyType());
            if (projects.isEmpty()) {
                String errorMsg = "The selected " + ("git".equals(inputSourceType) ? "Git repository" : "path") + 
                                 " does not contain any " + request.getProjectTechnologyType().getDisplayName() + " project artifacts.";
                logger.error(errorMsg);
                ar = results.get(request.getAnalysisId());
                if (ar != null) {
                    ar.setSuccess(false);
                    ar.setErrorMessage(errorMsg);
                    ar.setEndTime(java.time.LocalDateTime.now());
                }
                return;
            }
            logger.info("Found {} {} project(s) to analyze", projects.size(), request.getProjectTechnologyType());
            for (com.raks.raksanalyzer.core.discovery.DiscoveredProject project : projects) {
                logger.info("Analyzing project: {} at {}", project.getProjectName(), project.getProjectPath());
                AnalysisRequest projectRequest = new AnalysisRequest();
                projectRequest.setProjectTechnologyType(request.getProjectTechnologyType());
                projectRequest.setDocumentGenerationExecutionMode(request.getDocumentGenerationExecutionMode());
                projectRequest.setEnvironmentAnalysisScope(request.getEnvironmentAnalysisScope());
                projectRequest.setSelectedEnvironments(request.getSelectedEnvironments());
                projectRequest.setInputSourceType(request.getInputSourceType());
                projectRequest.setInputPath(project.getProjectPath().toString());
                projectRequest.setAnalysisId(request.getAnalysisId()); 
                projectRequest.setConfigFilePath(request.getConfigFilePath()); 
                
                if (ar != null) {
                    ar.setProgressMessage("Analyzing project: " + project.getProjectName());
                    ar.setProgressPercentage(30);
                }
                
                AnalysisResult result = AnalyzerFactory.analyze(projectRequest);
                result.setAnalysisId(request.getAnalysisId());
                result.setSourceUrl(request.getInputPath());
                ExecutionMode mode = request.getDocumentGenerationExecutionMode();
                OutputFormatConfig formatConfig = request.getOutputFormatConfig();
                if (formatConfig == null) {
                    formatConfig = new OutputFormatConfig(); 
                }
                if ((mode == ExecutionMode.FULL || mode == ExecutionMode.ANALYZE_ONLY) && formatConfig.isExcelEnabled()) {
                    if (result.getProjectInfo().getProjectType() == com.raks.raksanalyzer.domain.enums.ProjectType.TIBCO_BW5) {
                        com.raks.raksanalyzer.generator.excel.TibcoExcelGenerator tibcoExcelGen = new com.raks.raksanalyzer.generator.excel.TibcoExcelGenerator();
                        String excelPath = tibcoExcelGen.generate(result);
                        result.setExcelReportPath(excelPath);
                        logger.info("Tibco Excel report generated: {}", excelPath);
                    } else {
                        ExcelGenerator excelGen = new ExcelGenerator();
                        Path excelPath = excelGen.generate(result);
                        result.setExcelReportPath(excelPath.toString());
                        logger.info("Excel report generated: {}", excelPath);
                    }
                    if (ar != null) {
                        ar.setProgressMessage("Excel report generated");
                        ar.setProgressPercentage(60);
                    }
                }
                if ((mode == ExecutionMode.FULL || mode == ExecutionMode.GENERATE_ONLY) && formatConfig.isPdfEnabled()) {
                    try {
                        if (result.getProjectInfo().getProjectType() == com.raks.raksanalyzer.domain.enums.ProjectType.TIBCO_BW5) {
                            com.raks.raksanalyzer.generator.pdf.TibcoPdfGenerator tibcoPdfGen = new com.raks.raksanalyzer.generator.pdf.TibcoPdfGenerator();
                            String pdfPath = tibcoPdfGen.generate(result);
                            result.setPdfDocumentPath(pdfPath);
                            logger.info("Tibco PDF document generated: {}", pdfPath);
                        } else {
                            PdfGenerator pdfGen = new PdfGenerator(config);
                            Path pdfPath = pdfGen.generate(result);
                            result.setPdfDocumentPath(pdfPath.toString());
                            logger.info("PDF document generated: {}", pdfPath);
                        }
                        if (ar != null) {
                            ar.setProgressMessage("PDF document generated");
                            ar.setProgressPercentage(80);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to generate PDF", e);
                    }
                }
                logger.info("Format config - PDF: {}, Word: {}, Excel: {}", 
                    formatConfig.isPdfEnabled(), formatConfig.isWordEnabled(), formatConfig.isExcelEnabled());
                if ((mode == ExecutionMode.FULL || mode == ExecutionMode.GENERATE_ONLY) && formatConfig.isWordEnabled()) {
                    if (result.getProjectInfo().getProjectType() == com.raks.raksanalyzer.domain.enums.ProjectType.TIBCO_BW5) {
                        com.raks.raksanalyzer.generator.word.TibcoWordGenerator tibcoWordGen = new com.raks.raksanalyzer.generator.word.TibcoWordGenerator();
                        String wordPath = tibcoWordGen.generate(result);
                        result.setWordDocumentPath(wordPath);
                        logger.info("Tibco Word document generated: {}", wordPath);
                    } else {
                        WordGenerator wordGen = new WordGenerator();
                        Path wordPath = wordGen.generate(result);
                        result.setWordDocumentPath(wordPath.toString());
                        logger.info("Word document generated: {}", wordPath);
                    }
                    if (ar != null) {
                        ar.setProgressMessage("Word document generated");
                        ar.setProgressPercentage(95);
                    }
                }
                if (result.getEndTime() == null) {
                    result.setEndTime(java.time.LocalDateTime.now());
                }
                results.put(result.getAnalysisId(), result);
                logger.info("Analysis completed successfully for project: {}", project.getProjectName());
            }
            logger.info("All {} project(s) analyzed successfully", projects.size());
        } catch (Exception e) {
            logger.error("Analysis failed", e);
            AnalysisResult ar = results.get(request.getAnalysisId());
            if (ar != null) {
                ar.setSuccess(false);
                ar.setErrorMessage(e.getMessage());
                ar.setEndTime(java.time.LocalDateTime.now());
            }
        } finally {
            String inputSourceType = request.getInputSourceType();
            if ("zip".equals(inputSourceType) || "jar".equals(inputSourceType) || "git".equals(inputSourceType)) {
                String uploadId = request.getUploadId();
                if (uploadId != null) {
                    com.raks.raksanalyzer.util.FileExtractionUtil.cleanupTempDirectory(uploadId);
                    logger.info("Cleaned up temporary files for {}: {}", inputSourceType, uploadId);
                }
            }
        }
    }
    @POST
    @jakarta.ws.rs.Path("/email")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response sendEmail(Map<String, Object> requestData) {
        try {
            String email = (String) requestData.get("email");
            String analysisId = (String) requestData.get("analysisId");
            if (email == null || email.trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "Email address is required");
                return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
            }
            if (analysisId == null || analysisId.trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "Analysis ID is required");
                return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
            }
            AnalysisResult result = results.get(analysisId);
            if (result == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "Analysis not found");
                return Response.status(Response.Status.NOT_FOUND).entity(error).build();
            }
            List<String> documentPaths = Arrays.asList(
                result.getExcelReportPath(),
                result.getWordDocumentPath(),
                result.getPdfDocumentPath()
            );
            com.raks.raksanalyzer.service.EmailService emailService = 
                new com.raks.raksanalyzer.service.EmailService();
            boolean success = emailService.sendDocuments(
                email, 
                result.getProjectInfo() != null ? result.getProjectInfo().getProjectName() : "Project", 
                documentPaths
            );
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", success ? 
                "Documents sent successfully to " + email : 
                "Failed to send email. Please check logs.");
            return Response.ok(response).build();
        } catch (Exception e) {
            logger.error("Error sending email", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(error).build();
        }
    }
    @POST
    @jakarta.ws.rs.Path("/cleanup/{analysisId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response cleanup(@PathParam("analysisId") String analysisId) {
        try {
            logger.info("Cleanup requested for analysis: {}", analysisId);
            AnalysisResult result = results.get(analysisId);
            if (result == null) {
                logger.warn("Analysis not found for cleanup: {}", analysisId);
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "No cleanup needed");
                return Response.ok(response).build();
            }
            String outputDirStr = result.getOutputDirectory();
            boolean isTemp = false;
            if (outputDirStr != null) {
                try {
                    java.nio.file.Path outputPath = java.nio.file.Paths.get(outputDirStr).toAbsolutePath().normalize();
                    String tempDirConfig = config.getProperty("framework.temp.directory", "./temp");
                    java.nio.file.Path tempPath = java.nio.file.Paths.get(System.getProperty("user.dir"))
                                                 .resolve(tempDirConfig)
                                                 .toAbsolutePath()
                                                 .normalize();
                    logger.info("Cleanup Check: Output Path: {}", outputPath);
                    logger.info("Cleanup Check: Temp Root: {}", tempPath);
                    if (outputPath.startsWith(tempPath)) {
                        isTemp = true;
                    } else {
                        if (outputPath.toString().contains(java.io.File.separator + "temp" + java.io.File.separator) || 
                            outputPath.toString().endsWith(java.io.File.separator + "temp")) {
                            logger.info("Fallback check passed: Path contains 'temp' segment");
                            isTemp = true;
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Path check failed for: {}", outputDirStr, e);
                }
            }
            if (isTemp) {
                int deletedCount = 0;
                if (result.getExcelReportPath() != null) {
                    try {
                        java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(result.getExcelReportPath()));
                        deletedCount++;
                        logger.info("Deleted: {}", result.getExcelReportPath());
                    } catch (Exception e) {
                        logger.warn("Failed to delete Excel: {}", result.getExcelReportPath(), e);
                    }
                }
                if (result.getWordDocumentPath() != null) {
                    try {
                        java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(result.getWordDocumentPath()));
                        deletedCount++;
                        logger.info("Deleted: {}", result.getWordDocumentPath());
                    } catch (Exception e) {
                        logger.warn("Failed to delete Word: {}", result.getWordDocumentPath(), e);
                    }
                }
                if (result.getPdfDocumentPath() != null) {
                    try {
                        java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(result.getPdfDocumentPath()));
                        deletedCount++;
                        logger.info("Deleted: {}", result.getPdfDocumentPath());
                    } catch (Exception e) {
                        logger.warn("Failed to delete PDF: {}", result.getPdfDocumentPath(), e);
                    }
                }
                try {
                    java.nio.file.Path tempDir = java.nio.file.Paths.get(outputDirStr);
                    if (java.nio.file.Files.exists(tempDir)) {
                        logger.info("Attempting to recursively delete output dir: {}", tempDir);
                        com.raks.raksanalyzer.util.FileExtractionUtil.deleteRecursively(tempDir);
                        if (!java.nio.file.Files.exists(tempDir)) {
                             logger.info("Successfully deleted analysis output directory: {}", tempDir);
                             deletedCount++; 
                        } else {
                             logger.warn("Failed to delete analysis output directory (still exists): {}", tempDir);
                        }
                    } else {
                        logger.warn("Analysis output directory does not exist: {}", tempDir);
                    }
                } catch (Exception e) {
                    logger.warn("Exception during analysis output directory deletion: {}", outputDirStr, e);
                }
                if (result.getProjectInfo() != null) {
                    String inputPath = result.getProjectInfo().getProjectPath();
                    logger.info("Checking for upload cleanup. InputPath: {}", inputPath);
                    if (inputPath != null && (inputPath.contains("temp") && (inputPath.contains("uploads") || inputPath.contains("upload")))) {
                        try {
                            java.nio.file.Path path = java.nio.file.Paths.get(inputPath);
                            java.nio.file.Path parent = path;
                            boolean found = false;
                            while (parent != null) {
                                java.nio.file.Path grandParent = parent.getParent();
                                if (grandParent != null && grandParent.getFileName() != null && 
                                    grandParent.getFileName().toString().equals("uploads")) {
                                    String uploadId = parent.getFileName().toString();
                                    logger.info("Identified upload directory for cleanup: {} (ID: {})", parent, uploadId);
                                    com.raks.raksanalyzer.util.FileExtractionUtil.cleanupTempDirectory(uploadId);
                                    found = true;
                                    break;
                                }
                                parent = grandParent;
                            }
                            if (!found) {
                                logger.info("Could not identify upload parent folder 'uploads' in path hierarchy: {}", inputPath);
                            }
                        } catch (Exception e) {
                             logger.warn("Failed to identify upload directory from path: {}", inputPath, e);
                        }
                    } else {
                        logger.info("Input path does not look like a temp upload path: {}", inputPath);
                    }
                } else {
                    logger.warn("ProjectInfo is null, cannot check for upload cleanup.");
                }
                logger.info("Cleanup completed: {} files deleted", deletedCount);
            } else {
                logger.info("Skipping cleanup - documents in permanent output folder");
            }
            results.remove(analysisId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Cleanup completed");
            return Response.ok(response).build();
        } catch (Exception e) {
            logger.error("Error during cleanup", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Cleanup failed: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(error).build();
        }
    }

    private String loadLicenseKey() {
        // 1. Try System Property
        String key = System.getProperty("license.key");
        if (key != null && !key.trim().isEmpty()) return key;

        // 2. Try Environment Variable
        key = System.getenv("LICENSE_KEY");
        if (key != null && !key.trim().isEmpty()) return key;
        
        return null; // Validator handles null
    }
}
