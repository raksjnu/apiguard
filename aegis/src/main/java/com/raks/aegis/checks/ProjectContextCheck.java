package com.raks.aegis.checks;

import com.raks.aegis.model.Check;
import com.raks.aegis.model.CheckResult;
import java.nio.file.Path;
import java.util.Map;

public class ProjectContextCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        String projectName = projectRoot.getFileName().toString();
        Map<String, Object> params = check.getParams();

        String nameContains = (String) params.get("nameContains");
        String nameNotContains = (String) params.get("nameNotContains");
        String nameRegex = (String) params.get("nameRegex");
        boolean ignoreCase = (Boolean) params.getOrDefault("ignoreCase", true);

        if (nameContains != null) {
            boolean matches = ignoreCase ? 
                projectName.toLowerCase().contains(nameContains.toLowerCase()) : 
                projectName.contains(nameContains);
            if (!matches) {
                return CheckResult.fail(check.getRuleId(), check.getDescription(), 
                    "Project name '" + projectName + "' does not contain '" + nameContains + "'");
            }
        }

        if (nameNotContains != null) {
            boolean matches = ignoreCase ? 
                projectName.toLowerCase().contains(nameNotContains.toLowerCase()) : 
                projectName.contains(nameNotContains);
            if (matches) {
                return CheckResult.fail(check.getRuleId(), check.getDescription(), 
                    "Project name '" + projectName + "' forbiddenly contains '" + nameNotContains + "'");
            }
        }

        if (nameRegex != null) {
            if (!projectName.matches(nameRegex)) {
                return CheckResult.fail(check.getRuleId(), check.getDescription(), 
                    "Project name '" + projectName + "' does not match regex '" + nameRegex + "'");
            }
        }

        return CheckResult.pass(check.getRuleId(), check.getDescription(), "Project context validation passed.");
    }
}
