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
        
        com.raks.muleguard.PropertyResolver globalResolver = new com.raks.muleguard.PropertyResolver(projectRoot);
        
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
                    
                    // Automatically resolve properties in all string parameters
                    resolvePropertiesInCheck(check, globalResolver);
                    
                    AbstractCheck validator = CheckFactory.create(check);
                    CheckResult result = validator.execute(projectRoot, check);
                    
                    // Support Global negativeMatch parameter
                    boolean negativeMatch = Boolean.TRUE.equals(check.getParams().get("negativeMatch"));
                    if (negativeMatch) {
                        if (result.passed) {
                            result = CheckResult.fail(result.ruleId, result.checkDescription, 
                                "Negative Match: PASS inverted to FAIL as per configuration.");
                        } else {
                            result = CheckResult.pass(result.ruleId, result.checkDescription, 
                                "Negative Match: FAIL inverted to PASS as per configuration.\nOriginal failure: " + result.message);
                        }
                    }
                    
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
                report.addPassed(rule.getId(), rule.getName(), rule.getSeverity(), results, configSummary);
            } else {
                report.addFailed(rule.getId(), rule.getName(), rule.getSeverity(), results, configSummary);
            }
        }
        return report;
    }

    @SuppressWarnings("unchecked")
    private void resolvePropertiesInCheck(Check check, com.raks.muleguard.PropertyResolver resolver) {
        if (check.getParams() == null) return;
        
        // Only resolve properties if propertyResolution is not explicitly false
        Object propRes = check.getParams().get("propertyResolution");
        if (propRes instanceof Boolean && !((Boolean)propRes)) {
            return; 
        }

        for (java.util.Map.Entry<String, Object> entry : check.getParams().entrySet()) {
            entry.setValue(resolveNested(entry.getValue(), resolver));
        }
    }

    @SuppressWarnings("unchecked")
    private Object resolveNested(Object value, com.raks.muleguard.PropertyResolver resolver) {
        if (value instanceof String) {
            return resolver.resolve((String) value);
        } else if (value instanceof java.util.List) {
            java.util.List<Object> list = (java.util.List<Object>) value;
            for (int i = 0; i < list.size(); i++) {
                list.set(i, resolveNested(list.get(i), resolver));
            }
        } else if (value instanceof java.util.Map) {
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) value;
            for (java.util.Map.Entry<String, Object> entry : map.entrySet()) {
                entry.setValue(resolveNested(entry.getValue(), resolver));
            }
        }
        return value;
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
        if (params == null) return "";
        List<String> parts = new ArrayList<>();

        if (params.containsKey("tokens")) parts.add("Tokens: " + params.get("tokens"));
        if (params.containsKey("xpathExpressions")) parts.add("XPaths: " + params.get("xpathExpressions"));
        if (params.containsKey("key")) parts.add("Property: " + params.get("key"));
        if (params.containsKey("value")) parts.add("Value: " + params.get("value"));
        if (params.containsKey("filePatterns")) parts.add("Target Files: " + params.get("filePatterns"));
        if (params.get("environments") instanceof java.util.List && !((java.util.List<?>)params.get("environments")).isEmpty()) {
            parts.add("Envs: " + params.get("environments"));
        }
        

        if (params.containsKey("validationType")) parts.add("Type: " + params.get("validationType"));
        if (params.containsKey("parent")) parts.add("Parent: " + params.get("parent"));
        if (params.containsKey("properties")) parts.add("Props: " + params.get("properties"));
        if (params.containsKey("dependencies")) parts.add("Deps: " + params.get("dependencies"));
        if (params.containsKey("plugins")) parts.add("Plugins: " + params.get("plugins"));
        

        if (params.containsKey("filePattern")) parts.add("File: " + params.get("filePattern"));
        if (params.containsKey("requiredFields")) parts.add("Fields: " + params.get("requiredFields"));
        if (params.containsKey("requiredElements")) parts.add("Elements: " + params.get("requiredElements"));
        if (params.containsKey("minVersions")) parts.add("Min Ver: " + params.get("minVersions"));


        if (params.containsKey("elementTokenPairs")) {
            Object pairs = params.get("elementTokenPairs");
            parts.add("Forbidden: " + pairs);
        }
        

        if (params.containsKey("logic")) parts.add("Logic: " + params.get("logic"));
        
        if (params.containsKey("preconditions")) {
            parts.add("Preconditions: [" + summarizeCheckList(params.get("preconditions")) + "]");
        }
        
        if (params.containsKey("onSuccess")) {
            parts.add("Checks: [" + summarizeCheckList(params.get("onSuccess")) + "]");
        } else if (params.containsKey("checkCondition")) {

             Object condObj = params.get("checkCondition");
             if (condObj instanceof java.util.Map) {
                 @SuppressWarnings("unchecked")
                 java.util.Map<String, Object> cond = (java.util.Map<String, Object>) condObj;
                 String type = (String) cond.get("type");
                 String det = "";
                 if ("NAMESPACE_EXISTS".equals(type)) det = (String) cond.get("namespace");
                 parts.add("Condition: " + type + (det.isEmpty() ? "" : "(" + det + ")"));
             }
        }

        return String.join(" | ", parts);
    }

    private String summarizeCheckList(Object listObj) {
        if (!(listObj instanceof java.util.List)) return "";
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> list = (java.util.List<java.util.Map<String, Object>>) listObj;
        List<String> items = new ArrayList<>();
        for (java.util.Map<String, Object> item : list) {
            String type = (String) item.getOrDefault("type", "CHECK");
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> subParams = (java.util.Map<String, Object>) item.get("params");
            String subConfig = getCheckConfigString(subParams);
            if (!subConfig.isEmpty()) {
                items.add(type + " {" + subConfig + "}");
            } else {
                items.add(type);
            }
        }
        return String.join(", ", items);
    }
}
