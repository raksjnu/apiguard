package com.raks.aegis.model;

/**
 * Defines a project type with its description and detection criteria.
 * Examples: CODE, CONFIG, API, INTEGRATION
 */
public class ProjectTypeDefinition {
    private String description;
    private DetectionCriteria detectionCriteria;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public DetectionCriteria getDetectionCriteria() {
        return detectionCriteria;
    }

    public void setDetectionCriteria(DetectionCriteria detectionCriteria) {
        this.detectionCriteria = detectionCriteria;
    }
}
