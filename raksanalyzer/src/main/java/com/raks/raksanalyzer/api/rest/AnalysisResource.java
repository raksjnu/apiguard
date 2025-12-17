package com.raks.raksanalyzer.api.rest;

import com.raks.raksanalyzer.analyzer.AnalyzerFactory;
import com.raks.raksanalyzer.core.config.ConfigurationManager;
import com.raks.raksanalyzer.domain.enums.ExecutionMode;
import com.raks.raksanalyzer.domain.enums.ProjectType;
import com.raks.raksanalyzer.domain.model.AnalysisRequest;
import com.raks.raksanalyzer.domain.model.AnalysisResult;
import com.raks.raksanalyzer.generator.excel.ExcelGenerator;
import com.raks.raksanalyzer.generator.word.WordGenerator;
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
    public Response analyze(Map<String, String> requestData) {
        try {
            logger.info("Received analysis request: {}", requestData);
            
            // Build AnalysisRequest
            AnalysisRequest request = buildAnalysisRequest(requestData);
            
            // Execute analysis in background thread
            Thread analysisThread = new Thread(() -> executeAnalysis(request));
            analysisThread.start();
            
            // Return immediate response with analysis ID
            Map<String, Object> response = new HashMap<>();
            response.put("status", "STARTED");
            response.put("message", "Analysis started successfully");
            
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
    private AnalysisRequest buildAnalysisRequest(Map<String, String> requestData) {
        AnalysisRequest request = new AnalysisRequest();
        
        // Project type
        String projectType = requestData.get("projectTechnologyType");
        if (projectType != null) {
            request.setProjectTechnologyType(ProjectType.valueOf(projectType));
        } else {
            request.setProjectTechnologyType(ProjectType.MULE); // Default
        }
        
        // Execution mode
        String executionMode = requestData.get("documentGenerationExecutionMode");
        if (executionMode != null) {
            request.setDocumentGenerationExecutionMode(ExecutionMode.valueOf(executionMode));
        } else {
            request.setDocumentGenerationExecutionMode(ExecutionMode.FULL); // Default
        }
        
        // Environment scope
        String envScope = requestData.get("environmentAnalysisScope");
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
        request.setInputSourceType(requestData.get("inputSourceType"));
        request.setInputPath(requestData.get("inputPath"));
        request.setGitBranch(requestData.get("gitBranch"));
        
        return request;
    }
    
    /**
     * Execute analysis (runs in background thread).
     */
    private void executeAnalysis(AnalysisRequest request) {
        try {
            logger.info("Executing analysis: {}", request);
            
            // Run analyzer
            AnalysisResult result = AnalyzerFactory.analyze(request);
            
            // Generate documents based on execution mode
            ExecutionMode mode = request.getDocumentGenerationExecutionMode();
            
            if (mode == ExecutionMode.FULL || mode == ExecutionMode.ANALYZE_ONLY) {
                // Generate Excel
                ExcelGenerator excelGen = new ExcelGenerator();
                Path excelPath = excelGen.generate(result);
                result.setExcelReportPath(excelPath.toString());
                logger.info("Excel report generated: {}", excelPath);
            }
            
            if (mode == ExecutionMode.FULL || mode == ExecutionMode.GENERATE_ONLY) {
                // Generate Word
                WordGenerator wordGen = new WordGenerator();
                Path wordPath = wordGen.generate(result);
                result.setWordDocumentPath(wordPath.toString());
                logger.info("Word document generated: {}", wordPath);
            }
            
            // Store result
            results.put(result.getAnalysisId(), result);
            
            logger.info("Analysis completed successfully: {}", result.getAnalysisId());
            
        } catch (Exception e) {
            logger.error("Analysis failed", e);
        }
    }
}
