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
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST API endpoint for project analysis.
 * 
 * Endpoints:
 * - POST /api/analyze - Start analysis
 * - GET /api/status/{id} - Get analysis status
 * - GET /api/download/{id}/{type} - Download generated documents
 */
@jakarta.ws.rs.Path("/analyze")
public class AnalysisResource {
    private static final Logger logger = LoggerFactory.getLogger(AnalysisResource.class);
    
    private final ConfigurationManager config = ConfigurationManager.getInstance();
    
    // Store analysis results in memory (in production, use a database)
    private static final Map<String, AnalysisResult> results = new ConcurrentHashMap<>();
    
    /**
     * Start project analysis.
     * 
     * POST /api/analyze
     * Body: {
     *   "projectTechnologyType": "MULE",
     *   "documentGenerationExecutionMode": "FULL",
     *   "environmentAnalysisScope": "dev,qa,prod",
     *   "inputSourceType": "folder",
     *   "inputPath": "C:/projects/my-mule-app"
     * }
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response analyze(Map<String, Object> requestData) {
        try {
            logger.info("Received analysis request: {}", requestData);
            
            // Build AnalysisRequest
            AnalysisRequest request = buildAnalysisRequest(requestData);
            
            // Execute analysis in background thread
            Thread analysisThread = new Thread(() -> executeAnalysis(request));
            analysisThread.start();
            
            // Extract project name from input path
            String inputPath = getString(requestData, "inputPath");
            String projectName = extractProjectName(inputPath);
            
            // Generate expected file paths
            String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String outputDir = config.getProperty("framework.output.directory", "./output");
            
            // Convert relative path to absolute if needed
            Path outputPath = Paths.get(outputDir);
            if (!outputPath.isAbsolute()) {
                outputPath = Paths.get(System.getProperty("user.dir")).resolve(outputDir);
            }
            
            String excelPath = outputPath.resolve(projectName + "_" + timestamp + ".xlsx").toString();
            String wordPath = outputPath.resolve(projectName + "_" + timestamp + ".docx").toString();
            String pdfPath = outputPath.resolve(projectName + "_" + timestamp + ".pdf").toString();
            
            // Create a pending result so we have an ID to return
            String analysisId = java.util.UUID.randomUUID().toString();
            AnalysisResult pendingResult = new AnalysisResult();
            pendingResult.setAnalysisId(analysisId);
            pendingResult.setExcelReportPath(excelPath);
            pendingResult.setWordDocumentPath(wordPath);
            pendingResult.setPdfDocumentPath(pdfPath);
            // Store it with the ID the frontend will use
            results.put(analysisId, pendingResult);
            
            // Pass ID to bg thread
            request.setAnalysisId(analysisId);

            // Return immediate response with expected file paths
            Map<String, Object> response = new HashMap<>();
            response.put("status", "STARTED");
            response.put("analysisId", analysisId);
            response.put("message", "Analysis started successfully");
            response.put("projectName", projectName);
            response.put("excelPath", excelPath);
            response.put("wordPath", wordPath);
            response.put("pdfPath", pdfPath);
            response.put("outputDirectory", outputPath.toString());
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Error starting analysis", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "ERROR");
            error.put("message", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
        }
    }
    
    /**
     * Get analysis status.
     * 
     * GET /api/status/{id}
     */
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
        response.put("analysisId", result.getAnalysisId());
        response.put("success", result.isSuccess());
        response.put("startTime", result.getStartTime().toString());
        response.put("endTime", result.getEndTime() != null ? result.getEndTime().toString() : null);
        response.put("durationMs", result.getDurationMillis());
        response.put("flowCount", result.getFlows().size());
        response.put("propertyCount", result.getProperties().size());
        response.put("excelPath", result.getExcelReportPath());
        response.put("wordPath", result.getWordDocumentPath());
        
        if (!result.isSuccess()) {
            response.put("errorMessage", result.getErrorMessage());
        }
        
        return Response.ok(response).build();
    }
    
    /**
     * Open generated document in system editor.
     * 
     * POST /api/open/{id}/{type}
     * type: "excel", "word", "pdf", "folder"
     */
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
            // Use any valid path to get the parent folder
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

    /**
     * Download generated document.
     * 
     * GET /api/download/{id}/{type}
     * type: "excel" or "word"
     */
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
    
    /**
     * Build AnalysisRequest from request data.
     */
    private AnalysisRequest buildAnalysisRequest(Map<String, Object> requestData) {
        AnalysisRequest request = new AnalysisRequest();
        
        // Project type
        String projectType = getString(requestData, "projectTechnologyType");
        if (projectType != null) {
            request.setProjectTechnologyType(ProjectType.valueOf(projectType));
        } else {
            request.setProjectTechnologyType(ProjectType.MULE); // Default
        }
        
        // Execution mode
        String executionMode = getString(requestData, "documentGenerationExecutionMode");
        if (executionMode != null) {
            request.setDocumentGenerationExecutionMode(ExecutionMode.valueOf(executionMode));
        } else {
            request.setDocumentGenerationExecutionMode(ExecutionMode.FULL); // Default
        }
        
        // Environment scope
        String envScope = getString(requestData, "environmentAnalysisScope");
        if (envScope != null && !envScope.equalsIgnoreCase("ALL")) {
            List<String> environments = Arrays.asList(envScope.split(","));
            request.setSelectedEnvironments(environments);
        } else {
            // Use all configured environments
            List<String> allEnvs = config.getEnvironmentNames();
            request.setSelectedEnvironments(allEnvs);
        }
        request.setEnvironmentAnalysisScope(envScope);
        
        // Input source
        request.setInputSourceType(getString(requestData, "inputSourceType"));
        request.setInputPath(getString(requestData, "inputPath"));
        request.setGitBranch(getString(requestData, "gitBranch"));
        
        // Output format preferences - handle both boolean and string values
        OutputFormatConfig formatConfig = new OutputFormatConfig();
        formatConfig.setPdfEnabled(getBoolean(requestData, "generatePdf", true));
        formatConfig.setWordEnabled(getBoolean(requestData, "generateWord", false));
        formatConfig.setExcelEnabled(getBoolean(requestData, "generateExcel", false));
        request.setOutputFormatConfig(formatConfig);
        
        logger.info("Format config - PDF: {}, Word: {}, Excel: {}", 
                   formatConfig.isPdfEnabled(), formatConfig.isWordEnabled(), formatConfig.isExcelEnabled());
        
        return request;
    }
    
    /**
     * Helper to get String value from request data
     */
    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    /**
     * Helper to get boolean value from request data (handles both boolean and string)
     */
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
    
    
    /**
     * Extract the actual project name from the input path.
     * If the input path is a directory containing a single Mule project, return that project name.
     * Otherwise, return the last segment of the path.
     */
    private String extractProjectName(String inputPath) {
        if (inputPath == null || inputPath.trim().isEmpty()) {
            return "project";
        }
        
        try {
            // Resolve relative paths
            Path path = Paths.get(inputPath);
            if (!path.isAbsolute()) {
                path = Paths.get(System.getProperty("user.dir")).resolve(inputPath);
            }
            
            // If it's a directory, check if it contains a single Mule project subdirectory
            if (java.nio.file.Files.isDirectory(path)) {
                // Look for subdirectories with pom.xml (Mule projects)
                try (java.util.stream.Stream<Path> entries = java.nio.file.Files.list(path)) {
                    List<Path> muleProjects = entries
                        .filter(java.nio.file.Files::isDirectory)
                        .filter(dir -> java.nio.file.Files.exists(dir.resolve("pom.xml")))
                        .collect(java.util.stream.Collectors.toList());
                    
                    // If there's exactly one Mule project, use its name
                    if (muleProjects.size() == 1) {
                        return muleProjects.get(0).getFileName().toString();
                    }
                }
                
                // Otherwise, use the directory name itself
                return path.getFileName().toString();
            } else {
                // If it's a file (e.g., ZIP), use the filename without extension
                String fileName = path.getFileName().toString();
                int dotIndex = fileName.lastIndexOf('.');
                return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
            }
        } catch (Exception e) {
            logger.warn("Error extracting project name from path: {}", inputPath, e);
            // Fallback: extract last segment from path
            String cleanPath = inputPath.replace("\\", "/");
            String[] parts = cleanPath.split("/");
            return parts[parts.length - 1];
        }
    }
    
    /**
     * Execute analysis (runs in background thread).
     */
    private void executeAnalysis(AnalysisRequest request) {
        try {
            logger.info("Executing analysis: {}", request);
            
            // Discover all Mule projects in the input path
            Path inputPath = Paths.get(request.getInputPath());
            List<com.raks.raksanalyzer.core.discovery.DiscoveredProject> projects = 
                com.raks.raksanalyzer.core.discovery.ProjectDiscovery.findMuleProjects(inputPath);
            
            if (projects.isEmpty()) {
                logger.error("No Mule projects found in: {}", inputPath);
                return;
            }
            
            logger.info("Found {} Mule project(s) to analyze", projects.size());
            
            // Process each project separately
            for (com.raks.raksanalyzer.core.discovery.DiscoveredProject project : projects) {
                logger.info("Analyzing project: {} at {}", project.getProjectName(), project.getProjectPath());
                
                // Create a new request for this specific project
                AnalysisRequest projectRequest = new AnalysisRequest();
                projectRequest.setProjectTechnologyType(request.getProjectTechnologyType());
                projectRequest.setDocumentGenerationExecutionMode(request.getDocumentGenerationExecutionMode());
                projectRequest.setEnvironmentAnalysisScope(request.getEnvironmentAnalysisScope());
                projectRequest.setSelectedEnvironments(request.getSelectedEnvironments());
                projectRequest.setInputSourceType(request.getInputSourceType());
                projectRequest.setInputPath(project.getProjectPath().toString());
                
                // Run analyzer for this project
                AnalysisResult result = AnalyzerFactory.analyze(projectRequest);
                
                // Generate documents based on execution mode and format preferences
                ExecutionMode mode = request.getDocumentGenerationExecutionMode();
                OutputFormatConfig formatConfig = request.getOutputFormatConfig();
                
                if (formatConfig == null) {
                    formatConfig = new OutputFormatConfig(); // Use defaults
                }
                
                if ((mode == ExecutionMode.FULL || mode == ExecutionMode.ANALYZE_ONLY) && formatConfig.isExcelEnabled()) {
                    // Generate Excel
                    ExcelGenerator excelGen = new ExcelGenerator();
                    Path excelPath = excelGen.generate(result);
                    result.setExcelReportPath(excelPath.toString());
                    logger.info("Excel report generated: {}", excelPath);
                }
                
                if ((mode == ExecutionMode.FULL || mode == ExecutionMode.GENERATE_ONLY) && formatConfig.isPdfEnabled()) {
                    // Generate PDF
                    try {
                        PdfGenerator pdfGen = new PdfGenerator(config);
                        Path pdfPath = pdfGen.generate(result);
                        result.setPdfDocumentPath(pdfPath.toString());
                        logger.info("PDF document generated: {}", pdfPath);
                    } catch (Exception e) {
                        logger.error("Failed to generate PDF", e);
                    }
                }
                
                if ((mode == ExecutionMode.FULL || mode == ExecutionMode.GENERATE_ONLY) && formatConfig.isWordEnabled()) {
                    // Generate Word
                    WordGenerator wordGen = new WordGenerator();
                    Path wordPath = wordGen.generate(result);
                    result.setWordDocumentPath(wordPath.toString());
                    logger.info("Word document generated: {}", wordPath);
                }
                
                // Store result with project-specific ID
                String resultId = result.getAnalysisId() + "_" + project.getProjectName();
                results.put(resultId, result);
                
                logger.info("Analysis completed successfully for project: {}", project.getProjectName());
            }
            
            logger.info("All {} project(s) analyzed successfully", projects.size());
            
        } catch (Exception e) {
            logger.error("Analysis failed", e);
        }
    }
}
