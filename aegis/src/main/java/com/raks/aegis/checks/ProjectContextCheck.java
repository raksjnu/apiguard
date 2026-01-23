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
                return finalizeFail(check, "Project name '" + projectName + "' does not contain '" + nameContains + "'", projectName, nameContains, null);
            }
        }

        if (nameNotContains != null) {
            boolean matches = ignoreCase ? 
                projectName.toLowerCase().contains(nameNotContains.toLowerCase()) : 
                projectName.contains(nameNotContains);
            if (matches) {
                return finalizeFail(check, "Project name '" + projectName + "' forbiddenly contains '" + nameNotContains + "'", projectName, nameNotContains, null);
            }
        }

        if (nameRegex != null) {
            if (!projectName.matches(nameRegex)) {
                return finalizeFail(check, "Project name '" + projectName + "' does not match regex '" + nameRegex + "'", projectName, nameRegex, null);
            }
        }

        return finalizePass(check, "Project context validation passed.", projectName, projectName);
    }
}

