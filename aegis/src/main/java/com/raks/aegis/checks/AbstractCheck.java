package com.raks.aegis.checks;

import com.raks.aegis.model.Check;
import com.raks.aegis.model.CheckResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public abstract class AbstractCheck {
    public abstract CheckResult execute(Path projectRoot, Check check);
    protected Map<String, Object> effectiveParams;
    protected List<String> propertyResolutions = new java.util.ArrayList<>();
    protected List<String> ignoredFileNames = new java.util.ArrayList<>();
    protected List<String> ignoredFilePrefixes = new java.util.ArrayList<>();
    protected Path linkedConfigPath;

    public void init(Map<String, Object> params) {
        this.effectiveParams = params;
        this.propertyResolutions = new java.util.ArrayList<>();
    }

    public void setIgnoredFiles(List<String> exactNames, List<String> prefixes) {
        if (exactNames != null) this.ignoredFileNames.addAll(exactNames);
        if (prefixes != null) this.ignoredFilePrefixes.addAll(prefixes);
    }

    public void setLinkedConfigPath(Path linkedConfigPath) {
        this.linkedConfigPath = linkedConfigPath;
    }

    /**
     * Additive helper to find files across project and linked config roots.
     */
    protected List<Path> findFiles(Path projectRoot, List<String> filePatterns, boolean includeLinked) {
        List<Path> searchRoots = com.raks.aegis.util.ProjectContextHelper.getEffectiveSearchRoots(projectRoot, this.linkedConfigPath, includeLinked);
        List<Path> matchingFiles = new ArrayList<>();
        
        for (Path root : searchRoots) {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                    .filter(path -> matchesAnyPattern(path, filePatterns, root))
                    .filter(path -> !shouldIgnorePath(root, path))
                    .forEach(matchingFiles::add);
            } catch (Exception e) {
                // Silently skip if a root is inaccessible (safe fallback)
            }
        }
        return matchingFiles;
    }

    protected void addPropertyResolution(String original, String resolved) {
        if (original != null && resolved != null && !original.equals(resolved)) {
            String resolution = original + " → " + resolved;
            if (!propertyResolutions.contains(resolution)) {
                propertyResolutions.add(resolution);
            }
        }
    }

    protected void recordResolutions(List<String> list) {
        if (list == null || list.isEmpty()) return;
        for (String res : list) {
            if (!this.propertyResolutions.contains(res)) {
                this.propertyResolutions.add(res);
            }
        }
    }

    protected String getPropertyResolutionsString() {
        if (propertyResolutions.isEmpty()) {
            return "";
        }
        return String.join(", ", propertyResolutions);
    }

    protected Map<String, Object> getEffectiveParams(Check check) {
        if (effectiveParams != null) return effectiveParams;
        return check.getParams() != null ? check.getParams() : java.util.Collections.emptyMap();
    }

    protected CheckResult pass(String message) {
        return new CheckResult("", "", true, message, null, null, null, this.propertyResolutions);
    }
    protected CheckResult pass(String message, String checkedFiles) {
        return new CheckResult("", "", true, message, checkedFiles, null, null, this.propertyResolutions);
    }
    protected CheckResult fail(String message) {
        return new CheckResult("", "", false, message, null, null, null, this.propertyResolutions);
    }
    protected CheckResult fail(String message, String checkedFiles) {
        return new CheckResult("", "", false, message, checkedFiles, null, null, this.propertyResolutions);
    }
    protected CheckResult fail(String message, String checkedFiles, String foundItems) {
        return new CheckResult("", "", false, message, checkedFiles, foundItems, null, this.propertyResolutions);
    }
    
    protected String resolve(String value, Path projectRoot) {
        if (value == null) return null;
        boolean resolveLinked = (Boolean) getEffectiveParams(null).getOrDefault("resolveLinkedConfig", false);
        return com.raks.aegis.util.ProjectContextHelper.resolveWithFallback(value, projectRoot, this.linkedConfigPath, resolveLinked);
    }

    protected String resolve(String value, Path projectRoot, java.util.List<String> collection) {
        if (value == null) return null;
        boolean resolveLinked = (Boolean) getEffectiveParams(null).getOrDefault("resolveLinkedConfig", false);
        
        // Determine prefix for this root: [Config] for linked config path, null (for "from ") for primary
        String prefix = (linkedConfigPath != null && projectRoot.equals(linkedConfigPath)) ? "[Config] " : null;
        
        String resolved = com.raks.aegis.util.PropertyResolver.resolve(value, projectRoot, collection, prefix);
        
        // Fallback to linked config if resolved value still contains placeholders and it's not already the linkedConfigPath
        if (resolveLinked && linkedConfigPath != null && !projectRoot.equals(linkedConfigPath)) {
            resolved = com.raks.aegis.util.PropertyResolver.resolve(resolved, linkedConfigPath, collection, "[Config] ");
        }
        return resolved;
    }

    protected List<String> resolveEnvironments(Check check) {
        Map<String, Object> params = getEffectiveParams(check);
        @SuppressWarnings("unchecked")
        List<String> environments = (List<String>) params.get("environments");
        return environments;
    }

    protected String getCustomMessage(Check check, String defaultMsg) {
        return getCustomMessage(check, defaultMsg, null);
    }

    protected String getCustomMessage(Check check, String defaultMsg, String checkedFiles) {
        return getCustomMessage(check, defaultMsg, checkedFiles, null, null);
    }

    protected String getCustomMessage(Check check, String defaultMsg, String checkedFiles, String foundItems) {
        return getCustomMessage(check, defaultMsg, checkedFiles, foundItems, null);
    }

    protected String getCustomMessage(Check check, String defaultMsg, String checkedFiles, String foundItems, String matchingFiles) {
        Map<String, Object> params = getEffectiveParams(check);
        if (params.containsKey("message")) {
            return (String) params.get("message");
        }
        if (check.getRule() != null && check.getRule().getErrorMessage() != null && !check.getRule().getErrorMessage().isEmpty()) {
            return formatMessage(check.getRule().getErrorMessage(), defaultMsg, null, checkedFiles, foundItems, matchingFiles);
        }
        return defaultMsg;
    }

    protected String getCustomSuccessMessage(Check check, String defaultMsg) {
        return getCustomSuccessMessage(check, defaultMsg, null);
    }

    protected String getCustomSuccessMessage(Check check, String defaultMsg, String checkedFiles) {
        return getCustomSuccessMessage(check, defaultMsg, checkedFiles, null, null);
    }

    protected String getCustomSuccessMessage(Check check, String defaultMsg, String checkedFiles, String matchingFiles) {
        return getCustomSuccessMessage(check, defaultMsg, checkedFiles, null, matchingFiles);
    }

    protected String getCustomSuccessMessage(Check check, String defaultMsg, String checkedFiles, String foundItems, String matchingFiles) {
        if (check.getRule() != null && check.getRule().getSuccessMessage() != null && !check.getRule().getSuccessMessage().isEmpty()) {
            return formatMessage(check.getRule().getSuccessMessage(), defaultMsg, null, checkedFiles, foundItems, matchingFiles);
        }
        return defaultMsg;
    }

    protected String formatMessage(String template, String coreDetails, String failures, String checkedFiles, String foundItems, String matchingFiles) {
        if (template == null || template.isEmpty()) {
            return coreDetails != null ? coreDetails : "";
        }
        String result = template;
        String effectiveDetails = coreDetails != null ? coreDetails : (failures != null ? failures : "");
        result = result.replace("{CORE_DETAILS}", effectiveDetails);
        result = result.replace("{DEFAULT_MESSAGE}", effectiveDetails);
        result = result.replace("{FAILURES}", failures != null ? failures : "");
        result = result.replace("{CHECKED_FILES}", (checkedFiles != null && !checkedFiles.trim().isEmpty()) ? checkedFiles : "N/A");
        result = result.replace("{SCANNED_FILES}", (checkedFiles != null && !checkedFiles.trim().isEmpty()) ? checkedFiles : "N/A");
        result = result.replace("{FOUND_ITEMS}", (foundItems != null && !foundItems.trim().isEmpty()) ? foundItems : "N/A");
        result = result.replace("{MATCHING_FILES}", (matchingFiles != null && !matchingFiles.trim().isEmpty()) ? matchingFiles : "N/A");
        String propertyResolved = getPropertyResolutionsString();
        result = result.replace("{PROPERTY_RESOLVED}", !propertyResolved.isEmpty() ? "Properties Resolved: " + propertyResolved : "Properties Resolved: N/A");
        if (!template.contains("{") && coreDetails != null && !coreDetails.isEmpty()) {
            result += "\n\n" + coreDetails;
        }
        return result;
    }

    protected boolean shouldIgnorePath(Path root, Path path) {
        Path relativePath = root.relativize(path);
        String pathString = relativePath.toString().replace('\\', '/');
        if (pathString.startsWith("target/") || pathString.contains("/target/") ||
                pathString.startsWith("bin/") || pathString.contains("/bin/") ||
                pathString.startsWith("build/") || pathString.contains("/build/") ||
                pathString.startsWith(".git/") || pathString.contains("/.git/") ||
                pathString.startsWith(".idea/") || pathString.contains("/.idea/") ||
                pathString.startsWith(".vscode/") || pathString.contains("/.vscode/") ||
                pathString.startsWith("node_modules/") || pathString.contains("/node_modules/") ||
                pathString.startsWith("Aegis-reports/") || pathString.contains("/Aegis-reports/")) {
            return true;
        }
        String fileName = path.getFileName().toString();
        if (ignoredFileNames.contains(fileName)) return true;
        for (String prefix : ignoredFilePrefixes) {
            if (fileName.startsWith(prefix)) return true;
        }
        return false;
    }

    protected boolean matchesAnyPattern(Path path, List<String> patterns, Path root) {
        return matchesAnyPattern(path, patterns, root, true);
    }

    protected boolean matchesAnyPattern(Path path, List<String> patterns, Path root, boolean caseSensitive) {
        String relativePath = root.relativize(path).toString().replace("\\", "/");
        for (String pattern : patterns) {
            if (matchesPattern(relativePath, pattern, caseSensitive)) {
                return true;
            }
        }
        return false;
    }

    protected boolean matchesPattern(String path, String pattern, boolean caseSensitive) {
        String normalizedPath = path.replace('\\', '/');
        String regex = pattern.replace(".", "\\.");
        regex = regex.replace("?", ".");
        regex = regex.replace("**/", "___DS_SLASH___");
        regex = regex.replace("**", "___DS___");
        regex = regex.replace("*", "[^/]*");
        regex = regex.replace("___DS_SLASH___", ".*");
        regex = regex.replace("___DS___", ".*");
        java.util.regex.Pattern p = caseSensitive ? 
            java.util.regex.Pattern.compile(regex) : 
            java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE);
        return p.matcher(normalizedPath).matches();
    }

    protected boolean isRuleApplicable(Path projectRoot, Check check) {
        Map<String, Object> params = getEffectiveParams(check);
        @SuppressWarnings("unchecked")
        Map<String, Object> condition = (Map<String, Object>) params.get("checkCondition");
        if (condition == null || condition.isEmpty()) return true;
        String type = (String) condition.get("type");
        if ("NAMESPACE_EXISTS".equalsIgnoreCase(type)) {
            String namespaceUri = (String) condition.get("namespace");
            return checkNamespaceExists(projectRoot, namespaceUri);
        }
        return true;
    }

    private boolean checkNamespaceExists(Path projectRoot, String namespaceUri) {
         try (Stream<Path> paths = Files.walk(projectRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".xml"))
                    .anyMatch(file -> {
                        try {
                            String content = Files.readString(file);
                            return content.contains(namespaceUri);
                        } catch (java.io.IOException e) {
                            return false;
                        }
                    });
        } catch (java.io.IOException e) {
            return false;
        }
    }

    protected boolean evaluateMatchMode(String matchMode, int totalFiles, int matchingFiles) {
        if (matchMode == null || matchMode.isEmpty()) return matchingFiles == totalFiles; 
        switch (matchMode.toUpperCase()) {
            case "ANY_FILE": return matchingFiles > 0;
            case "NONE_OF_FILES": return matchingFiles == 0;
            case "EXACTLY_ONE": return matchingFiles == 1;
            case "ALL_FILES":
            default: return matchingFiles == totalFiles;
        }
    }

    protected boolean compareValues(String actual, String expected, String operator, String type) {
        if (actual == null || expected == null) return false;
        if (operator == null) operator = "EQ";
        if (type == null) type = "STRING";
        try {
            if ("INTEGER".equalsIgnoreCase(type)) {
                long act = Long.parseLong(actual.trim());
                long exp = Long.parseLong(expected.trim());
                return compareNumeric(act, exp, operator);
            } else if ("SEMVER".equalsIgnoreCase(type)) {
                return compareSemVer(actual.trim(), expected.trim(), operator);
            } else {
                return compareString(actual, expected, operator);
            }
        } catch (NumberFormatException e) {
            return "NEQ".equalsIgnoreCase(operator);
        }
    }

    private boolean compareNumeric(long a, long b, String op) {
        switch (op.toUpperCase()) {
            case "EQ": return a == b;
            case "NEQ": return a != b;
            case "GT": return a > b;
            case "GTE": return a >= b;
            case "LT": return a < b;
            case "LTE": return a <= b;
            default: return false;
        }
    }

    private boolean compareString(String a, String b, String op) {
        switch (op.toUpperCase()) {
            case "EQ": return a.equals(b);
            case "NEQ": return !a.equals(b);
            case "CONTAINS": return a.contains(b);
            case "NOT_CONTAINS": return !a.contains(b);
            case "MATCHES": return a.matches(b);
            case "NOT_MATCHES": return !a.matches(b);
            case "GT": return a.compareTo(b) > 0;
            case "GTE": return a.compareTo(b) >= 0;
            case "LT": return a.compareTo(b) < 0;
            case "LTE": return a.compareTo(b) <= 0;
            default: return a.equals(b);
        }
    }

    private boolean compareSemVer(String v1, String v2, String op) {
        String[] p1 = v1.replaceAll("[^0-9\\.]", "").split("\\.");
        String[] p2 = v2.replaceAll("[^0-9\\.]", "").split("\\.");
        int len = Math.max(p1.length, p2.length);
        int result = 0;
        for (int i = 0; i < len; i++) {
            int n1 = i < p1.length && !p1[i].isEmpty() ? Integer.parseInt(p1[i]) : 0;
            int n2 = i < p2.length && !p2[i].isEmpty() ? Integer.parseInt(p2[i]) : 0;
            if (n1 != n2) { result = Integer.compare(n1, n2); break; }
        }
        switch (op.toUpperCase()) {
            case "EQ": return result == 0;
            case "NEQ": return result != 0;
            case "GT": return result > 0;
            case "GTE": return result >= 0;
            case "LT": return result < 0;
            case "LTE": return result <= 0;
            default: return result == 0;
        }
    }
}
