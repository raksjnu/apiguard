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
public class XmlElementContentForbiddenCheck extends AbstractCheck {
    @Override
    public CheckResult execute(Path projectRoot, Check check) {
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) check.getParams().get("filePatterns");
        Object pairsObj = check.getParams().get("elementTokenPairs");
        List<Map<String, Object>> elementTokenPairs = new ArrayList<>();

        if (pairsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) pairsObj;
            elementTokenPairs.addAll(list);
        } else if (pairsObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) pairsObj;
            elementTokenPairs.add(map);
        }

        Boolean caseSensitive = (Boolean) check.getParams().getOrDefault("caseSensitive", true);
        String matchMode = (String) check.getParams().getOrDefault("matchMode", "SUBSTRING");

        if (filePatterns == null || filePatterns.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'filePatterns' parameter is required");
        }
        if (elementTokenPairs.isEmpty()) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Configuration error: 'elementTokenPairs' parameter is required");
        }
        List<String> failures = new ArrayList<>();
        List<Path> matchingFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            matchingFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesAnyPattern(path, filePatterns, projectRoot))
                    .filter(path -> !shouldIgnorePath(projectRoot, path))  
                    .toList();
            if (matchingFiles.isEmpty()) {
                return CheckResult.pass(check.getRuleId(), check.getDescription(),
                        "No files found matching patterns (nothing to validate)");
            }
            for (Path file : matchingFiles) {
                validateForbiddenContent(file, elementTokenPairs, matchMode, caseSensitive, projectRoot, failures);
            }
        } catch (IOException e) {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Error scanning files: " + e.getMessage());
        }
        if (failures.isEmpty()) {
            String fileList = matchingFiles.stream()
                    .map(projectRoot::relativize)
                    .map(Path::toString)
                    .collect(java.util.stream.Collectors.joining("; "));
            return CheckResult.pass(check.getRuleId(), check.getDescription(),
                    "No forbidden element content found\nFiles validated: " + fileList);
        } else {
            return CheckResult.fail(check.getRuleId(), check.getDescription(),
                    "Forbidden element content found:\n• " + String.join("\n• ", failures));
        }
    }
    private void validateForbiddenContent(Path file, List<Map<String, Object>> pairs, String matchMode,
            boolean caseSensitive, Path projectRoot, List<String> failures) {
        try {
            Document doc = parseXml(file);
            for (Map<String, Object> pair : pairs) {
                String elementName = (String) pair.get("element");
                @SuppressWarnings("unchecked")
                List<String> forbiddenTokens = (List<String>) pair.get("forbiddenTokens");
                if (elementName == null || forbiddenTokens == null || forbiddenTokens.isEmpty()) {
                    continue; 
                }
                NodeList nodeList = doc.getElementsByTagName(elementName);
                for (int i = 0; i < nodeList.getLength(); i++) {
                    Element element = (Element) nodeList.item(i);
                    String content = element.getTextContent();
                    for (String token : forbiddenTokens) {
                        if (containsToken(content, token, matchMode, caseSensitive)) {
                            failures.add(String.format("Forbidden token '%s' found in element '%s' in file: %s",
                                    token, elementName, projectRoot.relativize(file)));
                        }
                    }
                }
            }
        } catch (Exception e) {
            failures.add("Error parsing XML file " + projectRoot.relativize(file) + ": " + e.getMessage());
        }
    }
    private boolean containsToken(String content, String token, String matchMode, boolean caseSensitive) {
        if ("REGEX".equalsIgnoreCase(matchMode)) {
            try {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                Pattern pattern = Pattern.compile(token, flags);
                return pattern.matcher(content).find();
            } catch (Exception e) {
                return containsSubstring(content, token, caseSensitive);
            }
        } else {
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

}
