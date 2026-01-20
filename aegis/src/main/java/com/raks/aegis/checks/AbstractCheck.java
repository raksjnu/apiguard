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
    protected List<String> ignoredFileNames = new java.util.ArrayList<>();
    protected List<String> ignoredFilePrefixes = new java.util.ArrayList<>();

    public void init(Map<String, Object> params) {
        this.effectiveParams = params;
        this.propertyResolutions = new java.util.ArrayList<>();
    }

    public void setIgnoredFiles(List<String> exactNames, List<String> prefixes) {
        if (exactNames != null) this.ignoredFileNames.addAll(exactNames);
        if (prefixes != null) this.ignoredFilePrefixes.addAll(prefixes);
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
    protected String resolve(String value, java.nio.file.Path projectRoot) {
        if (value == null) return null;
        return com.raks.aegis.util.PropertyResolver.resolve(value, projectRoot, this.propertyResolutions);
    }

    protected String resolve(String value, java.nio.file.Path projectRoot, java.util.List<String> collection) {
        if (value == null) return null;
        return com.raks.aegis.util.PropertyResolver.resolve(value, projectRoot, collection);
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

    protected String formatMessage(String template, String coreDetails, String failures) {
        return formatMessage(template, coreDetails, failures, null, null, null);
    }

    protected String formatMessage(String template, String coreDetails, String failures, String checkedFiles) {
        return formatMessage(template, coreDetails, failures, checkedFiles, null, null);
    }

    protected String formatMessage(String template, String coreDetails, String failures, String checkedFiles, String foundItems) {
        return formatMessage(template, coreDetails, failures, checkedFiles, foundItems, null);
    }

    protected String formatMessage(String template, String coreDetails, String failures, String checkedFiles, String foundItems, String matchingFiles) {

        if (template == null || template.isEmpty()) {
            return coreDetails != null ? coreDetails : "";
        }

        String result = template;

        String effectiveDetails = coreDetails;
        if (effectiveDetails == null && failures != null) {
            effectiveDetails = failures;
        }

        if (effectiveDetails != null) {
            result = result.replace("{CORE_DETAILS}", effectiveDetails);
            result = result.replace("{DEFAULT_MESSAGE}", effectiveDetails);
        } else {
            result = result.replace("{CORE_DETAILS}", "");
            result = result.replace("{DEFAULT_MESSAGE}", "");
        }

        if (failures != null) {
            result = result.replace("{FAILURES}", failures);
        } else {
            result = result.replace("{FAILURES}", "");
        }
        if (checkedFiles != null && !checkedFiles.trim().isEmpty()) {
            result = result.replace("{CHECKED_FILES}", checkedFiles);
            result = result.replace("{SCANNED_FILES}", checkedFiles);
        } else {
             result = result.replace("{CHECKED_FILES}", "N/A");
             result = result.replace("{SCANNED_FILES}", "N/A");
        }

        if (foundItems != null && !foundItems.trim().isEmpty()) {
            result = result.replace("{FOUND_ITEMS}", foundItems);
        } else {
            result = result.replace("{FOUND_ITEMS}", "N/A");
        }

        if (matchingFiles != null && !matchingFiles.trim().isEmpty()) {
            result = result.replace("{MATCHING_FILES}", matchingFiles);
        } else {
            result = result.replace("{MATCHING_FILES}", "N/A");
        }

        String propertyResolved = getPropertyResolutionsString();
        // If we are formatting a message, we might want to override the property resolved string 
        // if it's already in the result provided to this method. 
        // But here we use the one from 'this' check instance.
        if (!propertyResolved.isEmpty()) {
            result = result.replace("{PROPERTY_RESOLVED}", "Properties Resolved: " + propertyResolved);
        } else {
            result = result.replace("{PROPERTY_RESOLVED}", "Properties Resolved: N/A");
        }

        if (!template.contains("{") && coreDetails != null && !coreDetails.isEmpty()) {
            result += "\n\n" + coreDetails;
        }

        return result;
    }

    protected boolean shouldIgnorePath(Path projectRoot, Path path) {
        Path relativePath = projectRoot.relativize(path);
        String pathString = relativePath.toString().replace('\\', '/');
        
        // check folder ignores first (performance)
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

        // check file ignores
        String fileName = path.getFileName().toString();
        
        if (ignoredFileNames.contains(fileName)) {
            return true;
        }
        
        for (String prefix : ignoredFilePrefixes) {
            if (fileName.startsWith(prefix)) {
                return true;
            }
        }
        
        return false;
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
        @SuppressWarnings("unchecked")
        Map<String, Object> condition = (Map<String, Object>) check.getParams().get("checkCondition");

        if (condition == null || condition.isEmpty()) {
            return true; 
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

    protected boolean evaluateMatchMode(String matchMode, int totalFiles, int matchingFiles) {
        if (matchMode == null || matchMode.isEmpty()) return matchingFiles == totalFiles; 

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
