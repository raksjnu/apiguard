package com.raks.muleguard.model;

import java.util.ArrayList;
import java.util.List;

public class ValidationReport {
    public String projectPath;
    public List<RuleResult> passed = new ArrayList<>();
    public List<RuleResult> failed = new ArrayList<>();
    public List<String> skipped = new ArrayList<>();

    public void addPassed(String id, String name, String severity, List<CheckResult> checks) {
        passed.add(new RuleResult(id, name, severity, true, checks));
    }

    public void addFailed(String id, String name, String severity, List<CheckResult> checks) {
        failed.add(new RuleResult(id, name, severity, false, checks));
    }

    public void addSkipped(String id, String name) {
        skipped.add(id + ": " + name);
    }

    public boolean hasFailures() {
        return !failed.isEmpty();
    }

    public static class RuleResult {
        public String id, name, severity;
        public boolean passed;
        public List<CheckResult> checks;

        public RuleResult(String id, String name, String severity, boolean passed, List<CheckResult> checks) {
            this.id = id;
            this.name = name;
            this.severity = severity;
            this.passed = passed;
            this.checks = checks;
        }
    }
}