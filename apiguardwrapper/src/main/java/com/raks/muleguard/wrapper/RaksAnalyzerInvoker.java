package com.raks.muleguard.wrapper;
import com.raks.raksanalyzer.analyzer.AnalyzerFactory;
import com.raks.raksanalyzer.core.discovery.DiscoveredProject;
import com.raks.raksanalyzer.core.discovery.ProjectDiscovery;
import com.raks.raksanalyzer.domain.enums.ProjectType;
import com.raks.raksanalyzer.domain.model.AnalysisRequest;
import com.raks.raksanalyzer.domain.model.AnalysisResult;
import com.raks.raksanalyzer.model.OutputFormatConfig;
import com.raks.raksanalyzer.util.FileExtractionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
public class RaksAnalyzerInvoker {
    private static final Logger logger = LoggerFactory.getLogger(RaksAnalyzerInvoker.class);
    public static Map<String, Object> analyze(String inputPath, String configPath, String outputDir, String workingDir, 
                                               boolean generateExcel, boolean generateWord, boolean generatePdf, String uploadId) {
        Map<String, Object> response = new HashMap<>();
        if (uploadId == null || uploadId.trim().isEmpty()) {
            uploadId = UUID.randomUUID().toString();
            logger.info("No uploadId provided, generated new ID: {}", uploadId);
        }
        String actualInputPath = inputPath;
        String extractedPath = null;
        try {
            logger.info("Starting RaksAnalyzer invocation for input: {}", inputPath);
            String inputSourceType = detectInputSourceType(inputPath);
            if ("zip".equals(inputSourceType) || "jar".equals(inputSourceType) || "ear".equals(inputSourceType)) {
                logger.info("Extracting archive...");
                Path archivePath = Paths.get(inputPath);
                Path extractionDest;
                if ("ear".equals(inputSourceType)) {
                    extractionDest = FileExtractionUtil.extractEar(archivePath, uploadId, workingDir);
                } else if ("jar".equals(inputSourceType)) {
                    extractionDest = FileExtractionUtil.extractJar(archivePath, uploadId, workingDir);
                } else {
                    extractionDest = FileExtractionUtil.extractZip(archivePath, uploadId, workingDir);
                }
                actualInputPath = extractionDest.toString();
                extractedPath = actualInputPath;
                logger.info("Extracted to: {}", actualInputPath);
            }
            Path projectRoot = Paths.get(actualInputPath).toAbsolutePath().normalize();
            ProjectType projectType = "ear".equals(inputSourceType) ? ProjectType.TIBCO_BW5 : ProjectType.MULE;
            List<DiscoveredProject> projects = ProjectDiscovery.findProjects(projectRoot, projectType);
            if (projects.isEmpty()) {
                if (projectType == ProjectType.MULE) {
                     projects = ProjectDiscovery.findProjects(projectRoot, ProjectType.TIBCO_BW5);
                     if (!projects.isEmpty()) projectType = ProjectType.TIBCO_BW5;
                }
            }
            if (projects.isEmpty()) {
                throw new RuntimeException("No valid Mule or Tibco projects found in input.");
            }
            DiscoveredProject project = projects.get(0); 
            logger.info("Analyzing project: {}", project.getProjectName());
            AnalysisRequest request = new AnalysisRequest();
            request.setInputPath(project.getProjectPath().toString());
            request.setProjectTechnologyType(projectType);
            request.setInputSourceType(inputSourceType);
            if (configPath != null) {
                request.setConfigFilePath(configPath);
            }
            Path outputPath = Paths.get(outputDir);
            if (!java.nio.file.Files.exists(outputPath)) {
                java.nio.file.Files.createDirectories(outputPath);
            }
            request.setOutputDirectory(outputPath.toString());
            OutputFormatConfig formatConfig = new OutputFormatConfig();
            formatConfig.setPdfEnabled(generatePdf);
            formatConfig.setWordEnabled(generateWord);
            formatConfig.setExcelEnabled(generateExcel);
            request.setOutputFormatConfig(formatConfig);
            AnalysisResult result = AnalyzerFactory.analyze(request);
            if (!result.isSuccess()) {
                throw new RuntimeException("Analysis failed: " + result.getErrorMessage());
            }
            com.raks.raksanalyzer.core.config.ConfigurationManager config = 
                com.raks.raksanalyzer.core.config.ConfigurationManager.getInstance();
            Path pdfPath = null;
            Path wordPath = null;
            Path excelPath = null;
            if (projectType == ProjectType.TIBCO_BW5) {
                 if (generatePdf) {
                     String pdfPathStr = new com.raks.raksanalyzer.generator.pdf.TibcoPdfGenerator().generate(result);
                     if (pdfPathStr != null) pdfPath = java.nio.file.Paths.get(pdfPathStr);
                 }
                 if (generateWord) {
                     String wordPathStr = new com.raks.raksanalyzer.generator.word.TibcoWordGenerator().generate(result);
                     if (wordPathStr != null) wordPath = java.nio.file.Paths.get(wordPathStr);
                 }
                 if (generateExcel) {
                     String excelPathStr = new com.raks.raksanalyzer.generator.excel.TibcoExcelGenerator().generate(result);
                     if (excelPathStr != null) excelPath = java.nio.file.Paths.get(excelPathStr);
                 }
            } else {
                 if (generatePdf) {
                     pdfPath = new com.raks.raksanalyzer.generator.pdf.PdfGenerator(config).generate(result);
                 }
                 if (generateWord) {
                     wordPath = new com.raks.raksanalyzer.generator.word.WordGenerator().generate(result);
                 }
                 if (generateExcel) {
                     excelPath = new com.raks.raksanalyzer.generator.excel.ExcelGenerator().generate(result);
                 }
            }
            response.put("status", "success");
            response.put("analysisId", result.getAnalysisId());
            response.put("reportsPath", outputPath.toString());
            if (excelPath != null) {
                String filename = excelPath.getFileName().toString();
                response.put("excelPath", "download/" + filename);
                response.put("excelFilename", filename);
            } else {
                response.put("excelPath", null);
            }
            if (wordPath != null) {
                String filename = wordPath.getFileName().toString();
                response.put("wordPath", "download/" + filename);
                response.put("wordFilename", filename);
            } else {
                response.put("wordPath", null);
            }
            if (pdfPath != null) {
                String filename = pdfPath.getFileName().toString();
                response.put("pdfPath", "download/" + filename);
                response.put("pdfFilename", filename);
            } else {
                response.put("pdfPath", null);
            }
            response.put("extractedPath", extractedPath); 
            response.put("uploadId", uploadId); 
        } catch (Exception e) {
            logger.error("RaksAnalyzer invocation failed", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            if (extractedPath != null) {
                 try { FileExtractionUtil.cleanupTempDirectory(uploadId, workingDir); } catch (Exception ex) {}
            }
        }
        return response;
    }
    private static String detectInputSourceType(String inputPath) {
        String lowerPath = inputPath.toLowerCase();
        if (lowerPath.endsWith(".zip")) return "zip";
        if (lowerPath.endsWith(".ear")) return "ear";
        if (lowerPath.endsWith(".jar")) return "jar";
        return "folder";
    }
}
