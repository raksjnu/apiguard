package com.raks.aegis.engine;
import com.raks.aegis.model.Check;
import com.raks.aegis.model.CheckResult;
public class RuleExecutor {
    public static CheckResult executeCheck(com.raks.aegis.checks.AbstractCheck check, java.nio.file.Path root,
            Check config) {
        return check.execute(root, config);
    }
}
