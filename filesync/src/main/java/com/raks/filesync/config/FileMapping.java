package com.raks.filesync.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a mapping between a source CSV file and a target CSV file
 */
public class FileMapping {
    private String sourceFile;
    private String targetFile;
    private List<FieldMapping> fieldMappings;
    private Integer sequenceNumber;

    public FileMapping() {
        this.fieldMappings = new ArrayList<>();
    }

    public FileMapping(String sourceFile, String targetFile) {
        this.sourceFile = sourceFile;
        this.targetFile = targetFile;
        this.fieldMappings = new ArrayList<>();
    }

    // Getters and Setters
    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public String getTargetFile() {
        return targetFile;
    }

    public void setTargetFile(String targetFile) {
        this.targetFile = targetFile;
    }

    public List<FieldMapping> getFieldMappings() {
        return fieldMappings;
    }

    public void setFieldMappings(List<FieldMapping> fieldMappings) {
        this.fieldMappings = fieldMappings;
    }

    public void addFieldMapping(FieldMapping fieldMapping) {
        this.fieldMappings.add(fieldMapping);
    }

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Integer sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public String toString() {
        return "FileMapping{" +
                "sourceFile='" + sourceFile + '\'' +
                ", targetFile='" + targetFile + '\'' +
                ", fieldMappings=" + fieldMappings.size() +
                '}';
    }
}
