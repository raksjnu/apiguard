package com.raks.muleguard.engine;

import com.raks.muleguard.checks.AbstractCheck;
import com.raks.muleguard.checks.CheckFactory;
import com.raks.muleguard.model.*;

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

            if (rulePassed) {
                report.addPassed(rule.getId(), rule.getName(), rule.getSeverity(), results);
            } else {
                report.addFailed(rule.getId(), rule.getName(), rule.getSeverity(), results);
            }
        }

        return report;
    }
}