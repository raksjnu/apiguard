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
    public FileSchema buildFileSchema(File csvFile) throws IOException {
        List<String> headers = extractHeaders(csvFile);
        return new FileSchema(csvFile.getName(), headers);
    }
    
    /**
     * Discover all files and build schemas
     */
    public Map<String, FileSchema> discoverAllSchemas(String directory) {
        Map<String, FileSchema> schemas = new LinkedHashMap<>();
        List<File> csvFiles = discoverFiles(directory);
        
        for (File file : csvFiles) {
            try {
                FileSchema schema = buildFileSchema(file);
                schemas.put(file.getName(), schema);
            } catch (IOException e) {
                System.err.println("Error processing file " + file.getName() + ": " + e.getMessage());
            }
        }
        
        return schemas;
    }
    
    /**
     * Represents the schema of a CSV file
     */
    public static class FileSchema {
        private final String fileName;
        private final List<String> headers;
        
        public FileSchema(String fileName, List<String> headers) {
            this.fileName = fileName;
            this.headers = headers;
        }
        
        public String getFileName() {
            return fileName;
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
