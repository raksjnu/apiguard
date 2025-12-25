package com.raks.raksanalyzer.analyzer;

import com.raks.raksanalyzer.analyzer.mule.MuleAnalyzer;
import com.raks.raksanalyzer.analyzer.tibco.TibcoAnalyzer;
import com.raks.raksanalyzer.core.utils.FileUtils;
import com.raks.raksanalyzer.domain.enums.ProjectType;
import com.raks.raksanalyzer.domain.model.AnalysisRequest;
import com.raks.raksanalyzer.domain.model.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Factory for creating and executing project analyzers.
 * 
 * Automatically detects project type if not specified.
 * Routes to appropriate analyzer based on project type.
 */
public class AnalyzerFactory {
    private static final Logger logger = LoggerFactory.getLogger(AnalyzerFactory.class);
    
    /**
     * Analyze project based on request.
     */
    public static AnalysisResult analyze(AnalysisRequest request) {
        logger.info("Starting analysis for project type: {}", request.getProjectTechnologyType());
        
        Path projectPath = Paths.get(request.getInputPath());
        List<String> environments = request.getSelectedEnvironments();
        
        String configPathStr = request.getConfigFilePath();
        Path configFilePath = configPathStr != null ? Paths.get(configPathStr) : null;
        
        AnalysisResult result;
        
        switch (request.getProjectTechnologyType()) {
            case MULE:
                MuleAnalyzer muleAnalyzer = new MuleAnalyzer(projectPath, environments);
                result = muleAnalyzer.analyze();
                break;
                
            case TIBCO_BW5:
                TibcoAnalyzer tibcoAnalyzer = new TibcoAnalyzer(projectPath, environments, configFilePath);
                result = tibcoAnalyzer.analyze();
                break;
                
            case TIBCO_BW6:
                result = createUnsupportedResult("Tibco BW6 analyzer not yet implemented");
                break;
                
            case SPRING_BOOT:
                result = createUnsupportedResult("Spring Boot analyzer not yet implemented");
                break;
                
            default:
                result = createUnsupportedResult("Unknown project type: " + request.getProjectTechnologyType());
                break;
        }
        
        if (result != null && request.getOutputDirectory() != null) {
            result.setOutputDirectory(request.getOutputDirectory());
        }
        
        return result;
    }
    
    /**
     * Auto-detect project type from directory.
     */
    public static ProjectType detectProjectType(Path projectPath) {
        logger.info("Auto-detecting project type for: {}", projectPath);
        
        if (FileUtils.isMuleProject(projectPath)) {
            logger.info("Detected Mule project");
            return ProjectType.MULE;
        }
        
        if (FileUtils.isTibcoBW5Project(projectPath)) {
            logger.info("Detected Tibco BW5 project");
            return ProjectType.TIBCO_BW5;
        }
        
        logger.warn("Could not detect project type, defaulting to Mule");
        return ProjectType.MULE;
    }
    
    /**
     * Create result for unsupported project types.
     */
    private static AnalysisResult createUnsupportedResult(String message) {
        AnalysisResult result = new AnalysisResult();
        result.setSuccess(false);
        result.setErrorMessage(message);
        result.setStartTime(java.time.LocalDateTime.now());
        result.setEndTime(java.time.LocalDateTime.now());
        return result;
    }
}
