package com.raks.aegis.wrapper;

import com.raks.aegis.AegisMain;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AegisInvoker {
    private static final Logger logger = LoggerFactory.getLogger(AegisInvoker.class);

    public static Map<String, Object> validate(String projectPath, String customRulesPath, String displayName,
            String reportDirName) {
        logger.info("Invoking Aegis Validation...");
        logger.info("Project Path: {}", projectPath);
        logger.info("Custom Rules Path: {}", customRulesPath);
        logger.info("Display Name: {}", displayName);
        logger.info("Report Dir Name: {}", reportDirName);

        // List files in project path before validation to verify extraction
        try (Stream<Path> paths = Files.walk(Paths.get(projectPath))) {
            logger.info("--- File System State (Pre-Validation) ---");
            paths.forEach(p -> logger.info("File: {}", p.toAbsolutePath()));
            logger.info("------------------------------------------");
        } catch (Exception e) {
            logger.error("Failed to list files in project path: {}", e.getMessage());
        }

        Map<String, Object> results = AegisMain.validateAndReturnResults(projectPath, customRulesPath, displayName, reportDirName);
        
        logger.info("Aegis Validation Result Status: {}", results.get("status"));
        
        // List files in report directory after validation to verify generation
        try {
             Path reportRoot = Paths.get(projectPath).resolve(reportDirName);
             if (Files.exists(reportRoot)) {
                 logger.info("Report Directory Exists: {}", reportRoot.toAbsolutePath());
                 try (Stream<Path> paths = Files.walk(reportRoot)) {
                    logger.info("--- Report Directory Content ---");
                    paths.forEach(p -> logger.info("Report File: {}", p.toAbsolutePath()));
                    logger.info("--------------------------------");
                 }
             } else {
                 logger.error("Report Directory DOES NOT EXIST at: {}", reportRoot.toAbsolutePath());
             }
        } catch (Exception e) {
             logger.error("Failed to inspect report directory: {}", e.getMessage());
        }
        
        return results;
    }
}
