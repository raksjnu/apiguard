package com.raks.muleguard.engine;

import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;

public class RuleExecutor {
    public static CheckResult executeCheck(com.raks.muleguard.checks.AbstractCheck check, java.nio.file.Path root,
            Check config) {
        return check.execute(root, config);
    }
}