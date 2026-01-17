package com.raks.aegis.engine;
import com.raks.aegis.checks.AbstractCheck;
import com.raks.aegis.checks.CheckFactory;
import com.raks.aegis.model.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ValidationEngine {
    private final List<Rule> rules;
    private final Path projectRoot;

    public ValidationEngine(List<Rule> rules, Path projectRoot) {
        this.rules = rules;
        this.projectRoot = projectRoot;
    }

    public ValidationReport validate() {
        if (!Files.exists(projectRoot)) {
            throw new IllegalArgumentException("Project root does not exist: " + projectRoot);
        }
        ValidationReport report = new ValidationReport();
        report.projectPath = projectRoot.toString();
        for (Rule rule : rules) {
            if (!rule.isEnabled()) {
                report.addSkipped(rule.getId(), rule.getName());
                continue;
            }
            List<CheckResult> results = new ArrayList<>();
            boolean rulePassed = true;
            for (Check check : rule.getChecks()) {
                try {
                    check.setRuleId(rule.getId());
                    check.setRule(rule);  // NEW: Link check to its parent rule
                    AbstractCheck validator = CheckFactory.create(check);
                    CheckResult result = validator.execute(projectRoot, check);
                    results.add(result);
                    if (!result.passed)
                        rulePassed = false;
                } catch (Exception e) {
                    CheckResult errorResult = CheckResult.fail(rule.getId(),
                            check.getDescription() != null ? check.getDescription() : check.getType(),
                            "Execution error: " + e.getMessage());
                    results.add(errorResult);
                    rulePassed = false;
                }
            }
            String configSummary = summarizeRule(rule);
            if (rulePassed) {
                report.addPassed(rule.getId(), rule.getName(), rule.getSeverity(), results, configSummary, rule.getScope());
            } else {
                report.addFailed(rule.getId(), rule.getName(), rule.getSeverity(), results, configSummary, rule.getScope());
            }
        }
        return report;
    }

    private String summarizeRule(Rule rule) {
        if (rule.getChecks() == null || rule.getChecks().isEmpty()) return "N/A";
        List<String> checkSummaries = new ArrayList<>();
        for (Check check : rule.getChecks()) {
            String summary = getCheckConfigString(check.getParams());
            if (!summary.isEmpty()) {
                checkSummaries.add(summary);
            }
        }
        return checkSummaries.isEmpty() ? "N/A" : String.join(" ; ", checkSummaries);
    }

    private String getCheckConfigString(java.util.Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "";
        try {
            org.yaml.snakeyaml.DumperOptions options = new org.yaml.snakeyaml.DumperOptions();
            options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setIndent(2);
            options.setWidth(120); // Prevent early wrapping
            
            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(options);
            return yaml.dump(params).trim();
        } catch (Exception e) {
            return "Error generating config view: " + e.getMessage();
        }
    }
}
