package com.raks.muleguard.checks;
import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
public class GenericTokenSearchCheck extends AbstractCheck {
    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) check.getParams().get("filePatterns");
        @SuppressWarnings("unchecked")
        List<String> tokens = (List<String>) check.getParams().get("tokens");
        List<String> environments = null;
        Object envsParam = check.getParams().get("environments");
        if (envsParam != null) {
            environments = resolveEnvironments(check);
        }
        String searchMode = (String) check.getParams().getOrDefault("searchMode", "FORBIDDEN");
        String matchMode = (String) check.getParams().getOrDefault("matchMode", "SUBSTRING");
        String elementName = (String) check.getParams().get("elementName");
        if (filePatterns == null || filePatterns.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'filePatterns' parameter is required");
        }
        if (tokens == null || tokens.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'tokens' parameter is required");
        }
        File searchDir = projectRoot.toFile();
        if (!searchDir.exists() || !searchDir.isDirectory()) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    "Project directory not found: " + projectRoot);
        }
        final List<String> finalEnvironments = environments;
        IOFileFilter fileFilter = new IOFileFilter() {
            @Override
            public boolean accept(File file) {
                String normalizedPath = FilenameUtils.separatorsToUnix(file.getAbsolutePath());
                boolean patternMatches = false;
                for (String pattern : filePatterns) {
                    if (FilenameUtils.wildcardMatch(normalizedPath, "**/" + pattern)) {
                        patternMatches = true;
                        break;
                    }
                }
                if (!patternMatches) {
                    return false;
                }
                if (finalEnvironments != null && !finalEnvironments.isEmpty()) {
                    String fileName = file.getName();
                    String fileBaseName = fileName.contains(".")
                            ? fileName.substring(0, fileName.lastIndexOf('.'))
                            : fileName;
                    return finalEnvironments.contains(fileBaseName);
                }
                return true;
            }
            @Override
            public boolean accept(File dir, String name) {
                return accept(new File(dir, name));
            }
        };
        Collection<File> files = FileUtils.listFiles(searchDir, fileFilter, TrueFileFilter.INSTANCE);
        if (files.isEmpty()) {
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    "No files found matching patterns: " + String.join(", ", filePatterns));
        }
        boolean tokenFound = false;
        String foundToken = null;
        String foundInFile = null;
        for (File file : files) {
            try {
                String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                for (String token : tokens) {
                    boolean matches = false;
                    switch (matchMode.toUpperCase()) {
                        case "REGEX":
                            matches = matchesRegex(content, token);
                            break;
                        case "ELEMENT_ATTRIBUTE":
                            if (elementName != null) {
                                matches = matchesInElement(content, elementName, token);
                            } else {
                                matches = content.contains(token);
                            }
                            break;
                        case "SUBSTRING":
                        default:
                            matches = content.contains(token);
                            break;
                    }
                    if (matches) {
                        tokenFound = true;
                        foundToken = token;
                        foundInFile = projectRoot.relativize(file.toPath()).toString();
                        break;
                    }
                }
                if (tokenFound) {
                    break; 
                }
            } catch (IOException e) {
            }
        }
        if ("REQUIRED".equalsIgnoreCase(searchMode)) {
            if (!tokenFound) {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        String.format("Required token(s) not found in files matching: %s",
                                String.join(", ", filePatterns)));
            }
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    String.format("Required token(s) found: '%s'", foundToken));
        } else {
            if (tokenFound) {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        String.format("Forbidden token '%s' found in file: %s", foundToken, foundInFile));
            }
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    "No forbidden tokens found");
        }
    }
    private boolean matchesRegex(String content, String regexPattern) {
        try {
            Pattern pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(content);
            return matcher.find();
        } catch (Exception e) {
            return content.contains(regexPattern);
        }
    }
    private boolean matchesInElement(String content, String elementName, String token) {
        String regex = String.format("(?s)<(?:[a-zA-Z0-9-]+:)?%s\\b[^>]*?%s[^>]*?>",
                Pattern.quote(elementName), Pattern.quote(token));
        try {
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(content);
            return matcher.find();
        } catch (Exception e) {
            return content.contains(token);
        }
    }
}
