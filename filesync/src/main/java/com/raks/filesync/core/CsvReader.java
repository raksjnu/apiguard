package com.raks.filesync.core;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Reads CSV files and provides data access
 */
public class CsvReader {
    
    /**
     * Read all rows from a CSV file
     * @return List of rows, where each row is a Map of column_name -> value
     */
    public List<Map<String, String>> readCsv(String filePath) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        
        try (com.opencsv.CSVReader reader = new com.opencsv.CSVReader(new FileReader(filePath))) {
            // Read header
            String[] headers = reader.readNext();
            if (headers == null) {
                return rows; // Empty file
            }
            
            // Read data rows
            String[] line;
            while ((line = reader.readNext()) != null) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length && i < line.length; i++) {
                    row.put(headers[i], line[i]);
                }
                rows.add(row);
            }
        } catch (CsvException e) {
            throw new IOException("Error reading CSV file: " + e.getMessage(), e);
        }
        
        return rows;
    }
    
    /**
     * Read CSV with custom delimiter
     */
    public List<Map<String, String>> readCsv(String filePath, char delimiter) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        
        try (com.opencsv.CSVReader reader = new com.opencsv.CSVReaderBuilder(new FileReader(filePath))
                .withCSVParser(new com.opencsv.CSVParserBuilder().withSeparator(delimiter).build())
                .build()) {
            String[] headers = reader.readNext();
            if (headers == null) {
                return rows;
            }
            
            String[] line;
            while ((line = reader.readNext()) != null) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length && i < line.length; i++) {
                    row.put(headers[i], line[i]);
                }
                rows.add(row);
            }
        } catch (CsvException e) {
            throw new IOException("Error reading CSV file: " + e.getMessage(), e);
        }
        
        return rows;
    }
}
