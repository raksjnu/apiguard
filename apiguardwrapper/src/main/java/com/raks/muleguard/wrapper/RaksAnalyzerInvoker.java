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
import com.raks.raksanalyzer.provider.GitProvider;
import com.raks.raksanalyzer.provider.GitHubProvider;
import com.raks.raksanalyzer.provider.GitLabProvider;
public class RaksAnalyzerInvoker {
    private static final Logger logger = LoggerFactory.getLogger(RaksAnalyzerInvoker.class);
    private static final Map<String, AnalysisResult> results = new java.util.concurrent.ConcurrentHashMap<>();

    public static Map<String, Object> analyze(String inputPath, String configPath, String outputDir, String workingDir, 
                                               boolean generateExcel, boolean generateWord, boolean generatePdf, String uId,
                                               String inputSourceType, String gitBranch, String gitToken, String projectTypeStr) {
        String uploadId = (uId == null || uId.trim().isEmpty()) ? UUID.randomUUID().toString() : uId;
        
        AnalysisResult initialResult = new AnalysisResult();
        initialResult.setAnalysisId(uploadId);
        initialResult.setStatus("STARTED");
        initialResult.setProgressMessage("Initializing analysis...");
        initialResult.setProgressPercentage(0);
        initialResult.setStartTime(java.time.LocalDateTime.now());
        results.put(uploadId, initialResult);

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                executeAnalysisInternal(inputPath, configPath, outputDir, workingDir, generateExcel, generateWord, generatePdf, 
                                      uploadId, inputSourceType, gitBranch, gitToken, projectTypeStr);
            } catch (Exception e) {
                logger.error("Async analysis failed", e);
                AnalysisResult ar = results.get(uploadId);
                if (ar != null) {
                    ar.setSuccess(false);
                    ar.setErrorMessage(e.getMessage());
                    ar.setStatus("FAILED");
                    ar.setEndTime(java.time.LocalDateTime.now());
                }
            }
        });

        Map<String, Object> response = new HashMap<>();
        response.put("status", "STARTED");
        response.put("analysisId", uploadId);
        response.put("uploadId", uploadId);
        response.put("message", "Analysis started in background");
        return response;
    }

    private static void executeAnalysisInternal(String inputPath, String configPath, String outputDir, String workingDir, 
                                               boolean generateExcel, boolean generateWord, boolean generatePdf, String uploadId,
                                               String inputSourceType, String gitBranch, String gitToken, String projectTypeStr) {
        String actualInputPath = inputPath;
        String extractedPath = null;
        AnalysisResult currentResult = results.get(uploadId);
        
        try {
            logger.info("Starting RaksAnalyzer invocation for input: {}, SourceType: {}, RequestedType: {}", inputPath, inputSourceType, projectTypeStr);
            
            if (currentResult != null) {
                currentResult.setProgressMessage("Preparing input...");
                currentResult.setProgressPercentage(10);
            }

            if (inputSourceType == null) {
                inputSourceType = detectInputSourceType(inputPath);
            }

            if ("git".equals(inputSourceType)) {
                if (currentResult != null) {
                    currentResult.setProgressMessage("Cloning Git repository...");
                    currentResult.setProgressPercentage(15);
                }
                logger.info("Handling Git repository analysis for: {}", inputPath);
                String repoName = "repo";
                if (inputPath != null && inputPath.contains("/")) {
                    repoName = inputPath.substring(inputPath.lastIndexOf("/") + 1).replace(".git", "");
                }
                
                Path gitClonePath = Paths.get(workingDir).resolve("git").resolve(uploadId).resolve(repoName).toAbsolutePath().normalize();
                if (!java.nio.file.Files.exists(gitClonePath)) {
                    java.nio.file.Files.createDirectories(gitClonePath);
                }

                logger.info("Cloning Git repository to: {}", gitClonePath);
                String providerType = (inputPath != null && inputPath.contains("github.com")) ? "github" : "gitlab";
                
                GitProvider provider;
                if ("github".equals(providerType)) {
                    provider = new com.raks.raksanalyzer.provider.GitHubProvider(gitToken);
                } else {
                    provider = new com.raks.raksanalyzer.provider.GitLabProvider(gitToken);
                }
                
                provider.cloneRepository(inputPath, gitClonePath.toFile(), gitBranch != null ? gitBranch : "main");
                actualInputPath = gitClonePath.toString();
                extractedPath = actualInputPath;
            } else if ("zip".equals(inputSourceType) || "jar".equals(inputSourceType) || "ear".equals(inputSourceType)) {
                if (currentResult != null) {
                    currentResult.setProgressMessage("Extracting archive...");
                    currentResult.setProgressPercentage(15);
                }
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
            
            // Determine project type based on request or input source
            ProjectType projectType = null;
            if (projectTypeStr != null && !projectTypeStr.isEmpty()) {
                try {
                    projectType = ProjectType.valueOf(projectTypeStr.toUpperCase());
                    logger.info("Using requested project type: {}", projectType);
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid project type requested: {}, will attempt detection", projectTypeStr);
                }
            }
            
            if (projectType == null) {
                projectType = "ear".equals(inputSourceType) ? ProjectType.TIBCO_BW5 : ProjectType.MULE;
                logger.info("Detected project type based on input source: {}", projectType);
            }

            if (currentResult != null) {
                currentResult.setProgressMessage("Discovering " + projectType.getDisplayName() + " projects...");
                currentResult.setProgressPercentage(25);
            }

            List<DiscoveredProject> projects = ProjectDiscovery.findProjects(projectRoot, projectType);
            
            // Fallback detection ONLY if user didn't specify a type
            if (projects.isEmpty() && (projectTypeStr == null || projectTypeStr.isEmpty())) {
                if (projectType == ProjectType.MULE) {
                     logger.info("No Mule project found, checking for Tibco...");
                     projects = ProjectDiscovery.findProjects(projectRoot, ProjectType.TIBCO_BW5);
                     if (!projects.isEmpty()) {
                         projectType = ProjectType.TIBCO_BW5;
                         logger.info("Tibco project found.");
                     }
                }
            }
            
            if (projects.isEmpty()) {
                String errorMsg = "The selected " + ("git".equals(inputSourceType) ? "Git repository" : "path") + 
                                 " does not contain any " + projectType.getDisplayName() + " project artifacts.";
                throw new RuntimeException(errorMsg);
            }
            
            DiscoveredProject project = projects.get(0); 
            logger.info("Analyzing project: {}", project.getProjectName());
            
            if (currentResult != null) {
                currentResult.setProgressMessage("Analyzing project structure...");
                currentResult.setProgressPercentage(40);
            }

            AnalysisRequest request = new AnalysisRequest();
            request.setInputPath(project.getProjectPath().toString());
            request.setProjectTechnologyType(projectType);
            request.setInputSourceType(inputSourceType);
            request.setGitBranch(gitBranch);
            request.setGitToken(gitToken);
            
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
            
            // Set source URL for reporting (e.g., Git URL or archive path)
            result.setSourceUrl(inputPath);
            
            if (currentResult != null) {
                currentResult.setProgressMessage("Generating documents...");
                currentResult.setProgressPercentage(80);
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
            
            // Update the stored result with success info
            if (currentResult != null) {
                currentResult.setSuccess(true);
                currentResult.setStatus("COMPLETED");
                currentResult.setProgressMessage("Analysis completed successfully!");
                currentResult.setProgressPercentage(100);
                currentResult.setEndTime(java.time.LocalDateTime.now());
                
                // Store paths in the result object itself if possible, or use the map logic 
                // in getAnalysisStatus to construct the legacy map response.
                // For now, we'll store paths in the result object using setters if available, 
                // otherwise we rely on the fact that we can reconstruct paths from output dir + filename convention
                if (excelPath != null) currentResult.setExcelReportPath(excelPath.toString());
                if (wordPath != null) currentResult.setWordDocumentPath(wordPath.toString());
                if (pdfPath != null) currentResult.setPdfDocumentPath(pdfPath.toString());
                
                // Also store extracted path for cleanup
                // We're hacking here a bit because AnalysisResult doesn't have a field for "extractedPath"
                // But typically cleanup happens in a separate scheduler or immediately if synchronous.
                // Since this is async, we might want to schedule cleanup or let the periodic cleaner handle it.
            }

        } catch (Exception e) {
            logger.error("RaksAnalyzer invocation failed", e);
            if (currentResult != null) {
                currentResult.setSuccess(false);
                currentResult.setStatus("FAILED");
                currentResult.setErrorMessage(e.getMessage());
                currentResult.setEndTime(java.time.LocalDateTime.now());
            }
            if (extractedPath != null) {
                  try { 
                      java.nio.file.Path p = java.nio.file.Paths.get(extractedPath);
                      if (java.nio.file.Files.exists(p)) {
                          com.raks.raksanalyzer.util.FileExtractionUtil.deleteRecursively(p);
                      }
                  } catch (Exception ex) {}
            }
        }
    }

    public static AnalysisResult getAnalysisStatus(String analysisId) {
        return results.get(analysisId);
    }
    private static String detectInputSourceType(String inputPath) {
        String lowerPath = inputPath.toLowerCase();
        if (lowerPath.endsWith(".zip")) return "zip";
        if (lowerPath.endsWith(".ear")) return "ear";
        if (lowerPath.endsWith(".jar")) return "jar";
        return "folder";
    }

    public static Map<String, Object> validateGitToken(String providerType, String token) {
        try {
            GitProvider provider = getProviderInstance(providerType, token);
            provider.validateCredentials();
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Credentials are valid.");
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "error");
            result.put("message", e.getMessage());
            return result;
        }
    }

    public static List<Map<String, String>> listGitRepos(String providerType, String token, String group) {
        try {
            GitProvider provider = getProviderInstance(providerType, token);
            return provider.listRepositories(group);
        } catch (Exception e) {
            logger.error("Failed to list repos", e);
            return java.util.Collections.emptyList();
        }
    }

    public static List<String> listGitBranches(String providerType, String token, String repo) {
        try {
            GitProvider provider = getProviderInstance(providerType, token);
            return provider.listBranches(repo);
        } catch (Exception e) {
            logger.error("Failed to list branches", e);
            return java.util.Collections.emptyList();
        }
    }

    private static GitProvider getProviderInstance(String type, String token) {
        if ("github".equalsIgnoreCase(type)) {
            return new com.raks.raksanalyzer.provider.GitHubProvider(token);
        } else {
            return new com.raks.raksanalyzer.provider.GitLabProvider(token);
        }
    }
}
