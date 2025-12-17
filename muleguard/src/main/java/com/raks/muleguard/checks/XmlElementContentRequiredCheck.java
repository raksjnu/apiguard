package com.raks.muleguard.checks;

import com.raks.muleguard.model.Check;
import com.raks.muleguard.model.CheckResult;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * XML Element Content Required Check - Validates that XML elements contain
 * required content/tokens.
 * Fails if required content is NOT found.
 * 
 * Supports:
 * - Multiple element-content pairs
 * - SUBSTRING or REGEX matching
 * - Case-sensitive/insensitive matching
 * - requireAll parameter (AND/OR logic)
 * 
 * @author Rakesh Kumar (raksjnu@gmail.com)
 */
public class XmlElementContentRequiredCheck extends AbstractCheck {

    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) check.getParams().get("filePatterns");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elementContentPairs = (List<Map<String, Object>>) check.getParams()
                .get("elementContentPairs");

        Boolean requireAll = (Boolean) check.getParams().getOrDefault("requireAll", true);
        Boolean caseSensitive = (Boolean) check.getParams().getOrDefault("caseSensitive", true);
        String matchMode = (String) check.getParams().getOrDefault("matchMode", "SUBSTRING");

        // Validation
        if (filePatterns == null || filePatterns.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'filePatterns' parameter is required");
        }

        if (elementContentPairs == null || elementContentPairs.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'elementContentPairs' parameter is required");
        }

        List<String> failures = new ArrayList<>();
        List<Path> matchingFiles = new ArrayList<>();
        List<String> successes = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            matchingFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesAnyPattern(path, filePatterns, projectRoot))
                    .filter(path -> !shouldIgnorePath(projectRoot, path))  // Filter ignored folders
                    .toList();

            if (matchingFiles.isEmpty()) {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        "No files found matching patterns: " + filePatterns);
            }

            for (Path file : matchingFiles) {
                validateElementContent(file, elementContentPairs, matchMode, caseSensitive,
                        projectRoot, failures, successes);
            }

        } catch (IOException e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error scanning files: " + e.getMessage());
        }

        // Determine result based on requireAll
        if (requireAll) {
            // ALL element-content pairs must match
            if (failures.isEmpty()) {
            String fileList = matchingFiles.stream()
                    .map(projectRoot::relativize)
                    .map(Path::toString)
                    .collect(java.util.stream.Collectors.joining("; "));
                return CheckResult.pass(check.getRuleId(), check.getDescription(),
                        "All required element content found\nFiles validated: " + fileList);
            } else {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        "Element content validation failures:\n• " + String.join("\n• ", failures));
            }
        } else {
            // At least ONE element-content pair must match
            if (!successes.isEmpty()) {
                return CheckResult.pass(check.getRuleId(), check.getDescription(),
                        "At least one required element content found");
            } else {
                return CheckResult.fail(check.getRuleId(), check.getDescription(),
                        "No element content matched:\n• " + String.join("\n• ", failures));
            }
        }
    }

    private void validateElementContent(Path file, List<Map<String, Object>> pairs, String matchMode,
            boolean caseSensitive, Path projectRoot,
            List<String> failures, List<String> successes) {
        try {
            Document doc = parseXml(file);

            for (Map<String, Object> pair : pairs) {
                String elementName = (String) pair.get("element");
                @SuppressWarnings("unchecked")
                List<String> requiredTokens = (List<String>) pair.get("requiredTokens");

                if (elementName == null || requiredTokens == null || requiredTokens.isEmpty()) {
                    failures.add(
                            "Invalid element-content pair configuration in file: " + file.getFileName().toString());
                    continue;
                }

                NodeList nodeList = doc.getElementsByTagName(elementName);
                boolean found = false;

                for (int i = 0; i < nodeList.getLength(); i++) {
                    Element element = (Element) nodeList.item(i);
                    String content = element.getTextContent();

                    // Check if all required tokens are present in this element
                    boolean allTokensFound = true;
                    for (String token : requiredTokens) {
                        if (!containsToken(content, token, matchMode, caseSensitive)) {
                            allTokensFound = false;
                            break;
                        }
                    }

                    if (allTokensFound) {
                        found = true;
                        successes.add(String.format("%s element '%s' has required tokens: %s",
                                file.getFileName().toString(), elementName, String.join(", ", requiredTokens)));
                        break;
                    }
                }

                if (!found) {
                    failures.add(String.format("%s element '%s' is missing required tokens: %s",
                            file.getFileName().toString(), elementName, String.join(", ", requiredTokens)));
                }
            }

        } catch (Exception e) {
            failures.add("Error parsing XML file " + file.getFileName().toString() + ": " + e.getMessage());
        }
    }

    private boolean containsToken(String content, String token, String matchMode, boolean caseSensitive) {
        if ("REGEX".equalsIgnoreCase(matchMode)) {
            try {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                Pattern pattern = Pattern.compile(token, flags);
                return pattern.matcher(content).find();
            } catch (Exception e) {
                // Invalid regex - fall back to substring
                return containsSubstring(content, token, caseSensitive);
            }
        } else {
            // SUBSTRING mode
            return containsSubstring(content, token, caseSensitive);
        }
    }

    private boolean containsSubstring(String content, String token, boolean caseSensitive) {
        if (caseSensitive) {
            return content.contains(token);
        } else {
            return content.toLowerCase().contains(token.toLowerCase());
        }
    }

    private Document parseXml(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(file.toFile());
    }

    private boolean matchesAnyPattern(Path path, List<String> patterns, Path projectRoot) {
        String relativePath = projectRoot.relativize(path).toString().replace("\\", "/");

        for (String pattern : patterns) {
            if (matchesPattern(relativePath, pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPattern(String path, String pattern) {
        String regex = pattern
                .replace(".", "\\.")
                .replace("**/", ".*")
                .replace("**", ".*")
                .replace("*", "[^/]*")
                .replace("?", ".");

        return path.matches(regex);
    }
}
