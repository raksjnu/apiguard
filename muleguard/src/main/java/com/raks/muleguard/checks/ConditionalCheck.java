package com.raks.muleguard.checks;

import com.raks.muleguard.checks.CheckFactory;
import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executes a list of 'onSuccess' checks ONLY if 'preconditions' are met.
 * Handles AND/OR logic for preconditions.
 */
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
        } else { // Default AND
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
            for (Check nested : onSuccess) {
                CheckResult res = evaluateAtomic(projectRoot, nested);
                details.append("- ").append(res.ruleId != null ? res.ruleId : "Check").append(": ").append(res.message).append("\n");
                if (!res.passed) {
                    allPassed = false;
                }
            }
            
            return allPassed ? 
                CheckResult.pass(check.getRuleId(), check.getDescription(), details.toString()) :
                CheckResult.fail(check.getRuleId(), check.getDescription(), details.toString());
        } else {

            return CheckResult.pass(check.getRuleId(), check.getDescription(), "Skipped: Preconditions not met for this project.");
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
