package com.raks.aegis.checks;

import com.raks.aegis.checks.CheckFactory;
import com.raks.aegis.model.Check;
import com.raks.aegis.model.CheckResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConditionalCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        Map<String, Object> params = check.getParams();
        String logic = (String) params.getOrDefault("logic", "AND");

        List<Check> preconditions = convertToChecks((List<Map<String, Object>>) params.get("preconditions"));
        List<Check> onSuccess = convertToChecks((List<Map<String, Object>>) params.get("onSuccess"));

        if (preconditions == null || preconditions.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Configuration error: 'preconditions' is required");
        }

        boolean preConditionMatched = false;
        if ("OR".equalsIgnoreCase(logic)) {
            for (Check pre : preconditions) {
                if (evaluateAtomic(projectRoot, pre).passed) {
                    preConditionMatched = true;
                    break;
                }
            }
        } else { 
            preConditionMatched = true;
            for (Check pre : preconditions) {
                if (!evaluateAtomic(projectRoot, pre).passed) {
                    preConditionMatched = false;
                    break;
                }
            }
        }

        if (preConditionMatched) {
            if (onSuccess == null || onSuccess.isEmpty()) {
                return CheckResult.pass(check.getRuleId(), check.getDescription(), "Conditions met, but no 'onSuccess' checks defined.");
            }

            StringBuilder details = new StringBuilder("Conditions met. Executing nested checks:\n");
            boolean allPassed = true;
            java.util.Set<String> aggregatedFiles = new java.util.HashSet<>();
            java.util.Set<String> aggregatedItems = new java.util.LinkedHashSet<>(); // Failures (found items)
            java.util.Set<String> aggregatedMatchingItems = new java.util.LinkedHashSet<>(); // Success matches

            for (Check nested : onSuccess) {
                CheckResult res = evaluateAtomic(projectRoot, nested);
                details.append("- ").append(res.ruleId != null ? res.ruleId : "Check").append(": ").append(res.message).append("\n");

                if (res.checkedFiles != null && !res.checkedFiles.isEmpty()) {
                    for (String f : res.checkedFiles.split(", ")) {
                        if (!f.trim().isEmpty()) aggregatedFiles.add(f.trim());
                    }
                }

                if (res.foundItems != null && !res.foundItems.isEmpty()) {
                    for (String item : res.foundItems.split(", ")) {
                         if (!item.trim().isEmpty()) aggregatedItems.add(item.trim());
                    }
                }
                
                // Aggregate specific success matches
                if (res.matchingFiles != null && !res.matchingFiles.isEmpty()) {
                    for (String item : res.matchingFiles.split(", ")) {
                         if (!item.trim().isEmpty()) aggregatedMatchingItems.add(item.trim());
                    }
                }

                if (res.propertyResolutions != null) {
                    for (String resItem : res.propertyResolutions) {
                        if (!this.propertyResolutions.contains(resItem)) {
                            this.propertyResolutions.add(resItem);
                        }
                    }
                }

                if (!res.passed) {
                    allPassed = false;
                }
            }

            String checkedFilesStr = String.join(", ", aggregatedFiles);
            String foundItemsStr = String.join(", ", aggregatedItems);
            String matchingItemsStr = aggregatedMatchingItems.isEmpty() ? null : String.join(", ", aggregatedMatchingItems);

            if (allPassed) {
                 return CheckResult.pass(check.getRuleId(), check.getDescription(), getCustomSuccessMessage(check, details.toString(), checkedFilesStr, matchingItemsStr), checkedFilesStr, matchingItemsStr, this.propertyResolutions);
            } else {
                 return CheckResult.fail(check.getRuleId(), check.getDescription(), getCustomMessage(check, details.toString(), checkedFilesStr, foundItemsStr, matchingItemsStr), checkedFilesStr, foundItemsStr, matchingItemsStr, this.propertyResolutions);
            }
        } else {

            return CheckResult.pass(check.getRuleId(), check.getDescription(), "⊘ Skipped: Preconditions not met for this project.");
        }
    }

    private CheckResult evaluateAtomic(Path projectRoot, Check check) {
        try {
            AbstractCheck validator = CheckFactory.create(check);
            return validator.execute(projectRoot, check);
        } catch (Exception e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(), "Error executing nested check: " + e.getMessage());
        }
    }

    private List<Check> convertToChecks(List<Map<String, Object>> maps) {
        if (maps == null) return null;
        List<Check> checks = new ArrayList<>();
        for (Map<String, Object> map : maps) {
            Check c = new Check();
            c.setType((String) map.get("type"));
            c.setParams((Map<String, Object>) map.get("params"));
            c.setDescription((String) map.get("description"));
            checks.add(c);
        }
        return checks;
    }
}
