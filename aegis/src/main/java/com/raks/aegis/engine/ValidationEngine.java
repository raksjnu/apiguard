package com.raks.aegis.engine;
import com.raks.aegis.checks.AbstractCheck;
import com.raks.aegis.checks.CheckFactory;
import com.raks.aegis.model.*;
import com.raks.aegis.util.ProjectTypeClassifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ValidationEngine {
    private final List<Rule> rules;
    private final Path projectRoot;
    private final ProjectTypeClassifier projectTypeClassifier;
    private final List<String> ignoredFileNames;
    private final List<String> ignoredFilePrefixes;

    public ValidationEngine(List<Rule> rules, Path projectRoot) {
        this(rules, projectRoot, null, null, null);
    }

    public ValidationEngine(List<Rule> rules, Path projectRoot, ProjectTypeClassifier projectTypeClassifier) {
        this(rules, projectRoot, projectTypeClassifier, null, null);
    }

    public ValidationEngine(List<Rule> rules, Path projectRoot, ProjectTypeClassifier projectTypeClassifier, 
                            List<String> ignoredFileNames, List<String> ignoredFilePrefixes) {
        this.rules = rules;
        this.projectRoot = projectRoot;
        this.projectTypeClassifier = projectTypeClassifier;
        this.ignoredFileNames = ignoredFileNames != null ? ignoredFileNames : new ArrayList<>();
        this.ignoredFilePrefixes = ignoredFilePrefixes != null ? ignoredFilePrefixes : new ArrayList<>();
    }

    public ValidationReport validate() {
        if (!Files.exists(projectRoot)) {
            throw new IllegalArgumentException("Project root does not exist: " + projectRoot);
        }
        
        // Classify this project (if classifier is available)
        Set<String> projectTypes = null;
        if (projectTypeClassifier != null) {
            projectTypes = projectTypeClassifier.classifyProject(projectRoot);
        }
        
        ValidationReport report = new ValidationReport();
        report.projectPath = projectRoot.toString();
        
        for (Rule rule : rules) {
            if (!rule.isEnabled()) {
                report.addSkipped(rule.getId(), rule.getName());
                continue;
            }
            
            // Check if rule applies to this project type
            if (projectTypes != null && rule.getAppliesTo() != null && !rule.getAppliesTo().isEmpty()) {
                boolean applicable = rule.getAppliesTo().stream()
                    .anyMatch(projectTypes::contains);
                
                if (!applicable) {
                    // Rule doesn't apply to this project type - skip it
                    report.addNotApplicable(rule.getId(), rule.getName(), rule.getSeverity());
                    continue;
                }
            }
            
            List<CheckResult> results = new ArrayList<>();
            boolean rulePassed = true;
            for (Check check : rule.getChecks()) {
                try {
                    check.setRuleId(rule.getId());
                    check.setRule(rule);  
                    AbstractCheck validator = CheckFactory.create(check);
                    validator.setIgnoredFiles(ignoredFileNames, ignoredFilePrefixes); // Pass global ignore config
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

        java.util.Map<String, Object> ruleMap = new java.util.LinkedHashMap<>();

        ruleMap.put("id", rule.getId());
        ruleMap.put("name", rule.getName());
        ruleMap.put("description", rule.getDescription());
        ruleMap.put("enabled", rule.isEnabled());
        ruleMap.put("severity", rule.getSeverity());

        if (rule.getSuccessMessage() != null) ruleMap.put("successMessage", rule.getSuccessMessage());
        if (rule.getErrorMessage() != null) ruleMap.put("errorMessage", rule.getErrorMessage());

        if (rule.getUseCase() != null) ruleMap.put("useCase", rule.getUseCase());
        if (rule.getRationale() != null) ruleMap.put("rationale", rule.getRationale());

        if (rule.getChecks() != null && !rule.getChecks().isEmpty()) {
            java.util.List<java.util.Map<String, Object>> checksList = new java.util.ArrayList<>();

            for (Check check : rule.getChecks()) {
                java.util.Map<String, Object> checkData = new java.util.LinkedHashMap<>();
                checkData.put("type", check.getType());

                if (check.getParams() != null) {
                    java.util.Map<String, Object> cleanParams = new java.util.LinkedHashMap<>(check.getParams());
                    cleanParams.remove("environments"); 
                    checkData.put("params", cleanParams);
                }

                checksList.add(checkData);
            }
            ruleMap.put("checks", checksList);
        }

        return getYamlString(ruleMap);
    }

    private String getYamlString(Object data) {
        try {
            org.yaml.snakeyaml.DumperOptions options = new org.yaml.snakeyaml.DumperOptions();
            options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setIndent(2);
            options.setWidth(120); 

            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(options);
            return yaml.dump(data).trim();
        } catch (Exception e) {
            return "Error generating config view: " + e.getMessage();
        }
    }
}
