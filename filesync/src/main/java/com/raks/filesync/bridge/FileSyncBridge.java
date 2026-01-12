package com.raks.filesync.bridge;

import com.raks.filesync.config.ConfigLoader;
import com.raks.filesync.config.FileMapping;
import com.raks.filesync.config.MappingConfig;
import com.raks.filesync.core.CsvReader;
import com.raks.filesync.core.MappingExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class FileSyncBridge {
    private static final Logger logger = LoggerFactory.getLogger(FileSyncBridge.class);

    public static Map<String, Object> scanCsvFiles(String sourceDir) {
        logger.info("Scanning CSV files in: {}", sourceDir);
        
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        
        try {
            File dir = new File(sourceDir);
            if (!dir.exists() || !dir.isDirectory()) {
                result.put("error", "Source directory not found: " + sourceDir);
                return result;
            }
            
            File[] csvFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".csv"));
            if (csvFiles == null || csvFiles.length == 0) {
                result.put("error", "No CSV files found in directory");
                return result;
            }
            
            CsvReader reader = new CsvReader();
            
            for (File csvFile : csvFiles) {
                try {
                    List<Map<String, String>> rows = reader.readCsv(csvFile.getAbsolutePath());
                    
                    if (!rows.isEmpty()) {
                        Map<String, String> firstRow = rows.get(0);
                        List<String> headers = new ArrayList<>(firstRow.keySet());
                        
                        Map<String, Object> fileInfo = new HashMap<>();
                        fileInfo.put("name", csvFile.getName());
                        fileInfo.put("headers", headers);
                        fileInfo.put("rowCount", rows.size());
                        files.add(fileInfo);
                        
                        logger.info("Scanned: {} ({} headers, {} rows)", 
                            csvFile.getName(), headers.size(), rows.size());
                    }
                } catch (Exception e) {
                    logger.warn("Failed to scan {}: {}", csvFile.getName(), e.getMessage());
                }
            }
            
            result.put("files", files);
            result.put("fileCount", files.size());
            
        } catch (Exception e) {
            logger.error("Error scanning CSV files", e);
            result.put("error", "Scan failed: " + e.getMessage());
        }
        
        return result;
    }

    public static Map<String, Object> executeTransformation(String sessionDir, String configPath) {
        logger.info("Executing transformation for session: {}", sessionDir);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            ConfigLoader configLoader = new ConfigLoader();
            MappingConfig config = configLoader.loadConfig(configPath);
            
            config.getPaths().setSourceDirectory(sessionDir + "/source");
            config.getPaths().setTargetDirectory(sessionDir + "/output");
            
            MappingExecutor executor = new MappingExecutor();
            MappingExecutor.ExecutionResult execResult = executor.execute(config);
            
            result.put("status", execResult.hasErrors() ? "error" : "success");
            result.put("successes", execResult.getSuccesses());
            result.put("warnings", execResult.getWarnings());
            result.put("errors", execResult.getErrors());
            result.put("fileCount", execResult.getSuccesses().size());
            
            int totalRows = 0;
            for (String success : execResult.getSuccesses()) {
                if (success.contains("(") && success.contains(" rows)")) {
                    String rowsPart = success.substring(success.lastIndexOf("(") + 1, success.lastIndexOf(" rows)"));
                    try {
                        totalRows += Integer.parseInt(rowsPart);
                    } catch (NumberFormatException ignored) {}
                }
            }
            result.put("rowsProcessed", totalRows);
            
            logger.info("Transformation complete: {} files, {} rows", 
                execResult.getSuccesses().size(), totalRows);
            
        } catch (Exception e) {
            logger.error("Transformation failed", e);
            result.put("status", "error");
            result.put("error", "Transformation failed: " + e.getMessage());
        }
        
        return result;
    }

    public static Map<String, Object> updateAutocompleteHistory(Map<String, Object> config) {
        logger.info("Extracting autocomplete history from config");
        
        Set<String> fileNames = new HashSet<>();
        Set<String> fieldNames = new HashSet<>();
        
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fileMappings = 
                (List<Map<String, Object>>) config.get("fileMappings");
            
            if (fileMappings != null) {
                for (Map<String, Object> fileMapping : fileMappings) {
                    String targetFile = (String) fileMapping.get("targetFile");
                    if (targetFile != null && !targetFile.trim().isEmpty()) {
                        fileNames.add(targetFile);
                    }
                    
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> fieldMappings = 
                        (List<Map<String, Object>>) fileMapping.get("fieldMappings");
                    
                    if (fieldMappings != null) {
                        for (Map<String, Object> fieldMapping : fieldMappings) {
                            String targetField = (String) fieldMapping.get("targetField");
                            if (targetField != null && !targetField.trim().isEmpty()) {
                                fieldNames.add(targetField);
                            }
                        }
                    }
                }
            }
            
            logger.info("Extracted {} file names, {} field names", fileNames.size(), fieldNames.size());
            
        } catch (Exception e) {
            logger.error("Failed to extract autocomplete history", e);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("fileNames", new ArrayList<>(fileNames));
        result.put("fieldNames", new ArrayList<>(fieldNames));
        return result;
    }

    public static Map<String, Object> extractZip(String zipPath, String outputDir) {
        logger.info("Extracting ZIP: {} to {}", zipPath, outputDir);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            File destDir = new File(outputDir);
            if (!destDir.exists()) {
                destDir.mkdirs();
            }
            
            java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new java.io.FileInputStream(zipPath));
            java.util.zip.ZipEntry zipEntry = zis.getNextEntry();
            
            int fileCount = 0;
            while (zipEntry != null) {
                File newFile = new File(destDir, zipEntry.getName());
                
                if (zipEntry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    new File(newFile.getParent()).mkdirs();
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(newFile);
                    
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, length);
                    }
                    fos.close();
                    fileCount++;
                }
                zipEntry = zis.getNextEntry();
            }
            
            zis.closeEntry();
            zis.close();
            
            result.put("status", "success");
            result.put("fileCount", fileCount);
            
            logger.info("Extracted {} files from ZIP", fileCount);
            
        } catch (Exception e) {
            logger.error("Failed to extract ZIP", e);
            result.put("error", "Extraction failed: " + e.getMessage());
        }
        
        return result;
    }

    public static Map<String, Object> createResultsZip(String sessionDir) {
        logger.info("Creating results ZIP for session: {}", sessionDir);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            File outputDir = new File(sessionDir + "/output");
            File zipFile = new File(sessionDir + "/results.zip");
            
            if (!outputDir.exists() || !outputDir.isDirectory()) {
                result.put("error", "Output directory not found");
                return result;
            }
            
            java.io.FileOutputStream fos = new java.io.FileOutputStream(zipFile);
            java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(fos);
            
            File[] files = outputDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(file.getName());
                        zos.putNextEntry(zipEntry);
                        
                        java.io.FileInputStream fis = new java.io.FileInputStream(file);
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = fis.read(buffer)) > 0) {
                            zos.write(buffer, 0, length);
                        }
                        fis.close();
                        zos.closeEntry();
                    }
                }
            }
            
            zos.close();
            fos.close();
            
            result.put("status", "success");
            result.put("zipPath", zipFile.getAbsolutePath());
            result.put("fileCount", files != null ? files.length : 0);
            
            logger.info("Results ZIP created: {}", zipFile.getAbsolutePath());
            
        } catch (Exception e) {
            logger.error("Failed to create results ZIP", e);
            result.put("error", "ZIP creation failed: " + e.getMessage());
        }
        
        return result;
    }

    public static Map<String, Object> validateConfig(String configPath) {
        logger.info("Validating config: {}", configPath);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            ConfigLoader configLoader = new ConfigLoader();
            MappingConfig config = configLoader.loadConfig(configPath);
            
            if (config.getFileMappings() == null || config.getFileMappings().isEmpty()) {
                result.put("valid", false);
                result.put("error", "No file mappings defined");
                return result;
            }
            
            result.put("valid", true);
            result.put("fileCount", config.getFileMappings().size());
            
            int totalMappings = 0;
            for (FileMapping fm : config.getFileMappings()) {
                totalMappings += fm.getFieldMappings().size();
            }
            result.put("fieldMappingCount", totalMappings);
            
            logger.info("Config valid: {} files, {} field mappings", 
                config.getFileMappings().size(), totalMappings);
            
        } catch (Exception e) {
            logger.error("Config validation failed", e);
            result.put("valid", false);
            result.put("error", "Validation failed: " + e.getMessage());
        }
        
        return result;
    }
}
