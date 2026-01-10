package com.raks.filesync.core;

import com.opencsv.CSVWriter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Writes data to CSV files
 */
public class CsvWriter {
    
    /**
     * Write rows to a CSV file
     * @param filePath Target file path
     * @param headers Column headers
     * @param rows Data rows (each row is a Map of column_name -> value)
     */
    public void writeCsv(String filePath, List<String> headers, List<Map<String, String>> rows) throws IOException {
        try (CSVWriter writer = new CSVWriter(new FileWriter(filePath))) {
            // Write headers
            writer.writeNext(headers.toArray(new String[0]));
            
            // Write data rows
            for (Map<String, String> row : rows) {
                String[] rowData = new String[headers.size()];
                for (int i = 0; i < headers.size(); i++) {
                    String header = headers.get(i);
                    rowData[i] = row.getOrDefault(header, "");
                }
                writer.writeNext(rowData);
            }
        }
    }
    
    /**
     * Append rows to an existing CSV file
     */
    public void appendCsv(String filePath, List<String> headers, List<Map<String, String>> rows) throws IOException {
        try (CSVWriter writer = new CSVWriter(new FileWriter(filePath, true))) {
            for (Map<String, String> row : rows) {
                String[] rowData = new String[headers.size()];
                for (int i = 0; i < headers.size(); i++) {
                    String header = headers.get(i);
                    rowData[i] = row.getOrDefault(header, "");
                }
                writer.writeNext(rowData);
            }
        }
    }
}
