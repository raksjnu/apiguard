package com.raks.muleguard.model;
import java.util.ArrayList;
import java.util.List;
public class ValidationReport {
    public String projectPath;
    public List<RuleResult> passed = new ArrayList<>();
    public List<RuleResult> failed = new ArrayList<>();
    public List<String> skipped = new ArrayList<>();
    public void addPassed(String id, String name, String severity, List<CheckResult> checks, String ruleConfig) {
        passed.add(new RuleResult(id, name, severity, true, checks, ruleConfig));
    }
    public void addFailed(String id, String name, String severity, List<CheckResult> checks, String ruleConfig) {
        failed.add(new RuleResult(id, name, severity, false, checks, ruleConfig));
    }
    public void addSkipped(String id, String name) {
        skipped.add(id + ": " + name);
    }
    public boolean hasFailures() {
        return !failed.isEmpty();
    }
    public static class RuleResult {
        public String id, name, severity, ruleConfig;
        public String displayId; // New field for dynamic sequence (e.g., "001")
        public boolean passed;
        public List<CheckResult> checks;
        public RuleResult(String id, String name, String severity, boolean passed, List<CheckResult> checks, String ruleConfig) {
            this.id = id;
            this.displayId = id; // Default to ID if not set
            this.name = name;
            this.severity = severity;
            this.passed = passed;
            this.checks = checks;
            this.ruleConfig = ruleConfig;
        }
    }
}
