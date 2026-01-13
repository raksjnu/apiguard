package com.raks.filesync.core;

import com.opencsv.CSVReader;
import com.raks.filesync.config.FieldMapping;
import com.raks.filesync.config.FileMapping;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Parses Mapping_*.csv files into FileMapping objects
 */
public class CsvMappingParser {
    
    public List<FileMapping> parseMappingCsv(String csvFilePath) throws IOException {
        Map<String, FileMapping> fileMappingMap = new LinkedHashMap<>();
        Map<FieldMapping, Integer> fieldSequenceMap = new HashMap<>();
        
        try (CSVReader reader = new CSVReader(new FileReader(csvFilePath))) {
            String[] headers = reader.readNext();
            if (headers == null || headers.length < 6) {
                throw new IOException("Invalid mapping CSV: missing required columns");
            }
            
            String[] row;
            while ((row = reader.readNext()) != null) {
                if (row.length < 6) continue;
                
                String seqStr = row[0].trim();
                String sourceFile = row[1].trim();
                String sourceField = row[2].trim();
                String targetFile = row[3].trim();
                String targetField = row[4].trim();
                String ruleType = row[5].trim();
                
                Integer sequence = seqStr.isEmpty() ? null : Integer.parseInt(seqStr);
                
                String key = sourceFile + "->" + targetFile;
                FileMapping fileMapping = fileMappingMap.get(key);
                
                if (fileMapping == null) {
                    fileMapping = new FileMapping(sourceFile, targetFile);
                    // Use the first sequence number encountered for this file pair
                    if (sequence != null) {
                        fileMapping.setSequenceNumber(sequence);
                    }
                    fileMappingMap.put(key, fileMapping);
                }
                
                FieldMapping fieldMapping = new FieldMapping(sourceField, targetField);
                fieldMapping.setRuleType(ruleType);
                fieldMapping.setSequenceNumber(sequence); // Store sequence per field
                if (row.length > 6) fieldMapping.setRule1(row[6].trim());
                if (row.length > 7) fieldMapping.setRule2(row[7].trim());
                if (row.length > 8) fieldMapping.setRule3(row[8].trim());
                if (row.length > 9) fieldMapping.setRule4(row[9].trim());
                if (row.length > 10) fieldMapping.setRule5(row[10].trim());
                
                // Store the sequence for this specific field mapping
                if (sequence != null) {
                    fieldSequenceMap.put(fieldMapping, sequence);
                }
                
                fileMapping.addFieldMapping(fieldMapping);
            }
        } catch (com.opencsv.exceptions.CsvValidationException e) {
            throw new IOException("CSV validation error: " + e.getMessage(), e);
        }
        
        return fileMappingMap.values().stream()
                .sorted(Comparator.comparing(fm -> fm.getSequenceNumber() != null ? fm.getSequenceNumber() : Integer.MAX_VALUE))
                .collect(Collectors.toList());
    }
    
    public List<File> findMappingFiles(String directory) {
        List<File> mappingFiles = new ArrayList<>();
        File dir = new File(directory);
        
        if (!dir.exists() || !dir.isDirectory()) {
            return mappingFiles;
        }
        
        File[] files = dir.listFiles((d, name) -> 
            name.toLowerCase().startsWith("mapping") && name.toLowerCase().endsWith(".csv"));
        
        if (files != null) {
            mappingFiles.addAll(Arrays.asList(files));
        }
        
        return mappingFiles;
    }
}
