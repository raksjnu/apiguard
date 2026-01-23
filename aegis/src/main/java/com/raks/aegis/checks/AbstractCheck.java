package com.raks.aegis.checks;

import com.raks.aegis.model.Check;
import com.raks.aegis.model.CheckResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import org.w3c.dom.Document;

public abstract class AbstractCheck {
    public abstract CheckResult execute(Path projectRoot, Check check);
    protected Map<String, Object> effectiveParams;
    protected List<String> propertyResolutions = new java.util.ArrayList<>();
    protected List<String> ignoredFileNames = new java.util.ArrayList<>();
    protected List<String> ignoredFilePrefixes = new java.util.ArrayList<>();
    protected Path linkedConfigPath;
    protected java.util.Set<Path> fileFilter;

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

    public void setFileFilter(java.util.Set<Path> fileFilter) {
        this.fileFilter = fileFilter;
    }

    /**
     * Additive helper to find files across project and linked config roots.
     */
    protected List<Path> findFiles(Path projectRoot, List<String> filePatterns, boolean includeLinked) {
        List<Path> searchRoots = com.raks.aegis.util.ProjectContextHelper.getEffectiveSearchRoots(projectRoot, this.linkedConfigPath, includeLinked);
        List<Path> allMatching = new ArrayList<>();
        
        for (Path root : searchRoots) {
            try (java.util.stream.Stream<Path> paths = java.nio.file.Files.walk(root)) {
                paths.filter(java.nio.file.Files::isRegularFile)
                    .filter(path -> matchesAnyPattern(path, filePatterns, root))
                    .filter(path -> !shouldIgnorePath(root, path))
                    .forEach(allMatching::add);
            } catch (Exception e) {}
        }

        if (fileFilter != null && !fileFilter.isEmpty()) {
            boolean filterApplicable = false;
            for (Path f : fileFilter) {
                if (allMatching.contains(f)) {
                    filterApplicable = true;
                    break;
                }
            }
            if (filterApplicable) {
                return allMatching.stream().filter(fileFilter::contains).toList();
            }
        }
        return allMatching;
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
        return String.join("\n", propertyResolutions);
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
        return resolve(value, projectRoot, this.propertyResolutions);
    }

    protected String resolve(String value, Path projectRoot, java.util.List<String> collection) {
        if (value == null) return null;
        
        Map<String, Object> params = getEffectiveParams(null);
        boolean resolvePropertiesMaster = true; 
        if (params != null && (params.containsKey("resolveProperties") || params.containsKey("resolveProperty"))) {
             resolvePropertiesMaster = Boolean.parseBoolean(String.valueOf(params.getOrDefault("resolveProperties", params.getOrDefault("resolveProperty", "true"))));
        }

        if (!resolvePropertiesMaster) return value;

        boolean includeLinked = false;
        if (params != null) {
            if (params.containsKey("includeLinkedConfig")) {
                includeLinked = Boolean.parseBoolean(String.valueOf(params.get("includeLinkedConfig")));
            } else if (params.containsKey("resolveLinkedConfig")) {
                includeLinked = Boolean.parseBoolean(String.valueOf(params.get("resolveLinkedConfig")));
            }
        }
        
        return com.raks.aegis.util.PropertyResolver.resolveProjectProperty(value, projectRoot, this.linkedConfigPath, includeLinked, collection);
    }

    protected java.util.Set<String> resolveAll(String value, Path projectRoot) {
        return resolveAll(value, projectRoot, this.propertyResolutions);
    }

    protected java.util.Set<String> resolveAll(String value, Path projectRoot, java.util.List<String> collection) {
        if (value == null) return java.util.Collections.emptySet();
        
        Map<String, Object> params = getEffectiveParams(null);
        boolean resolvePropertiesMaster = true; 
        if (params != null && (params.containsKey("resolveProperties") || params.containsKey("resolveProperty"))) {
             resolvePropertiesMaster = Boolean.parseBoolean(String.valueOf(params.getOrDefault("resolveProperties", params.getOrDefault("resolveProperty", "true"))));
        }

        if (!resolvePropertiesMaster) return java.util.Collections.singleton(value);

        boolean includeLinked = false;
        if (params != null) {
            if (params.containsKey("includeLinkedConfig")) {
                includeLinked = Boolean.parseBoolean(String.valueOf(params.get("includeLinkedConfig")));
            } else if (params.containsKey("resolveLinkedConfig")) {
                includeLinked = Boolean.parseBoolean(String.valueOf(params.get("resolveLinkedConfig")));
            }
        }
        
        return com.raks.aegis.util.CheckHelper.resolveAndRecord(value, projectRoot, this.linkedConfigPath, null, true, collection, includeLinked);
    }

    protected java.util.Set<String> resolveAndRecord(String value, Path projectRoot, List<String> searchTokens, boolean alwaysRecord) {
        Map<String, Object> params = getEffectiveParams(null);
        boolean includeLinked = false;
        if (params != null) {
            if (params.containsKey("includeLinkedConfig")) {
                includeLinked = Boolean.parseBoolean(String.valueOf(params.get("includeLinkedConfig")));
            } else if (params.containsKey("resolveLinkedConfig")) {
                includeLinked = Boolean.parseBoolean(String.valueOf(params.get("resolveLinkedConfig")));
            }
        }
        return com.raks.aegis.util.CheckHelper.resolveAndRecord(value, projectRoot, this.linkedConfigPath, searchTokens, alwaysRecord, this.propertyResolutions, includeLinked);
    }

    protected boolean searchAll(String rawContent, Path projectRoot, List<String> tokens, 
                              boolean caseSensitive, boolean isRegex, boolean wholeWord, 
                              List<java.util.regex.Pattern> patterns, 
                              java.util.Set<String> fileFound, 
                              java.util.Set<String> thisFileMatchedTokens, 
                              java.util.Set<String> allFoundItems) {
        
        java.util.Set<String> resolvedContents = resolveAndRecord(rawContent, projectRoot, tokens, false);
        boolean contentMatched = false;

        for (String contentForMatch : resolvedContents) {
            if (!caseSensitive && !isRegex && !wholeWord) contentForMatch = contentForMatch.toLowerCase();

            for (int i = 0; i < tokens.size(); i++) {
                String configToken = tokens.get(i);
                if (!caseSensitive && !isRegex && !wholeWord) configToken = configToken.toLowerCase();
                
                if (isRegex || wholeWord) {
                    java.util.regex.Matcher m = patterns.get(i).matcher(contentForMatch);
                    while (m.find()) { 
                        String tokenToAdd = m.group();
                        fileFound.add(tokenToAdd);
                        thisFileMatchedTokens.add(tokens.get(i));
                        allFoundItems.add(tokenToAdd);
                        contentMatched = true;
                    }
                } else {
                    if (contentForMatch.contains(configToken)) {
                        fileFound.add(configToken);
                        thisFileMatchedTokens.add(tokens.get(i));
                        allFoundItems.add(configToken);
                        contentMatched = true;
                    }
                }
            }
        }
        return contentMatched;
    }

    protected List<String> resolveEnvironments(Check check) {
        Map<String, Object> params = getEffectiveParams(check);
        @SuppressWarnings("unchecked")
        List<String> environments = (List<String>) params.get("environments");
        return environments;
    }

    protected String getCustomMessage(Check check, String defaultMsg) {
        return getCustomMessage(check, defaultMsg, null, null, null);
    }

    protected String getCustomMessage(Check check, String defaultMsg, String checkedFiles) {
        return getCustomMessage(check, defaultMsg, checkedFiles, null, null);
    }

    protected String getCustomMessage(Check check, String defaultMsg, String checkedFiles, String foundItems) {
        return getCustomMessage(check, defaultMsg, checkedFiles, foundItems, null);
    }

    protected String getCustomMessage(Check check, String defaultMsg, String checkedFiles, String foundItems, String matchingFiles) {
        Map<String, Object> params = getEffectiveParams(check);
        String template = null;
        if (params.containsKey("message")) {
            template = (String) params.get("message");
        } else if (check.getRule() != null && check.getRule().getErrorMessage() != null && !check.getRule().getErrorMessage().isEmpty()) {
            template = check.getRule().getErrorMessage();
        }
        
        if (template != null) {
            return formatMessage(template, defaultMsg, null, checkedFiles, foundItems, matchingFiles);
        }
        return formatMessage("{DEFAULT_MESSAGE}", defaultMsg, null, checkedFiles, foundItems, matchingFiles);
    }

    protected String getCustomSuccessMessage(Check check, String defaultMsg) {
        return getCustomSuccessMessage(check, defaultMsg, null, null, null);
    }

    protected String getCustomSuccessMessage(Check check, String defaultMsg, String checkedFiles) {
        return getCustomSuccessMessage(check, defaultMsg, checkedFiles, null, null);
    }

    protected String getCustomSuccessMessage(Check check, String defaultMsg, String checkedFiles, String matchingFiles) {
        return getCustomSuccessMessage(check, defaultMsg, checkedFiles, null, matchingFiles);
    }

    protected String getCustomSuccessMessage(Check check, String defaultMsg, String checkedFiles, String foundItems, String matchingFiles) {
        String template = null;
        if (check.getRule() != null && check.getRule().getSuccessMessage() != null && !check.getRule().getSuccessMessage().isEmpty()) {
            template = check.getRule().getSuccessMessage();
        }
        
        if (template != null) {
            return formatMessage(template, defaultMsg, null, checkedFiles, foundItems, matchingFiles);
        }
        // If no template, use {DEFAULT_MESSAGE} as template for pass case too to get auto-append
        return formatMessage("{DEFAULT_MESSAGE}", defaultMsg, null, checkedFiles, foundItems, matchingFiles);
    }

    // New helper methods to finalize results in a segregated way
    protected CheckResult finalizePass(Check check, String defaultMsg, String checkedFiles, String matchingFiles) {
        return finalizePass(check, defaultMsg, checkedFiles, null, matchingFiles, null);
    }
    protected CheckResult finalizePass(Check check, String defaultMsg, String checkedFiles, String foundItems, String matchingFiles) {
        return finalizePass(check, defaultMsg, checkedFiles, foundItems, matchingFiles, null);
    }

    protected CheckResult finalizePass(Check check, String defaultMsg, String checkedFiles, String foundItems, String matchingFiles, java.util.Set<Path> matchedPaths) {
        String msg = getCustomSuccessMessage(check, defaultMsg, checkedFiles, foundItems, matchingFiles);
        return CheckResult.pass(check.getRuleId(), check.getDescription(), msg, checkedFiles, foundItems, matchingFiles, this.propertyResolutions, matchedPaths);
    }

    protected CheckResult finalizeFail(Check check, String defaultMsg, String checkedFiles, String foundItems, String matchingFiles) {
        return finalizeFail(check, defaultMsg, checkedFiles, foundItems, matchingFiles, null);
    }

    protected CheckResult finalizeFail(Check check, String defaultMsg, String checkedFiles, String foundItems, String matchingFiles, java.util.Set<Path> matchedPaths) {
        String msg = getCustomMessage(check, defaultMsg, checkedFiles, foundItems, matchingFiles);
        return CheckResult.fail(check.getRuleId(), check.getDescription(), msg, checkedFiles, foundItems, matchingFiles, this.propertyResolutions, matchedPaths);
    }

    protected String formatMessage(String template, String coreDetails, String failures, String checkedFiles, String foundItems, String matchingFiles) {
        if (template == null || template.isEmpty()) {
            template = "{DEFAULT_MESSAGE}";
        }
        
        String result = template;
        String effectiveDetails = coreDetails != null ? coreDetails : (failures != null ? failures : "");
        
        // Define standard tokens
        String cf = (checkedFiles != null && !checkedFiles.trim().isEmpty()) ? checkedFiles : "N/A";
        String fi = (foundItems != null && !foundItems.trim().isEmpty()) ? foundItems : "N/A";
        String mf = (matchingFiles != null && !matchingFiles.trim().isEmpty()) ? matchingFiles : "N/A";
        String propertyResolved = getPropertyResolutionsString();
        String pr = !propertyResolved.isEmpty() ? "Properties Resolved:\n" + propertyResolved : "Properties Resolved: N/A";

        // Check if template uses tokens. If not, we will append them automatically.
        boolean usesTokens = template.contains("{CHECKED_FILES}") || template.contains("{FOUND_ITEMS}") || 
                             template.contains("{MATCHING_FILES}") || template.contains("{PROPERTY_RESOLVED}") ||
                             template.contains("{DEFAULT_MESSAGE}") || template.contains("{CORE_DETAILS}") ||
                             template.contains("{SCANNED_FILES}") || template.contains("{FAILURES}");

        result = result.replace("{CORE_DETAILS}", effectiveDetails);
        result = result.replace("{DEFAULT_MESSAGE}", effectiveDetails);
        result = result.replace("{FAILURES}", failures != null ? failures : "");
        result = result.replace("{CHECKED_FILES}", cf);
        result = result.replace("{SCANNED_FILES}", cf);
        result = result.replace("{FOUND_ITEMS}", fi);
        result = result.replace("{MATCHING_FILES}", mf);
        result = result.replace("{PROPERTY_RESOLVED}", pr);

        // Innovation: Automatically append tokens if they are not explicitly mentioned in the template
        if (!usesTokens) {
            StringBuilder autoAppend = new StringBuilder();
            if (result.equals(effectiveDetails)) {
                // If the result is just the default message, we prefix it to separate from tokens
                // But normally we want the original message at the top
            }
            autoAppend.append("\nFiles Checked: ").append(cf);
            autoAppend.append("\nItems Found: ").append(fi);
            autoAppend.append("\nItems Matched: ").append(mf);
            autoAppend.append("\nDetails: ").append(effectiveDetails);
            autoAppend.append("\n").append(pr);
            result += autoAppend.toString();
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

    protected String readFileContent(Path file, Map<String, Object> params) throws IOException {
        String content = Files.readString(file);
        boolean ignoreComments = true;
        if (params != null && params.containsKey("ignoreComments")) {
            ignoreComments = Boolean.parseBoolean(String.valueOf(params.get("ignoreComments")));
        }
        if (ignoreComments) {
            content = removeComments(content, file);
        }
        return content;
    }

    protected Document parseXml(Path file, Map<String, Object> params) throws Exception {
        String content = readFileContent(file, params);
        javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Security features to prevent XXE
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (Exception ignore) {}
        
        javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new java.io.ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    protected String removeComments(String content, Path file) {
        if (content == null) return null;
        String fileName = file.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".xml") || fileName.endsWith(".html") || fileName.endsWith(".mule")) {
            return content.replaceAll("(?s)<!--.*?-->", "");
        } else if (fileName.endsWith(".java") || fileName.endsWith(".js") || fileName.endsWith(".c") || fileName.endsWith(".cpp")) {
            // Multi-line and single-line
            return content.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
        } else if (fileName.endsWith(".properties") || fileName.endsWith(".yaml") || fileName.endsWith(".yml") || fileName.endsWith(".sh") || fileName.endsWith(".policy")) {
            return content.replaceAll("(?m)^\\s*#.*$", "");
        } else if (fileName.endsWith(".json")) {
            // JSON theoretically doesn't have comments, but many tools allow them
            return content.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
        }
        return content;
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
        return com.raks.aegis.util.CheckHelper.evaluateMatchMode(matchMode, totalFiles, matchingFiles);
    }

    protected boolean compareValues(String actual, String expected, String operator, String type) {
        return com.raks.aegis.util.CheckHelper.compareValues(actual, expected, operator, type);
    }
}
