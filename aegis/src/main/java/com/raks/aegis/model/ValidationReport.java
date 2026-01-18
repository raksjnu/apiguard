package com.raks.aegis.model;
import java.util.ArrayList;
import java.util.List;
public class ValidationReport {
    public String projectPath;
    public List<RuleResult> passed = new ArrayList<>();
    public List<RuleResult> failed = new ArrayList<>();
    public List<String> skipped = new ArrayList<>();
    public List<String> notApplicable = new ArrayList<>();  // NEW: Rules not applicable to this project type
    public void addPassed(String id, String name, String severity, List<CheckResult> checks, String ruleConfig, String scope) {
        passed.add(new RuleResult(id, name, severity, true, checks, ruleConfig, scope));
    }
    public void addFailed(String id, String name, String severity, List<CheckResult> checks, String ruleConfig, String scope) {
        failed.add(new RuleResult(id, name, severity, false, checks, ruleConfig, scope));
    }
    public void addSkipped(String id, String name) {
        skipped.add(id + ": " + name);
    }
    public void addNotApplicable(String id, String name, String severity) {
        notApplicable.add(id + ": " + name);
    }
    public boolean hasFailures() {
        return !failed.isEmpty();
    }

    public java.util.Map<String, String> labels = new java.util.HashMap<>();
    {
        labels.put("PASS", "PASS");
        labels.put("FAIL", "FAIL");
        labels.put("WARN", "WARN");
    }
    public void setLabels(java.util.Map<String, String> labels) {
        if (labels != null) {
            this.labels.putAll(labels);
        }
    }
    public static class RuleResult {
        public String id, name, severity, ruleConfig, scope;
        public String displayId; 
        public boolean passed;
        public List<CheckResult> checks;
        public RuleResult(String id, String name, String severity, boolean passed, List<CheckResult> checks, String ruleConfig, String scope) {
            this.id = id;
            this.displayId = id; 
            this.name = name;
            this.severity = severity;
            this.passed = passed;
            this.checks = checks;
            this.ruleConfig = ruleConfig;
            this.scope = scope;
        }
    }
}
