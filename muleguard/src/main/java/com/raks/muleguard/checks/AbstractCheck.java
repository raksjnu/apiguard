package com.raks.muleguard.checks;
import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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

    protected boolean matchesAnyPattern(Path path, List<String> patterns, Path projectRoot) {
        return matchesAnyPattern(path, patterns, projectRoot, true);
    }

    protected boolean matchesAnyPattern(Path path, List<String> patterns, Path projectRoot, boolean caseSensitive) {
        String relativePath = projectRoot.relativize(path).toString().replace("\\", "/");
        for (String pattern : patterns) {
            if (matchesPattern(relativePath, pattern, caseSensitive)) {
                return true;
            }
        }
        return false;
    }

    protected boolean matchesPattern(String path, String pattern) {
        return matchesPattern(path, pattern, true);
    }

    protected boolean matchesPattern(String path, String pattern, boolean caseSensitive) {
        String regex = pattern
                .replace(".", "\\.")
                .replace("**/", ".*")
                .replace("**", ".*")
                .replace("*", "[^/]*")
                .replace("?", ".");
        
        java.util.regex.Pattern p = caseSensitive ? 
            java.util.regex.Pattern.compile(regex) : 
            java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE);
            
        return p.matcher(path).matches();
    }
    
    /**
     * Checks if the rule should actully run based on pre-conditions.
     * E.g., Only run if a certain namespace or file exists.
     */
    protected boolean isRuleApplicable(Path projectRoot, Check check) {
        @SuppressWarnings("unchecked")
        Map<String, Object> condition = (Map<String, Object>) check.getParams().get("checkCondition");
        
        if (condition == null || condition.isEmpty()) {
            return true; // No condition, always run
        }
        
        String type = (String) condition.get("type");
        if ("NAMESPACE_EXISTS".equalsIgnoreCase(type)) {
            String namespaceUri = (String) condition.get("namespace");
            return checkNamespaceExists(projectRoot, namespaceUri);
        }
        
        return true;
    }

    private boolean checkNamespaceExists(Path projectRoot, String namespaceUri) {
         try (java.util.stream.Stream<Path> paths = java.nio.file.Files.walk(projectRoot)) {
            return paths
                    .filter(java.nio.file.Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".xml"))
                    .anyMatch(file -> {
                        try {
                            String content = java.nio.file.Files.readString(file);
                            return content.contains(namespaceUri);
                        } catch (java.io.IOException e) {
                            return false;
                        }
                    });
        } catch (java.io.IOException e) {
            return false;
        }
    }
}
