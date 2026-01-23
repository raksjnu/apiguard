package com.raks.aegis.model;

import java.util.ArrayList;

public class CheckResult {
    public final String ruleId;
    public final String checkDescription;
    public final boolean passed;
    public final String message;
    public final String checkedFiles; 
    public final String foundItems;
    public final String matchingFiles;
    public final java.util.List<String> propertyResolutions;
    public final java.util.Set<java.nio.file.Path> matchedPaths; 

    public CheckResult(String ruleId, String checkDescription, boolean passed, String message, 
                        String checkedFiles, String foundItems, String matchingFiles, 
                        java.util.List<String> propertyResolutions,
                        java.util.Set<java.nio.file.Path> matchedPaths) {
        this.ruleId = ruleId;
        this.checkDescription = checkDescription;
        this.passed = passed;
        this.message = message;
        this.checkedFiles = checkedFiles != null ? checkedFiles : "";
        this.foundItems = foundItems != null ? foundItems : "";
        this.matchingFiles = matchingFiles != null ? matchingFiles : "";
        this.propertyResolutions = propertyResolutions != null ? propertyResolutions : new ArrayList<>();
        this.matchedPaths = matchedPaths != null ? matchedPaths : new java.util.HashSet<>();
    }

    public CheckResult(String ruleId, String checkDescription, boolean passed, String message, 
                        String checkedFiles, String foundItems, String matchingFiles, 
                        java.util.List<String> propertyResolutions) {
        this(ruleId, checkDescription, passed, message, checkedFiles, foundItems, matchingFiles, propertyResolutions, null);
    }

    public CheckResult(String ruleId, String checkDescription, boolean passed, String message, 
                        String checkedFiles, String foundItems, String matchingFiles) {
        this(ruleId, checkDescription, passed, message, checkedFiles, foundItems, matchingFiles, null, null);
    }

    public static CheckResult pass(String ruleId, String description, String message) {
        return new CheckResult(ruleId, description, true, message, null, null, null, null, null);
    }

    public static CheckResult pass(String ruleId, String description, String message, String checkedFiles) {
        return new CheckResult(ruleId, description, true, message, checkedFiles, null, null, null, null);
    }

    public static CheckResult pass(String ruleId, String description, String message, 
                                   String checkedFiles, String matchingFiles) {
        return new CheckResult(ruleId, description, true, message, checkedFiles, null, matchingFiles, null, null);
    }

    public static CheckResult pass(String ruleId, String description, String message, 
                                   String checkedFiles, String matchingFiles, 
                                   java.util.List<String> propertyResolutions) {
        return new CheckResult(ruleId, description, true, message, checkedFiles, null, matchingFiles, propertyResolutions, null);
    }

    public static CheckResult pass(String ruleId, String description, String message, 
                                   String checkedFiles, String foundItems, String matchingFiles, 
                                   java.util.List<String> propertyResolutions) {
        return new CheckResult(ruleId, description, true, message, checkedFiles, foundItems, matchingFiles, propertyResolutions, null);
    }

    public static CheckResult pass(String ruleId, String description, String message, 
                                   String checkedFiles, String foundItems, String matchingFiles, 
                                   java.util.List<String> propertyResolutions,
                                   java.util.Set<java.nio.file.Path> matchedPaths) {
        return new CheckResult(ruleId, description, true, message, checkedFiles, foundItems, matchingFiles, propertyResolutions, matchedPaths);
    }

    public static CheckResult fail(String ruleId, String description, String message) {
        return new CheckResult(ruleId, description, false, message, null, null, null, null, null);
    }

    public static CheckResult fail(String ruleId, String description, String message, String checkedFiles) {
        return new CheckResult(ruleId, description, false, message, checkedFiles, null, null, null, null);
    }

    public static CheckResult fail(String ruleId, String description, String message, 
                                   String checkedFiles, String foundItems) {
        return new CheckResult(ruleId, description, false, message, checkedFiles, foundItems, null, null, null);
    }

    public static CheckResult fail(String ruleId, String description, String message, 
                                   String checkedFiles, String foundItems, String matchingFiles) {
        return new CheckResult(ruleId, description, false, message, checkedFiles, foundItems, matchingFiles, null, null);
    }

    public static CheckResult fail(String ruleId, String description, String message, 
                                   String checkedFiles, String foundItems, String matchingFiles, 
                                   java.util.List<String> propertyResolutions) {
        return new CheckResult(ruleId, description, false, message, checkedFiles, foundItems, matchingFiles, propertyResolutions, null);
    }

    public static CheckResult fail(String ruleId, String description, String message, 
                                   String checkedFiles, String foundItems, String matchingFiles, 
                                   java.util.List<String> propertyResolutions,
                                   java.util.Set<java.nio.file.Path> matchedPaths) {
        return new CheckResult(ruleId, description, false, message, checkedFiles, foundItems, matchingFiles, propertyResolutions, matchedPaths);
    }
}

