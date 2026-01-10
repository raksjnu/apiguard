package com.raks.filesync.config;

/**
 * Represents a mapping between a source field and a target field
 */
public class FieldMapping {
    private String sourceField;
    private String targetField;
    private String transformation;
    private String description;

    public FieldMapping() {
        this.transformation = "direct";
    }

    public FieldMapping(String sourceField, String targetField) {
        this.sourceField = sourceField;
        this.targetField = targetField;
        this.transformation = "direct";
    }

    public FieldMapping(String sourceField, String targetField, String transformation) {
        this.sourceField = sourceField;
        this.targetField = targetField;
        this.transformation = transformation;
    }

    // Getters and Setters
    public String getSourceField() {
        return sourceField;
    }

    public void setSourceField(String sourceField) {
        this.sourceField = sourceField;
    }

    public String getTargetField() {
        return targetField;
    }

    public void setTargetField(String targetField) {
        this.targetField = targetField;
    }

    public String getTransformation() {
        return transformation;
    }

    public void setTransformation(String transformation) {
        this.transformation = transformation;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "FieldMapping{" +
                "sourceField='" + sourceField + '\'' +
                ", targetField='" + targetField + '\'' +
                ", transformation='" + transformation + '\'' +
                '}';
    }
}
