package com.raks.muleguard.model;

public class CheckResult {
    public final String ruleId;
    public final String checkDescription;
    public final boolean passed;
    public final String message;

    public CheckResult(String ruleId, String checkDescription, boolean passed, String message) {
        this.ruleId = ruleId;
        this.checkDescription = checkDescription;
        this.passed = passed;
        this.message = message;
    }

    public static CheckResult pass(String ruleId, String description, String message) {
        return new CheckResult(ruleId, description, true, message);
    }

    public static CheckResult fail(String ruleId, String description, String message) {
        return new CheckResult(ruleId, description, false, message);
    }
}