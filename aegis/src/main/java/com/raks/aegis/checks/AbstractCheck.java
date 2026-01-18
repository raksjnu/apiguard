package com.raks.aegis.checks;
import com.raks.aegis.model.Check;
import com.raks.aegis.model.CheckResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public abstract class AbstractCheck {
    public abstract CheckResult execute(Path projectRoot, Check check);
    protected Map<String, Object> effectiveParams;
    protected List<String> propertyResolutions = new java.util.ArrayList<>();

    public void init(Map<String, Object> params) {
        this.effectiveParams = params;
        this.propertyResolutions = new java.util.ArrayList<>();
    }
    
    /**
     * Track a property resolution for transparency in reports.
     * @param original The original placeholder (e.g., "${soapversion}")
     * @param resolved The resolved value (e.g., "SOAP_11")
     */
    protected void addPropertyResolution(String original, String resolved) {
        if (original != null && resolved != null && !original.equals(resolved)) {
            propertyResolutions.add(original + " → " + resolved);
        }
    }
    
    /**
     * Get formatted property resolutions for display.
     * @return Comma-separated list of resolutions, or empty string if none
     */
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
        return new CheckResult("", "", true, message);
    }
    protected CheckResult fail(String message) {
        return new CheckResult("", "", false, message);
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
        return getCustomMessage(check, defaultMsg, checkedFiles, null);
    }

    protected String getCustomMessage(Check check, String defaultMsg, String checkedFiles, String foundItems) {
        // 1. Check params "message" (legacy override)
        Map<String, Object> params = getEffectiveParams(check);
        if (params.containsKey("message")) {
            return (String) params.get("message");
        }
        
        // 2. Check Rule-level errorMessage
        if (check.getRule() != null && check.getRule().getErrorMessage() != null && !check.getRule().getErrorMessage().isEmpty()) {
            return formatMessage(check.getRule().getErrorMessage(), defaultMsg, null, checkedFiles, foundItems);
        }
        
        return defaultMsg;
    }

    protected String getCustomSuccessMessage(Check check, String defaultMsg) {
        return getCustomSuccessMessage(check, defaultMsg, null);
    }

    protected String getCustomSuccessMessage(Check check, String defaultMsg, String checkedFiles) {
        if (check.getRule() != null && check.getRule().getSuccessMessage() != null && !check.getRule().getSuccessMessage().isEmpty()) {
            return formatMessage(check.getRule().getSuccessMessage(), defaultMsg, null, checkedFiles, null);
        }
        return defaultMsg;
    }
    
    /**
     * Format message template with placeholders.
     * Supports {CORE_DETAILS}, {FAILURES}, {CHECKED_FILES}, and {FOUND_ITEMS}.
     */
    protected String formatMessage(String template, String coreDetails, String failures) {
        return formatMessage(template, coreDetails, failures, null, null);
    }

    protected String formatMessage(String template, String coreDetails, String failures, String checkedFiles) {
        return formatMessage(template, coreDetails, failures, checkedFiles, null);
    }

    protected String formatMessage(String template, String coreDetails, String failures, String checkedFiles, String foundItems) {
        // Fallback: if no template, use core details only
        if (template == null || template.isEmpty()) {
            return coreDetails != null ? coreDetails : "";
        }
        
        String result = template;
        
        // Determine effective details for DEFAULT_MESSAGE replacement
        String effectiveDetails = coreDetails;
        if (effectiveDetails == null && failures != null) {
            effectiveDetails = failures;
        }

        // Replace placeholders
        if (effectiveDetails != null) {
            result = result.replace("{CORE_DETAILS}", effectiveDetails);
            result = result.replace("{DEFAULT_MESSAGE}", effectiveDetails);
        }
        if (failures != null) {
            result = result.replace("{FAILURES}", failures);
        }
        if (checkedFiles != null) {
            result = result.replace("{CHECKED_FILES}", checkedFiles);
            // Also support {SCANNED_FILES} alias
            result = result.replace("{SCANNED_FILES}", checkedFiles);
        } else {
             // If token exists but no files passed, replace with empty or "None"
             result = result.replace("{CHECKED_FILES}", "None");
             result = result.replace("{SCANNED_FILES}", "None");
        }
        
        if (foundItems != null) {
            result = result.replace("{FOUND_ITEMS}", foundItems);
        } else {
            result = result.replace("{FOUND_ITEMS}", "");
        }
        
        // NEW: Property Resolution Token
        String propertyResolved = getPropertyResolutionsString();
        if (!propertyResolved.isEmpty()) {
            result = result.replace("{PROPERTY_RESOLVED}", "Properties Resolved: " + propertyResolved);
        } else {
            result = result.replace("{PROPERTY_RESOLVED}", "");
        }
        
        // If no placeholders found and coreDetails exists, append at end
        if (!template.contains("{") && coreDetails != null && !coreDetails.isEmpty()) {
            result += "\n\n" + coreDetails;
        }
        
        return result;
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
                pathString.startsWith("Aegis-reports/") || pathString.contains("/Aegis-reports/");
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

    /**
     * Standardized Logic for Multi-File Validation.
     * @param matchMode The quantifier (ALL_FILES, ANY_FILE, etc.)
     * @param totalFiles Total number of applicable files found.
     * @param matchingFiles Number of files that passed the specific check condition.
     * @return true if the condition is met, false otherwise.
     */
    protected boolean evaluateMatchMode(String matchMode, int totalFiles, int matchingFiles) {
        if (matchMode == null || matchMode.isEmpty()) return matchingFiles == totalFiles; // Default: ALL

        switch (matchMode.toUpperCase()) {
            case "ANY_FILE":
                return matchingFiles > 0;
            case "NONE_OF_FILES":
                return matchingFiles == 0;
            case "EXACTLY_ONE":
                return matchingFiles == 1;
            case "ALL_FILES":
            default:
                return matchingFiles == totalFiles;
        }
    }

    /**
     * Universal Value Comparison Helper.
     * Supports String, Integer, and SemVer logic.
     * Operators: EQ, NEQ, GT, GTE, LT, LTE, CONTAINS, NOT_CONTAINS, MATCHES (Regex), NOT_MATCHES
     */
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
            // If parsing fails, fall back to strict NEQ
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
            // Lexicographical order for GT/LT strings
            case "GT": return a.compareTo(b) > 0;
            case "GTE": return a.compareTo(b) >= 0;
            case "LT": return a.compareTo(b) < 0;
            case "LTE": return a.compareTo(b) <= 0;
            default: return a.equals(b);
        }
    }

    private boolean compareSemVer(String v1, String v2, String op) {
        // Simplified SemVer Parser: Split by "."
        String[] p1 = v1.replaceAll("[^0-9\\.]", "").split("\\.");
        String[] p2 = v2.replaceAll("[^0-9\\.]", "").split("\\.");
        
        int len = Math.max(p1.length, p2.length);
        int result = 0;
        
        for (int i = 0; i < len; i++) {
            int n1 = i < p1.length && !p1[i].isEmpty() ? Integer.parseInt(p1[i]) : 0;
            int n2 = i < p2.length && !p2[i].isEmpty() ? Integer.parseInt(p2[i]) : 0;
            if (n1 != n2) {
                result = Integer.compare(n1, n2);
                break;
            }
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

    protected boolean evaluateMatchModeWithParam(String matchMode, int totalFiles, int matchingFiles, int n) {
        if (matchMode == null) return evaluateMatchMode(null, totalFiles, matchingFiles);
        
        switch (matchMode.toUpperCase()) {
            case "AT_LEAST_N":
                return matchingFiles >= n;
            case "AT_MOST_N":
                return matchingFiles <= n;
            default:
                return evaluateMatchMode(matchMode, totalFiles, matchingFiles);
        }
    }
}
