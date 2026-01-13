package com.raks.filesync.core;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Discovers CSV files and extracts their schema (headers)
 */
public class CsvDiscovery {
    
    /**
     * Discover all CSV files in a directory
     */
    public List<File> discoverFiles(String directory) {
        List<File> csvFiles = new ArrayList<>();
        File dir = new File(directory);
        
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Directory does not exist: " + directory);
            return csvFiles;
        }
        
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".csv"));
        if (files != null) {
            csvFiles.addAll(Arrays.asList(files));
        }
        
        return csvFiles;
    }
    
    /**
     * Extract headers from a CSV file
     */
    public List<String> extractHeaders(File csvFile) throws IOException {
        try (CSVReader reader = new CSVReader(new FileReader(csvFile))) {
            String[] headers = reader.readNext();
            if (headers != null) {
                return Arrays.asList(headers);
            }
        } catch (CsvException e) {
            throw new IOException("Error reading CSV headers: " + e.getMessage(), e);
        }
        return new ArrayList<>();
    }
    
    /**
     * Build a complete schema for a CSV file
     */
    private FileSchema buildFileSchema(File file) throws IOException {
        List<String> headers = extractHeaders(file);
        return new FileSchema(file.getName(), file.getAbsolutePath(), headers);
    }
    
    /**
     * Discover all CSV files in a directory and extract their schemas
     */
    public Map<String, FileSchema> discoverAllSchemas(String directoryPath) {
        Map<String, FileSchema> schemas = new LinkedHashMap<>();
        File directory = new File(directoryPath);
        
        if (!directory.exists() || !directory.isDirectory()) {
            return schemas;
        }
        
        // Recursively scan for CSV files
        scanDirectoryRecursive(directory, schemas);
        
        return schemas;
    }
    
    private void scanDirectoryRecursive(File directory, Map<String, FileSchema> schemas) {
        File[] files = directory.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                // Recursively scan subdirectories
                scanDirectoryRecursive(file, schemas);
            } else if (file.getName().toLowerCase().endsWith(".csv") && 
                       !file.getName().toLowerCase().startsWith("mapping")) {
                // Process CSV files (but not mapping files)
                try {
                    FileSchema schema = discoverSchema(file.getAbsolutePath());
                    schemas.put(file.getName(), schema);
                } catch (IOException e) {
                    System.err.println("Error reading " + file.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Discovers the schema for a single CSV file given its path.
     * This method is introduced to support the recursive scanning logic.
     */
    public FileSchema discoverSchema(String filePath) throws IOException {
        File csvFile = new File(filePath);
        return buildFileSchema(csvFile);
    }
    
    /**
     * Represents the schema of a CSV file
     */
    public static class FileSchema {
        private final String fileName;
        private final String filePath;
        private final List<String> headers;
        
        public FileSchema(String fileName, String filePath, List<String> headers) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.headers = headers;
        }
        
        public String getFileName() {
            return fileName;
        }

        public String getFilePath() {
            return filePath;
        }
        
        public List<String> getHeaders() {
            return headers;
        }
        
        @Override
        public String toString() {
            return "FileSchema{" +
                    "fileName='" + fileName + '\'' +
                    ", headers=" + headers +
                    '}';
        }
    }
}
