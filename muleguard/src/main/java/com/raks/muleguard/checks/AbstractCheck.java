package com.raks.muleguard.checks;
import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;
import java.nio.file.Path;
import java.util.List;
public abstract class AbstractCheck {
    public abstract CheckResult execute(Path projectRoot, Check check);
    protected CheckResult pass(String message) {
        return new CheckResult("", "", true, message);
    }
    protected CheckResult fail(String message) {
        return new CheckResult("", "", false, message);
    }
    protected List<String> resolveEnvironments(Check check) {
        @SuppressWarnings("unchecked")
        List<String> environments = (List<String>) check.getParams().get("environments");
        return environments;
    }
    protected boolean shouldIgnorePath(Path projectRoot, Path path) {
        Path relativePath = projectRoot.relativize(path);
        String pathString = relativePath.toString().replace('\\', '/');
        return pathString.startsWith("target/") || pathString.contains("/target/") ||
                pathString.startsWith("bin/") || pathString.contains("/bin/") ||
                pathString.startsWith("build/") || pathString.contains("/build/") ||
                pathString.startsWith(".git/") || pathString.contains("/.git/") ||
                pathString.startsWith(".idea/") || pathString.contains("/.idea/") ||
                pathString.startsWith(".vscode/") || pathString.contains("/.vscode/") ||
                pathString.startsWith("node_modules/") || pathString.contains("/node_modules/") ||
                pathString.startsWith("muleguard-reports/") || pathString.contains("/muleguard-reports/");
    }
}